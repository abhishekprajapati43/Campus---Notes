package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.service.AuthService;
import com.example.techcampushubbackend.utils.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service account management. Replaces the Supabase "delete-account" edge
 * function the static frontend used to call.
 */
@Tag(name = "Account", description = "Self-service account management")
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Permanently delete the caller's account and everything attached to it")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteOwnAccount() {
        Long userId = currentUserService.requireUser().getId();
        authService.deleteAccount(userId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Account deleted", null));
    }
}
