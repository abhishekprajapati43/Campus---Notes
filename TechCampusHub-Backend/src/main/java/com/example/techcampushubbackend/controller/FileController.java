package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.ContentDtos.FileUploadResponse;
import com.example.techcampushubbackend.service.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin-only file upload: sends the file to Cloudinary and returns the secure
 * URL, which is then stored in PostgreSQL when creating/updating content.
 */
@Tag(name = "Files", description = "Admin file uploads backed by Cloudinary")
@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
public class FileController {

    private final CloudinaryService cloudinaryService;

    @Operation(summary = "Upload a PDF/image to Cloudinary (admin)",
            description = "Returns the secure URL to persist in the content record.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "misc") String folder) {
        // Keep the folder segment path-safe.
        String safeFolder = folder.replaceAll("[^a-zA-Z0-9_\\-]", "");
        return ResponseEntity.ok(ApiResponse.ok(
                cloudinaryService.upload(file, safeFolder.isBlank() ? "misc" : safeFolder)));
    }
}
