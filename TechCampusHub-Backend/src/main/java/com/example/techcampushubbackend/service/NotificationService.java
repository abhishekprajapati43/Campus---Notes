package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.EngagementDtos.NotificationRequest;
import com.example.techcampushubbackend.dto.EngagementDtos.NotificationResponse;
import com.example.techcampushubbackend.dto.FacultyDtos.FacultyNotificationRequest;
import com.example.techcampushubbackend.dto.FacultyDtos.FacultyNotificationSendResponse;
import com.example.techcampushubbackend.entity.Course;
import com.example.techcampushubbackend.entity.Notification;
import com.example.techcampushubbackend.entity.Notification.Kind;
import com.example.techcampushubbackend.entity.Purchase;
import com.example.techcampushubbackend.entity.PurchaseStatus;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.BadRequestException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.NotificationRepository;
import com.example.techcampushubbackend.repository.PurchaseRepository;
import com.example.techcampushubbackend.repository.UserRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Notifications: admins send broadcasts (no user id) or direct messages
 * (user id set); students receive broadcasts plus their own direct messages.
 * Broadcasts carry an audience (all / premium / free) that is filtered
 * server-side using each student's active purchases.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PurchaseRepository purchaseRepository;
    private final CourseService courseService;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine() {
        User user = currentUserService.requireUser();
        boolean premium = hasActivePremium(user);
        return notificationRepository.findVisibleTo(user.getId()).stream()
                .filter(n -> audienceVisible(n.getAudience(), premium))
                .map(NotificationService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listAll() {
        return notificationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(NotificationService::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse send(NotificationRequest request) {
        User sender = currentUserService.requireUser();
        User recipient = null;
        if (request.userId() != null) {
            recipient = userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
        }
        Notification notification = Notification.builder()
                .title(request.title().trim())
                .message(request.message() == null ? "" : request.message().trim())
                .kind(parseKind(request.kind()))
                .category(normalizeCategory(request.category()))
                .audience(normalizeAudience(request.audience()))
                .createdBy(sender.getEmail())
                .targetUser(recipient)
                .build();
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public void delete(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        notificationRepository.delete(notification);
    }

    // -------------------------------------------------- faculty workspace

    /**
     * Faculty send: one targeted notification per student enrolled (approved
     * purchase) in the caller's own courses - optionally narrowed to a single
     * owned course. Students of other faculty are never reachable from here.
     */
    @Transactional
    public FacultyNotificationSendResponse sendToMyStudents(FacultyNotificationRequest request) {
        User sender = currentUserService.requireUser();
        // Optional narrowing; 403 when the course is not one of the caller's.
        Course course = courseService.requireOwnedBundle(request.courseId());

        List<User> students = purchaseRepository.findFacultyPurchases(sender.getId()).stream()
                .filter(purchase -> purchase.getStatus() == PurchaseStatus.APPROVED)
                .filter(purchase -> course == null
                        || course.getId().equals(purchase.getCourse().getId()))
                .map(Purchase::getUser)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(
                        User::getId, student -> student, (first, second) -> first,
                        java.util.LinkedHashMap::new))
                .values().stream()
                .toList();

        if (students.isEmpty()) {
            throw new BadRequestException(
                    "No students are enrolled in your courses yet - nothing to send.");
        }

        Kind kind = parseKind(request.kind());
        String category = normalizeCategory(request.category());
        String title = request.title().trim();
        String message = request.message() == null ? "" : request.message().trim();

        for (User student : students) {
            notificationRepository.save(Notification.builder()
                    .title(title)
                    .message(message)
                    .kind(kind)
                    .category(category)
                    .audience("all")
                    .createdBy(sender.getEmail())
                    .targetUser(student)
                    .build());
        }
        return new FacultyNotificationSendResponse(students.size());
    }

    /** Notifications the caller sent (faculty "sent" list). */
    @Transactional(readOnly = true)
    public List<NotificationResponse> listSentByMe() {
        User sender = currentUserService.requireUser();
        return notificationRepository.findByCreatedByOrderByCreatedAtDesc(sender.getEmail()).stream()
                .map(NotificationService::toResponse)
                .toList();
    }

    /** True when the student has at least one approved, unexpired purchase. */
    private boolean hasActivePremium(User user) {
        // ADMIN and FACULTY staff always see premium-audience notifications.
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.FACULTY) {
            return true;
        }
        return purchaseRepository.existsByUserIdAndStatusAndValidUntilAfter(
                user.getId(), PurchaseStatus.APPROVED, LocalDateTime.now());
    }

    private static boolean audienceVisible(String audience, boolean premium) {
        return switch (audience == null ? "all" : audience.toLowerCase(Locale.ROOT)) {
            case "premium" -> premium;
            case "free" -> !premium;
            default -> true;
        };
    }

    private static Kind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return Kind.INFO;
        }
        try {
            return Kind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid kind: " + raw
                    + " (expected INFO, UPDATE, OFFER or ALERT)");
        }
    }

    private static String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return "general";
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return switch (trimmed) {
            case "general", "timetable", "course", "notes", "pyq", "tnp" -> trimmed;
            default -> "general";
        };
    }

    private static String normalizeAudience(String raw) {
        if (raw == null || raw.isBlank()) {
            return "all";
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return switch (trimmed) {
            case "all", "premium", "free" -> trimmed;
            default -> "all";
        };
    }

    public static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getTitle(), n.getMessage(), n.getKind().name(),
                n.getCategory(), n.getAudience(), n.getCreatedBy(),
                n.getTargetUser() == null ? null : n.getTargetUser().getId(),
                n.getTargetUser() == null ? null : n.getTargetUser().getName(),
                n.getTargetUser() == null,
                n.getCreatedAt());
    }
}
