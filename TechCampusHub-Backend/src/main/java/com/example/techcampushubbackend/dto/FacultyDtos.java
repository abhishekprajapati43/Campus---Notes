package com.example.techcampushubbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request/response payloads for the faculty dashboard:
 * statistics, the scoped student list, scoped notifications and
 * the extra upload kinds (videos / assignments / other files).
 */
public final class FacultyDtos {

    private FacultyDtos() {
    }

    // ------------------------------------------------------------- statistics

    /**
     * Dashboard summary - every number is scoped to the courses and content
     * owned by the logged-in faculty member.
     */
    public record FacultyStatsResponse(
            long totalCourses,
            long totalStudents,
            long totalContent,
            /** Purchases of the caller's courses made in the last 30 days. */
            long recentPurchases,
            /** Latest purchases of the caller's courses (dashboard activity list). */
            List<PurchaseDtos.PurchaseResponse> recentPurchaseList) {
    }

    // --------------------------------------------------------------- students

    /**
     * One row of the faculty Users table: a purchase of one of the caller's
     * courses. Faculty have read-only access - there is no mutation route.
     */
    public record FacultyStudentResponse(
            Long purchaseId,
            Long userId,
            String studentName,
            String userEmail,
            String userPhone,
            Long courseId,
            String courseTitle,
            BigDecimal amountInr,
            /** Purchase requested date/time. */
            LocalDateTime purchaseDate,
            /** APPROVED | PENDING | REJECTED */
            String status,
            /** Active | Pending | Expired | Rejected - derived for display. */
            String accessStatus,
            /** Course completion progress (0-100); null when not tracked yet. */
            Integer progress,
            LocalDateTime validUntil) {
    }

    // ----------------------------------------------------------- notifications

    /**
     * Faculty broadcast: delivered only to students enrolled (approved
     * purchase) in the caller's own courses; {@code courseId} optionally
     * narrows the audience to one owned course.
     */
    public record FacultyNotificationRequest(
            @NotBlank(message = "title is required")
            @Size(max = 200, message = "title must be at most 200 characters")
            String title,

            @Size(max = 4000, message = "message must be at most 4000 characters")
            String message,

            /** INFO | UPDATE | OFFER | ALERT - defaults to INFO. */
            String kind,

            /** general | timetable | course | notes | pyq | tnp - defaults to general. */
            String category,

            /** Optional: only students enrolled in this (owned) course. */
            Long courseId) {
    }

    public record FacultyNotificationSendResponse(
            /** Number of students the notification was delivered to. */
            int sentCount) {
    }

    // -------------------------------------------------------------- resources

    /** Upload of a video, assignment or other file into an owned course. */
    public record ResourceRequest(
            @NotBlank(message = "title is required")
            @Size(max = 200, message = "title must be at most 200 characters")
            String title,

            @Size(max = 600, message = "description must be at most 600 characters")
            String description,

            /** VIDEO | ASSIGNMENT | OTHER (case-insensitive). */
            @Size(max = 20, message = "kind must be at most 20 characters")
            String kind,

            /** Required on create; optional on update (partial PUT). */
            @Size(max = 600)
            @Pattern(regexp = "^$|^https?://.*", message = "fileUrl must be an http(s) URL")
            String fileUrl,

            @Size(max = 300, message = "storagePath must be at most 300 characters")
            String storagePath,

            @Size(max = 255, message = "fileName must be at most 255 characters")
            String fileName,

            /** Optional: the owned course this resource is bundled with. */
            Long courseId) {
    }

    public record ResourceResponse(
            Long id,
            String title,
            String description,
            String kind,
            String fileUrl,
            String storagePath,
            String fileName,
            Long courseId,
            String courseTitle,
            LocalDateTime createdAt) {
    }
}
