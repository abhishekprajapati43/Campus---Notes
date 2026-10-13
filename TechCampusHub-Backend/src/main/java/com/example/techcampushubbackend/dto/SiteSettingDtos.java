package com.example.techcampushubbackend.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Request/response payloads for the global site settings row. */
public final class SiteSettingDtos {

    private SiteSettingDtos() {
    }

    /** Every field is optional: only the supplied (non-null) values are changed. */
    public record SiteSettingRequest(
            @Size(max = 120, message = "upiId must be at most 120 characters")
            String upiId,

            @Size(max = 400, message = "geminiUrl must be at most 400 characters")
            String geminiUrl,

            @Size(max = 400, message = "telegramLink must be at most 400 characters")
            String telegramLink) {
    }

    public record SiteSettingResponse(
            Long id,
            String upiId,
            String geminiUrl,
            String telegramLink,
            LocalDateTime updatedAt) {
    }
}
