package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.AuthDtos.AuthResponse;
import com.example.techcampushubbackend.dto.AuthDtos.ChangePasswordRequest;
import com.example.techcampushubbackend.dto.AuthDtos.ForgotPasswordRequest;
import com.example.techcampushubbackend.dto.AuthDtos.LoginRequest;
import com.example.techcampushubbackend.dto.AuthDtos.RegisterRequest;
import com.example.techcampushubbackend.dto.AuthDtos.ResetPasswordRequest;
import com.example.techcampushubbackend.dto.AuthDtos.UpdateProfileRequest;
import com.example.techcampushubbackend.dto.AuthDtos.UserResponse;
import com.example.techcampushubbackend.dto.AuthDtos.VerifyEmailRequest;
import com.example.techcampushubbackend.entity.RegistrationStatus;
import com.example.techcampushubbackend.security.AuthenticatedUser;
import com.example.techcampushubbackend.service.AuthService;
import com.example.techcampushubbackend.utils.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. All of these are public except the /me and
 * profile/password-change routes, which require a bearer token.
 */
@Tag(name = "Auth", description = "Registration, login, e-mail verification, password reset")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Register a new student or faculty account",
            description = "role defaults to STUDENT; FACULTY requires phone, aadhaarNumber, "
                    + "address and declarationAccepted=true and lands as PENDING until an "
                    + "admin approves it. ADMIN can never be self-registered.")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        String message = user.registrationStatus() == RegistrationStatus.PENDING
                ? "Account created. Your registration is pending approval by an administrator - "
                        + "you will be able to log in once it is approved."
                : "Account created. Check your email for the verification link.";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(message, user));
    }

    @Operation(summary = "Login and receive a JWT")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @Operation(summary = "Verify e-mail address with the token from the email",
            description = "Accepts a JSON body {\"token\": \"...\"} or a ?token= query parameter.")
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody(required = false) VerifyEmailRequest body,
            @RequestParam(required = false) String token) {
        String effectiveToken = body != null ? body.token() : token;
        if (effectiveToken == null || effectiveToken.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("token is required"));
        }
        authService.verifyEmail(effectiveToken);
        return ResponseEntity.ok(ApiResponse.ok("Email verified. You can now log in.", null));
    }

    /** Supports the link emitted in the verification e-mail (GET). */
    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmailGet(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.ok("Email verified. You can now log in.", null));
    }

    @Operation(summary = "Re-send the verification e-mail")
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(ApiResponse.ok("If the account exists, a verification email was sent.", null));
    }

    @Operation(summary = "Start the forgot-password flow")
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.ok(
                "If the account exists, a password reset link was sent.", null));
    }

    @Operation(summary = "Complete the password reset with the emailed token")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password updated. You can now log in.", null));
    }

    @Operation(summary = "Current user profile (requires token)")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        AuthenticatedUser principal = currentUserService.requirePrincipal();
        return ResponseEntity.ok(ApiResponse.ok(authService.getProfile(principal.id())));
    }

    @Operation(summary = "Update name/phone (requires token)")
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        AuthenticatedUser principal = currentUserService.requirePrincipal();
        return ResponseEntity.ok(ApiResponse.ok(authService.updateProfile(principal.id(), request)));
    }

    @Operation(summary = "Change password (requires token)")
    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<UserResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        AuthenticatedUser principal = currentUserService.requirePrincipal();
        return ResponseEntity.ok(ApiResponse.ok("Password changed.",
                authService.changePassword(principal.id(), request)));
    }
}
