package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.PurchaseDtos.PurchaseApproveRequest;
import com.example.techcampushubbackend.dto.PurchaseDtos.PurchaseCreateRequest;
import com.example.techcampushubbackend.dto.PurchaseDtos.PurchaseResponse;
import com.example.techcampushubbackend.dto.PurchaseDtos.SecondCourseOfferResponse;
import com.example.techcampushubbackend.entity.Course;
import com.example.techcampushubbackend.entity.Purchase;
import com.example.techcampushubbackend.entity.PurchaseStatus;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ApiException;
import com.example.techcampushubbackend.exception.BadRequestException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.PurchaseRepository;
import com.example.techcampushubbackend.repository.UserRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Purchase lifecycle:
 * student submits UTR -> PENDING -> admin approves (grants validity) / rejects,
 * admin can extend the validity of an approved purchase.
 */
@Service
@RequiredArgsConstructor
public class PurchaseService {

    /**
     * 1+1 second-course offer: a student buying their second course pays this
     * fraction of the list price. First and every later course stay at full
     * price; eligibility is always decided from the purchase ledger below,
     * never from anything the client sends.
     */
    public static final BigDecimal SECOND_COURSE_DISCOUNT = new BigDecimal("0.50");

    private final PurchaseRepository purchaseRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public PurchaseResponse request(PurchaseCreateRequest request) {
        User caller = currentUserService.requireUser();
        // Lock the student's row for the rest of this transaction: everything
        // below (duplicate guard, 1+1 discount eligibility, insert) is a
        // read-modify-write on this student's ledger. Two parallel requests
        // would otherwise both see "no pending purchase" / "exactly one owned
        // course" and both act on it - creating duplicate PENDING rows or
        // handing out the discount twice.
        User user = userRepository.findByIdForUpdate(caller.getId()).orElse(caller);

        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", request.courseId()));
        if (!course.isActive()) {
            throw new BadRequestException("This course is not open for enrollment");
        }

        boolean alreadyActive = purchaseRepository
                .existsByUserIdAndCourseIdAndStatusAndValidUntilAfter(
                        user.getId(), course.getId(), PurchaseStatus.APPROVED, LocalDateTime.now());
        boolean alreadyPending = purchaseRepository
                .existsByUserIdAndCourseIdAndStatus(user.getId(), course.getId(), PurchaseStatus.PENDING);
        if (alreadyActive) {
            throw new BadRequestException("You already have active access to this course");
        }
        if (alreadyPending) {
            throw new BadRequestException("A purchase request for this course is already pending review");
        }

        // 1+1 second-course offer, decided purely server-side.
        BigDecimal originalAmount = course.getPriceInr();
        BigDecimal discountAmount = secondCourseDiscountFor(user, course, originalAmount);

        Purchase purchase = Purchase.builder()
                .user(user)
                .course(course)
                .amountInr(originalAmount.subtract(discountAmount))
                .originalAmountInr(originalAmount)
                .discountAmountInr(discountAmount)
                .utr(request.utr().trim())
                .status(PurchaseStatus.PENDING)
                .build();
        return toResponse(purchaseRepository.save(purchase));
    }

    /**
     * 1+1 second-course offer: half price while the student holds exactly one
     * other course (pending or approved) and this purchase is for a different
     * course. Everything is derived from the purchase ledger, so the price a
     * frontend displays is never trusted; rejected rows never count, and
     * pending rows count so discounted requests cannot stack while earlier
     * ones await admin review.
     */
    private BigDecimal secondCourseDiscountFor(User user, Course course, BigDecimal originalAmount) {
        if (originalAmount == null || originalAmount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }

        List<Long> ownedCourseIds = purchaseRepository.findCourseIdsByUserAndStatusIn(
                user.getId(), List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED));

        boolean secondCourseStage = ownedCourseIds.size() == 1
                && !ownedCourseIds.contains(course.getId());

        if (!secondCourseStage) {
            return BigDecimal.ZERO.setScale(2);
        }

        return originalAmount.multiply(SECOND_COURSE_DISCOUNT)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 1+1 offer snapshot for the course cards and the checkout step. Display
     * only - the actual discount is recomputed in {@link #request}.
     */
    @Transactional(readOnly = true)
    public SecondCourseOfferResponse offer() {
        User user = currentUserService.requireUser();

        List<Long> ownedCourseIds = purchaseRepository.findCourseIdsByUserAndStatusIn(
                user.getId(), List.of(PurchaseStatus.PENDING, PurchaseStatus.APPROVED));

        return new SecondCourseOfferResponse(
                ownedCourseIds.size() == 1,
                SECOND_COURSE_DISCOUNT.movePointRight(2).intValue(),
                ownedCourseIds.size(),
                ownedCourseIds);
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> listMine() {
        User user = currentUserService.requireUser();
        return purchaseRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(PurchaseService::toResponse)
                .toList();
    }

    /** Courses the caller can currently access (approved + not expired). */
    @Transactional(readOnly = true)
    public List<PurchaseResponse> listMyActive() {
        User user = currentUserService.requireUser();
        return purchaseRepository.findActiveByUser(user.getId(), LocalDateTime.now()).stream()
                .map(PurchaseService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> listByStatus(String status) {
        PurchaseStatus parsed = parseStatus(status);
        List<Purchase> purchases = parsed == null
                ? purchaseRepository.findAllByOrderByCreatedAtDesc()
                : purchaseRepository.findByStatusOrderByCreatedAtDesc(parsed);
        return purchases.stream().map(PurchaseService::toResponse).toList();
    }

    @Transactional
    public PurchaseResponse approve(Long id, PurchaseApproveRequest request) {
        Purchase purchase = require(id);
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new BadRequestException("Only pending purchases can be approved");
        }
        User admin = currentUserService.requireUser();
        LocalDateTime now = LocalDateTime.now();
        purchase.setStatus(PurchaseStatus.APPROVED);
        purchase.setValidFrom(now);
        purchase.setValidUntil(resolveValidityUntil(purchase, request, now));
        purchase.setReviewedAt(now);
        purchase.setReviewedBy(admin);
        purchase.setRejectionReason(null);
        return toResponse(purchaseRepository.save(purchase));
    }

    @Transactional
    public PurchaseResponse reject(Long id, String reason) {
        Purchase purchase = require(id);
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new BadRequestException("Only pending purchases can be rejected");
        }
        purchase.setStatus(PurchaseStatus.REJECTED);
        purchase.setReviewedAt(LocalDateTime.now());
        purchase.setReviewedBy(currentUserService.requireUser());
        purchase.setRejectionReason(reason);
        purchase.setValidFrom(null);
        purchase.setValidUntil(null);
        return toResponse(purchaseRepository.save(purchase));
    }

    /** Extends an approved purchase's validity window by N days from now/validUntil. */
    @Transactional
    public PurchaseResponse extendValidity(Long id, int days) {
        Purchase purchase = require(id);
        if (purchase.getStatus() != PurchaseStatus.APPROVED) {
            throw new BadRequestException("Only approved purchases can be extended");
        }
        LocalDateTime base = purchase.getValidUntil() == null
                || purchase.getValidUntil().isBefore(LocalDateTime.now())
                ? LocalDateTime.now()
                : purchase.getValidUntil();
        purchase.setValidUntil(base.plusDays(days));
        return toResponse(purchaseRepository.save(purchase));
    }

    /** Sets an exact expiry date on an approved purchase (admin edit). */
    @Transactional
    public PurchaseResponse setValidity(Long id, LocalDateTime validUntil) {
        Purchase purchase = require(id);
        if (purchase.getStatus() != PurchaseStatus.APPROVED) {
            throw new BadRequestException("Only approved purchases have a validity window");
        }
        if (validUntil.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("The expiry date must be in the future");
        }
        if (purchase.getValidFrom() == null) {
            purchase.setValidFrom(LocalDateTime.now());
        }
        purchase.setValidUntil(validUntil);
        return toResponse(purchaseRepository.save(purchase));
    }

    /**
     * Validity window for a fresh approval: explicit validUntil wins, then
     * validityDays, then the course's default.
     */
    private static LocalDateTime resolveValidityUntil(
            Purchase purchase, PurchaseApproveRequest request, LocalDateTime now) {
        if (request != null && request.validUntil() != null) {
            if (request.validUntil().isBefore(now)) {
                throw new BadRequestException("The expiry date must be in the future");
            }
            return request.validUntil();
        }
        int days = request != null && request.validityDays() != null
                ? request.validityDays()
                : purchase.getCourse().getValidityDays();
        return now.plusDays(days);
    }

    /** Removes a purchase record entirely (admin cleanup). */
    @Transactional
    public void delete(Long id) {
        Purchase purchase = require(id);
        purchaseRepository.delete(purchase);
    }

    /** Access check used by content services for bundle-gated material. */
    public boolean hasActiveAccess(Long userId, Long courseId) {
        if (userId == null || courseId == null) {
            return false;
        }
        return purchaseRepository.existsByUserIdAndCourseIdAndStatusAndValidUntilAfter(
                userId, courseId, PurchaseStatus.APPROVED, LocalDateTime.now());
    }

    private Purchase require(Long id) {
        return purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase", id));
    }

    private static PurchaseStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return PurchaseStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Invalid status: " + status + " (expected PENDING, APPROVED, REJECTED or all)");
        }
    }

    public static PurchaseResponse toResponse(Purchase p) {
        return new PurchaseResponse(
                p.getId(),
                p.getCourse().getId(),
                p.getCourse().getTitle(),
                p.getUser().getId(),
                p.getUser().getName(),
                p.getUser().getEmail(),
                p.getAmountInr(),
                p.getOriginalAmountInr(),
                p.getDiscountAmountInr(),
                p.getUtr(),
                p.getStatus().name(),
                p.getValidFrom(),
                p.getValidUntil(),
                p.getReviewedAt(),
                p.getRejectionReason(),
                p.getProgress(),
                p.getCreatedAt());
    }
}
