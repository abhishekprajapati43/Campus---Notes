package com.example.techcampushubbackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Request/response payloads for the content collections:
 * notes, PYQs, TNP notes, timetables and certificates.
 *
 * <p>The client either passes a pre-uploaded Cloudinary {@code fileUrl}, or uses the
 * {@code POST /api/admin/files} multipart endpoint to upload and then stores the URL.</p>
 */
public final class ContentDtos {

    private ContentDtos() {
    }

    // ---------------------------------------------------------------- notes

    public record NoteRequest(
            @NotBlank(message = "title is required")
            @Size(max = 200) String title,

            @Size(max = 120) String subject,

            @Size(max = 60) String course,

            Integer semester,

            @Size(max = 600) String description,

            @NotBlank(message = "fileUrl is required")
            @Size(max = 600)
            @Pattern(regexp = "^https?://.*", message = "fileUrl must be an http(s) URL")
            String fileUrl,

            Long courseId) {
    }

    public record NoteResponse(
            Long id,
            String title,
            String subject,
            String course,
            int semester,
            String description,
            String fileUrl,
            Long courseId,
            String courseTitle,
            LocalDateTime createdAt) {
    }

    // ------------------------------------------------------------------ PYQ

    public record PyqRequest(
            @NotBlank(message = "title is required")
            @Size(max = 200) String title,

            @Size(max = 120) String subject,

            @Size(max = 60) String course,

            Integer semester,

            Integer examYear,

            @Size(max = 600) String description,

            @NotBlank(message = "fileUrl is required")
            @Size(max = 600)
            @Pattern(regexp = "^https?://.*", message = "fileUrl must be an http(s) URL")
            String fileUrl,

            Long courseId) {
    }

    public record PyqResponse(
            Long id,
            String title,
            String subject,
            String course,
            int semester,
            Integer examYear,
            String description,
            String fileUrl,
            Long courseId,
            String courseTitle,
            LocalDateTime createdAt) {
    }

    // ------------------------------------------------------------- TNP notes

    public record TnpNoteRequest(
            @NotBlank(message = "title is required")
            @Size(max = 200) String title,

            @Size(max = 120) String topic,

            @Size(max = 600) String description,

            @NotBlank(message = "fileUrl is required")
            @Size(max = 600)
            @Pattern(regexp = "^https?://.*", message = "fileUrl must be an http(s) URL")
            String fileUrl,

            Long courseId) {
    }

    public record TnpNoteResponse(
            Long id,
            String title,
            String topic,
            String description,
            String fileUrl,
            Long courseId,
            String courseTitle,
            LocalDateTime createdAt) {
    }

    // ------------------------------------------------------------- timetables

    public record TimetableRequest(
            @NotBlank(message = "title is required")
            @Size(max = 200) String title,

            @Size(max = 60) String course,

            Integer semester,

            @Size(max = 40) String dayOfWeek,

            LocalDate effectiveFrom,

            @NotBlank(message = "fileUrl is required")
            @Size(max = 600)
            @Pattern(regexp = "^https?://.*", message = "fileUrl must be an http(s) URL")
            String fileUrl,

            Long courseId) {
    }

    public record TimetableResponse(
            Long id,
            String title,
            String course,
            int semester,
            String dayOfWeek,
            LocalDate effectiveFrom,
            String fileUrl,
            Long courseId,
            String courseTitle,
            LocalDateTime createdAt) {
    }

    // ------------------------------------------------------------ certificates

    public record CertificateRequest(
            @Size(max = 60) String certificateNo,

            @NotBlank(message = "title is required")
            @Size(max = 200) String title,

            Long userId,

            Long courseId,

            LocalDate issuedOn,

            @NotBlank(message = "fileUrl is required")
            @Size(max = 600)
            @Pattern(regexp = "^https?://.*", message = "fileUrl must be an http(s) URL")
            String fileUrl) {
    }

    public record CertificateResponse(
            Long id,
            String certificateNo,
            String title,
            Long userId,
            String userName,
            Long courseId,
            String courseTitle,
            LocalDate issuedOn,
            String fileUrl,
            LocalDateTime createdAt) {
    }

    // -------------------------------------------------------------- file upload

    /** Result of POST /api/admin/files - the URL that gets persisted. */
    public record FileUploadResponse(
            String url,
            String publicId,
            String originalName,
            long sizeBytes) {
    }

    public record FileUploadRequest(
            @NotBlank(message = "fileUrl is required")
            String fileUrl,
            @NotNull(message = "itemType is required")
            String itemType,
            @NotNull(message = "itemId is required")
            Long itemId) {
    }
}
