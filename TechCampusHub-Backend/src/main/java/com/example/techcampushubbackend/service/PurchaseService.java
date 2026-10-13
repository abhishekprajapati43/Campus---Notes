package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.PurchaseDtos.PurchaseCreateRequest;
import com.example.techcampushubbackend.dto.PurchaseDtos.PurchaseResponse;
import com.example.techcampushubbackend.entity.Course;
import com.example.techcampushubbackend.entity.Purchase;
import com.example.techcampushubbackend.entity.PurchaseStatus;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ApiException;
import com.example.techcampushubbackend.exception.BadRequestException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.PurchaseRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final PurchaseRepository purchaseRepository;
    private final CourseRepository courseRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public PurchaseResponse request(PurchaseCreateRequest request) {
        User user = currentUserService.requireUser();
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

        Purchase purchase = Purchase.builder()
                .user(user)
                .course(course)
                .amountInr(course.getPriceInr())
                .utr(request.utr().trim())
                .status(PurchaseStatus.PENDING)
                .build();
        return toResponse(purchaseRepository.save(purchase));
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
    public PurchaseResponse approve(Long id) {
        Purchase purchase = require(id);
        if (purchase.getStatus() != PurchaseStatus.PENDING) {
            throw new BadRequestException("Only pending purchases can be approved");
        }
        User admin = currentUserService.requireUser();
        LocalDateTime now = LocalDateTime.now();
        purchase.setStatus(PurchaseStatus.APPROVED);
        purchase.setValidFrom(now);
        purchase.setValidUntil(now.plusDays(purchase.getCourse().getValidityDays()));
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

    public boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
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
                p.getUtr(),
                p.getStatus().name(),
                p.getValidFrom(),
                p.getValidUntil(),
                p.getReviewedAt(),
                p.getRejectionReason(),
                p.getCreatedAt());
    }
}
