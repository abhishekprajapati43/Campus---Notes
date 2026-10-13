package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.ContentDtos.CertificateLinkRequest;
import com.example.techcampushubbackend.dto.ContentDtos.CertificateLinkResponse;
import com.example.techcampushubbackend.service.CertificateLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Certificate links: the public gallery on certificates.html (students see
 * active links) plus the admin CRUD behind admin.html.
 */
@Tag(name = "Certificate links", description = "Admin-published certificate links")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CertificateLinkController {

    private final CertificateLinkService certificateLinkService;

    @Operation(summary = "Certificate links visible to the caller (students: active only)")
    @GetMapping("/certificates/links")
    public ResponseEntity<ApiResponse<List<CertificateLinkResponse>>> listVisible() {
        return ResponseEntity.ok(ApiResponse.ok(certificateLinkService.listVisible()));
    }

    @Operation(summary = "All certificate links, incl. hidden (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/certificates/links")
    public ResponseEntity<ApiResponse<List<CertificateLinkResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(certificateLinkService.listVisible()));
    }

    @Operation(summary = "Create a certificate link (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/certificates/links")
    public ResponseEntity<ApiResponse<CertificateLinkResponse>> create(
            @Valid @RequestBody CertificateLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Certificate link added", certificateLinkService.create(request)));
    }

    @Operation(summary = "Update a certificate link (admin, partial)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/certificates/links/{id}")
    public ResponseEntity<ApiResponse<CertificateLinkResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CertificateLinkRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Certificate link updated",
                certificateLinkService.update(id, request)));
    }

    @Operation(summary = "Delete a certificate link (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/certificates/links/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        certificateLinkService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Certificate link deleted", null));
    }
}
