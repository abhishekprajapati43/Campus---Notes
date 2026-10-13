package com.example.techcampushubbackend.dto;

import com.example.techcampushubbackend.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Request/response payloads for authentication endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "name is required")
            @Size(max = 120, message = "name must be at most 120 characters")
            String name,

            @NotBlank(message = "email is required")
            @Email(message = "email must be valid")
            @Size(max = 190, message = "email must be at most 190 characters")
            String email,

            @NotBlank(message = "password is required")
            @Size(min = 8, max = 72, message = "password must be 8-72 characters")
            String password,

            @Pattern(regexp = "^$|\\+?[0-9\\-\\s]{7,20}", message = "phone must be a valid phone number")
            String phone,

            /** Academic profile - optional, used by the admin Users panel. */
            @Size(max = 60, message = "course must be at most 60 characters")
            String course,

            @Size(max = 60, message = "branch must be at most 60 characters")
            String branch,

            @Min(value = 1, message = "year must be >= 1")
            @Max(value = 6, message = "year must be <= 6")
            Integer year,

            @Min(value = 1, message = "semester must be >= 1")
            @Max(value = 12, message = "semester must be <= 12")
            Integer semester) {
    }

    public record LoginRequest(
            @NotBlank(message = "email is required")
            @Email(message = "email must be valid")
            String email,

            @NotBlank(message = "password is required")
            String password) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "email is required")
            @Email(message = "email must be valid")
            String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "token is required")
            String token,

            @NotBlank(message = "newPassword is required")
            @Size(min = 8, max = 72, message = "newPassword must be 8-72 characters")
            String newPassword) {
    }

    public record VerifyEmailRequest(
            @NotBlank(message = "token is required")
            String token) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "currentPassword is required")
            String currentPassword,

            @NotBlank(message = "newPassword is required")
            @Size(min = 8, max = 72, message = "newPassword must be 8-72 characters")
            String newPassword) {
    }

    public record UpdateProfileRequest(
            @Size(max = 120, message = "name must be at most 120 characters")
            String name,

            @Pattern(regexp = "^$|\\+?[0-9\\-\\s]{7,20}", message = "phone must be a valid phone number")
            String phone,

            @Size(max = 60, message = "course must be at most 60 characters")
            String course,

            @Size(max = 60, message = "branch must be at most 60 characters")
            String branch,

            @Min(value = 1, message = "year must be >= 1")
            @Max(value = 6, message = "year must be <= 6")
            Integer year,

            @Min(value = 1, message = "semester must be >= 1")
            @Max(value = 12, message = "semester must be <= 12")
            Integer semester) {
    }

    /** JWT-carrying response returned on register/login. */
    public record AuthResponse(
            String token,
            String tokenType,
            long expiresInMs,
            UserResponse user) {
    }

    public record UserResponse(
            Long id,
            String name,
            String email,
            String phone,
            Role role,
            boolean emailVerified,
            String course,
            String branch,
            Integer year,
            Integer semester,
            LocalDateTime createdAt) {
    }
}
