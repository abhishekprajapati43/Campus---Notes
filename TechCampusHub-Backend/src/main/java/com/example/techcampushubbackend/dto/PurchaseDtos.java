package com.example.techcampushubbackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    public record ExtendValidityRequest(
            @NotNull(message = "days is required")
            @Min(value = 1, message = "days must be >= 1")
            Integer days) {
    }

    public record PurchaseResponse(
            Long id,
            Long courseId,
            String courseTitle,
            Long userId,
            String userName,
            String userEmail,
            BigDecimal amountInr,
            String utr,
            String status,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            LocalDateTime reviewedAt,
            String rejectionReason,
            LocalDateTime createdAt) {
    }
}
