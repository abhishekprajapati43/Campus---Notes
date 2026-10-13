package com.example.techcampushubbackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Request/response payloads for courses. */
public final class CourseDtos {

    private CourseDtos() {
    }

    public record CourseRequest(
            @NotBlank(message = "title is required")
            @Size(max = 160, message = "title must be at most 160 characters")
            String title,

            @Size(max = 40, message = "code must be at most 40 characters")
            String code,

            @Size(max = 60, message = "category must be at most 60 characters")
            String category,

            String description,

            @NotNull(message = "priceInr is required")
            @DecimalMin(value = "0", message = "priceInr must be >= 0")
            BigDecimal priceInr,

            String thumbnailUrl,

            @Min(value = 1, message = "validityDays must be >= 1")
            Integer validityDays,

            Boolean active) {
    }

    public record CourseResponse(
            Long id,
            String title,
            String code,
            String category,
            String description,
            BigDecimal priceInr,
            String thumbnailUrl,
            int validityDays,
            boolean active,
            LocalDateTime createdAt) {
    }
}
