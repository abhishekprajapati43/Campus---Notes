package com.example.techcampushubbackend.dto;

import com.example.techcampushubbackend.entity.RegistrationStatus;
import com.example.techcampushubbackend.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
            Integer semester,

            /** Optional: STUDENT (default) or FACULTY, case-insensitive. ADMIN is never self-registerable. */
            @Size(max = 20, message = "role must be at most 20 characters")
            String role,

            /** FACULTY only: 12-digit Aadhaar number, stored as a SHA-256 hash and never returned. */
            @Pattern(regexp = "^$|[0-9]{12}", message = "aadhaarNumber must be 12 digits")
            String aadhaarNumber,

            /** FACULTY only: postal address captured at sign-up. */
            @Size(max = 500, message = "address must be at most 500 characters")
            String address,

            /** Optional at sign-up: shown on the profile page (all roles). */
            @Size(max = 20, message = "gender must be at most 20 characters")
            String gender,

            /** STUDENT only: roll / enrollment number captured at sign-up. */
            @Size(max = 40, message = "enrollmentNumber must be at most 40 characters")
            String enrollmentNumber,

            /** FACULTY only: home department, e.g. "Computer Science". */
            @Size(max = 120, message = "department must be at most 120 characters")
            String department,

            /** FACULTY only: highest qualification, e.g. "M.Tech". */
            @Size(max = 120, message = "qualification must be at most 120 characters")
            String qualification,

            /** FACULTY only: teaching/industry experience, e.g. "5 years". */
            @Size(max = 60, message = "experience must be at most 60 characters")
            String experience,

            /** FACULTY only: must be true or registration is rejected. */
            Boolean declarationAccepted) {
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
            /** False while an admin has suspended the account (login is blocked). */
            boolean enabled,
            /** Faculty sign-up declaration; always false for students. */
            boolean declarationAccepted,
            /** Registration moderation state; faculty start PENDING until approved. */
            RegistrationStatus registrationStatus,
            String course,
            String branch,
            Integer year,
            Integer semester,
            /** Postal address captured at sign-up (optional; faculty provide one). */
            String address,
            /** Optional profile details captured at sign-up (null when not provided). */
            String gender,
            String enrollmentNumber,
            String department,
            String qualification,
            String experience,
            LocalDateTime createdAt) {
    }

    /** Admin panel: temporarily disable ("suspend") or re-enable an account. */
    public record UserEnabledRequest(
            @NotNull(message = "enabled is required")
            Boolean enabled) {
    }

    /** Admin "Manage Faculty": approve or reject a pending faculty sign-up. */
    public record UserRegistrationStatusRequest(
            @NotNull(message = "status is required")
            RegistrationStatus status) {
    }
}
