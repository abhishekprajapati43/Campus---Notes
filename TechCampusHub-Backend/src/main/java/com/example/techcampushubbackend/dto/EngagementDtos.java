package com.example.techcampushubbackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Request/response payloads for notifications, favorites and feedback. */
public final class EngagementDtos {

    private EngagementDtos() {
    }

    // ---------------------------------------------------------- notifications

    public record NotificationRequest(
            @NotBlank(message = "title is required")
            @Size(max = 200) String title,

            @NotBlank(message = "message is required")
            String message,

            /** INFO | UPDATE | OFFER | ALERT - defaults to INFO. */
            String kind,

            /** Optional recipient; omit/null = broadcast to everyone. */
            Long userId) {
    }

    public record NotificationResponse(
            Long id,
            String title,
            String message,
            String kind,
            Long userId,
            String targetUserName,
            boolean broadcast,
            LocalDateTime createdAt) {
    }

    // -------------------------------------------------------------- favorites

    public record FavoriteRequest(
            /** COURSE | NOTE | PYQ | TNP_NOTE | TIMETABLE | CERTIFICATE */
            @NotBlank(message = "itemType is required")
            String itemType,

            @NotNull(message = "itemId is required")
            Long itemId) {
    }

    public record FavoriteResponse(
            Long id,
            String itemType,
            Long itemId,
            String itemTitle,
            LocalDateTime createdAt) {
    }

    // -------------------------------------------------------------- feedback

    public record FeedbackRequest(
            Long courseId,

            @NotNull(message = "rating is required")
            @Min(value = 1, message = "rating must be 1-5")
            @Max(value = 5, message = "rating must be 1-5")
            Integer rating,

            @NotBlank(message = "message is required")
            @Size(max = 2000, message = "message must be at most 2000 characters")
            String message) {
    }

    public record FeedbackResponse(
            Long id,
            Long userId,
            String userName,
            Long courseId,
            String courseTitle,
            int rating,
            String message,
            LocalDateTime createdAt) {
    }
}
