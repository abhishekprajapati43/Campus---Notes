package com.example.techcampushubbackend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.techcampushubbackend.config.AppProperties;
import com.example.techcampushubbackend.dto.ContentDtos.FileUploadResponse;
import com.example.techcampushubbackend.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Uploads PDFs/images to Cloudinary and returns the secure URL.
 * PostgreSQL only ever stores that URL - never the binary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    /** Files we accept for notes/papers/timetables/certificates. */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/jpg");

    private static final long MAX_BYTES = 15L * 1024 * 1024; // 15 MB

    private final Cloudinary cloudinary;
    private final AppProperties properties;

    public FileUploadResponse upload(MultipartFile file, String subFolder) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Only PDF, PNG, JPEG and WEBP files are allowed");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File must be 15MB or smaller");
        }
        if (!properties.getCloudinary().isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "File storage is not configured (CLOUDINARY_* environment variables missing)");
        }

        String folder = properties.getCloudinary().getFolder()
                + (subFolder == null || subFolder.isBlank() ? "" : "/" + subFolder);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", contentType.startsWith("image/") ? "image" : "raw"));

            String url = String.valueOf(result.get("secure_url"));
            String publicId = String.valueOf(result.get("public_id"));
            log.info("Uploaded '{}' -> {}", file.getOriginalFilename(), url);
            return new FileUploadResponse(url, publicId, file.getOriginalFilename(), file.getSize());
        } catch (IOException ex) {
            log.error("Cloudinary upload failed", ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "File upload failed: " + ex.getMessage());
        }
    }

    /**
     * Best-effort deletion of a previously uploaded asset by public id.
     *
     * <p>Returns a short human-readable outcome so callers can surface what
     * actually happened instead of silently keeping orphaned files:
     * {@code "deleted"}, {@code "not-found"}, {@code "skipped"} or
     * {@code "failed"}.</p>
     *
     * @param publicId     Cloudinary public id returned at upload time
     * @param resourceType {@code raw} for PDFs, {@code image} for pictures
     * @return outcome of the deletion attempt
     */
    public String delete(String publicId, String resourceType) {
        if (publicId == null || publicId.isBlank()) {
            return "skipped (no public id)";
        }
        if (!properties.getCloudinary().isConfigured()) {
            // Surface misconfiguration instead of silently keeping orphaned files.
            log.warn("Skipping Cloudinary delete for '{}': CLOUDINARY_* environment "
                    + "variables (cloud name, API key, API secret) are not configured.",
                    publicId);
            return "skipped (Cloudinary not configured)";
        }
        String normalizedType = "image".equalsIgnoreCase(resourceType) ? "image" : "raw";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId,
                    ObjectUtils.asMap("resource_type", normalizedType, "invalidate", true));

            // Cloudinary reports {"result":"ok"} on success; anything else (e.g.
            // "not found") means the asset was not removed.
            String status = result == null ? null : String.valueOf(result.get("result"));
            if ("ok".equalsIgnoreCase(status)) {
                log.info("Deleted Cloudinary asset {} ({})", publicId, normalizedType);
                return "deleted";
            }
            log.warn("Cloudinary did not delete '{}': result={}", publicId, status);
            return "not-found (" + status + ")";
        } catch (IOException ex) {
            log.warn("Could not delete Cloudinary asset {}: {}", publicId, ex.getMessage());
            return "failed (" + ex.getMessage() + ")";
        }
    }
}
