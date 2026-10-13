package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.ContentDtos.CertificateRequest;
import com.example.techcampushubbackend.dto.ContentDtos.CertificateResponse;
import com.example.techcampushubbackend.service.CertificateService;
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

/** Certificates: students see their own; admins manage all. */
@Tag(name = "Certificates", description = "Student certificates")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @Operation(summary = "My certificates (student)")
    @GetMapping("/certificates/mine")
    public ResponseEntity<ApiResponse<List<CertificateResponse>>> mine() {
        return ResponseEntity.ok(ApiResponse.ok(certificateService.listMine()));
    }

    @Operation(summary = "List all certificates (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/certificates")
    public ResponseEntity<ApiResponse<List<CertificateResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(certificateService.listAll()));
    }

    @Operation(summary = "Issue certificate (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/certificates")
    public ResponseEntity<ApiResponse<CertificateResponse>> create(
            @Valid @RequestBody CertificateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(certificateService.create(request)));
    }

    @Operation(summary = "Update certificate (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/certificates/{id}")
    public ResponseEntity<ApiResponse<CertificateResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CertificateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(certificateService.update(id, request)));
    }

    @Operation(summary = "Delete certificate (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/certificates/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        certificateService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Certificate deleted", null));
    }
}
