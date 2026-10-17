package com.example.techcampushubbackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response payloads for course purchases. */
public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    public record PurchaseCreateRequest(
            @NotNull(message = "courseId is required")
            Long courseId,

            @NotBlank(message = "utr is required")
            @Size(max = 64, message = "utr must be at most 64 characters")
            String utr) {
    }

    public record PurchaseReviewRequest(
            @Size(max = 300, message = "reason must be at most 300 characters")
            String reason) {
    }

    /**
     * Approval with an optional custom validity window. When neither field is
     * supplied the course's default validity_days is used.
     */
    public record PurchaseApproveRequest(
            @Min(value = 1, message = "validityDays must be >= 1")
            @Max(value = 3650, message = "validityDays must be <= 3650")
            Integer validityDays,

            /** Exact expiry date/time (admin picks a custom end date). */
            LocalDateTime validUntil) {
    }

    /** Extend an approved purchase's validity by N days from now/expiry. */
    public record ExtendValidityRequest(
            @NotNull(message = "days is required")
            @Min(value = 1, message = "days must be >= 1")
            Integer days) {
    }

    /** Set an exact expiry date on an approved (or older) purchase. */
    public record SetValidityRequest(
            @NotNull(message = "validUntil is required")
            LocalDateTime validUntil) {
    }

    public record PurchaseResponse(
            Long id,
            Long courseId,
            String courseTitle,
            Long userId,
            String userName,
            String userEmail,
            BigDecimal amountInr,
            /** 1+1 offer: list price before the discount (null on legacy rows). */
            BigDecimal originalAmountInr,
            /** 1+1 offer: discount granted on this purchase (null/zero = none). */
            BigDecimal discountAmountInr,
            String utr,
            String status,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            LocalDateTime reviewedAt,
            String rejectionReason,
            /** Course completion progress (0-100); null when not tracked yet. */
            Integer progress,
            LocalDateTime createdAt) {
    }

    /**
     * Snapshot of the 1+1 second-course offer for the storefront and checkout
     * UI. Display only: the real discount is always recomputed server-side
     * inside the purchase request itself, so a tampered client can never
     * choose its own price.
     */
    public record SecondCourseOfferResponse(
            /** True while the student holds exactly one other course (second-course stage). */
            boolean eligible,

            /** Percent off granted on the second course (50). */
            int discountPercent,

            /** Distinct courses the student already holds (pending or approved). */
            int ownedCourseCount,

            /** Those course ids, so the UI can skip owned cards. */
            List<Long> ownedCourseIds) {
    }
}
