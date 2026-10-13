package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.AuthDtos.UserResponse;
import com.example.techcampushubbackend.service.AuthService;
import com.example.techcampushubbackend.utils.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin "Users" panel: every registered account (students + admins) with the
 * academic profile captured at sign-up, plus permanent account deletion.
 */
@Tag(name = "Admin users", description = "Registered accounts and account deletion")
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

    @Operation(summary = "Delete a student account and all of its data (admin)",
            description = "Admin accounts are protected: the last admin cannot be deleted.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        authService.deleteAccount(id, currentUserService.requireUser().getId());
        return ResponseEntity.ok(ApiResponse.ok("Account deleted", null));
    }
}
