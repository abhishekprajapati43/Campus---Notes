package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.SiteSettingDtos.SiteSettingRequest;
import com.example.techcampushubbackend.dto.SiteSettingDtos.SiteSettingResponse;
import com.example.techcampushubbackend.service.SiteSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Site settings. Reading is public (the storefront shows the UPI id and the
 * Telegram invite before anyone logs in); writing is admin-only.
 */
@Tag(name = "Settings", description = "Global site settings: UPI id, chatbot link, Telegram invite")
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SiteSettingController {

    private final SiteSettingService siteSettingService;

    @Operation(summary = "Read the current site settings (public)")
    @GetMapping
    public ResponseEntity<ApiResponse<SiteSettingResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(siteSettingService.get()));
    }

    @Operation(summary = "Update site settings (admin; only supplied fields change)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public ResponseEntity<ApiResponse<SiteSettingResponse>> update(
            @Valid @RequestBody SiteSettingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Settings saved", siteSettingService.update(request)));
    }
}
