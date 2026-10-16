package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.AuthDtos.UserEnabledRequest;
import com.example.techcampushubbackend.dto.AuthDtos.UserRegistrationStatusRequest;
import com.example.techcampushubbackend.dto.AuthDtos.UserResponse;
import com.example.techcampushubbackend.entity.RegistrationStatus;
import com.example.techcampushubbackend.service.AuthService;
import com.example.techcampushubbackend.utils.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin "Users" panel: every registered account (students + admins) with the
 * academic profile captured at sign-up, plus suspension and permanent account
 * deletion.
 */
@Tag(name = "Admin users", description = "Registered accounts, suspension and account deletion")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "List every registered account (admin)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(authService.listUsers()));
    }

    @Operation(summary = "Suspend or re-enable an account (admin)",
            description = "A suspended account keeps all of its data but cannot "
                    + "log in until it is re-enabled (enabled=false suspends).")
    @PatchMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<UserResponse>> setEnabled(
            @PathVariable Long id,
            @Valid @RequestBody UserEnabledRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                authService.setUserEnabled(id, request.enabled(),
                        currentUserService.requireUser().getId())));
    }

    @Operation(summary = "Approve or reject a faculty registration (admin)",
            description = "Pending faculty cannot log in until status=APPROVED; "
                    + "REJECTED keeps the application on file but blocks access. "
                    + "Only FACULTY accounts are accepted.")
    @PatchMapping("/{id}/registration-status")
    public ResponseEntity<ApiResponse<UserResponse>> setRegistrationStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserRegistrationStatusRequest request) {
        UserResponse user = authService.setRegistrationStatus(id, request.status(),
                currentUserService.requireUser().getId());
        String message = request.status() == RegistrationStatus.APPROVED
                ? "Faculty member approved"
                : "Faculty application rejected";
        return ResponseEntity.ok(ApiResponse.ok(message, user));
    }

    @Operation(summary = "Delete a student account and all of its data (admin)",
            description = "Admin accounts are protected: the last admin cannot be deleted.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        authService.deleteAccount(id, currentUserService.requireUser().getId());
        return ResponseEntity.ok(ApiResponse.ok("Account deleted", null));
    }
}
