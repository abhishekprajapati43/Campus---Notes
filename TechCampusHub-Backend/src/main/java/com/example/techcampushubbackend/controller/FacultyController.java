package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.ContentDtos.FileUploadResponse;
import com.example.techcampushubbackend.dto.ContentDtos.NoteRequest;
import com.example.techcampushubbackend.dto.ContentDtos.NoteResponse;
import com.example.techcampushubbackend.dto.CourseDtos.CourseRequest;
import com.example.techcampushubbackend.dto.CourseDtos.CourseResponse;
import com.example.techcampushubbackend.service.CloudinaryService;
import com.example.techcampushubbackend.service.CourseService;
import com.example.techcampushubbackend.service.NoteService;
import com.example.techcampushubbackend.utils.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Faculty content workspace, deliberately mapped under {@code /api/faculty}
 * (never {@code /api/admin}, which SecurityConfig hard-blocks to ADMIN).
 *
 * <p>Faculty may create, list and update <b>their own</b> notes and courses;
 * admins may do the same for anything. <b>There is no DELETE route here and
 * none may ever be added</b> - content deletion stays exclusively behind
 * {@code DELETE /api/admin/**}, which is gated twice (URL matcher +
 * {@code @PreAuthorize}).</p>
 */
@Tag(name = "Faculty", description = "Faculty workspace: create/read/update own notes and courses "
        + "(no delete - deletion is admin-only)")
@RestController
@RequestMapping("/api/faculty")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
public class FacultyController {

    private final NoteService noteService;
    private final CourseService courseService;
    private final CloudinaryService cloudinaryService;
    private final CurrentUserService currentUserService;

    // ------------------------------------------------------------------ notes

    @Operation(summary = "Create a note (faculty/admin) - stamped with the uploader")
    @PostMapping("/notes")
    public ResponseEntity<ApiResponse<NoteResponse>> createNote(
            @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(noteService.createAsOwner(
                        request, currentUserService.requireUser().getId())));
    }

    @Operation(summary = "List my notes (faculty: own only; admins: all)")
    @GetMapping("/notes")
    public ResponseEntity<ApiResponse<List<NoteResponse>>> listNotes() {
        return ResponseEntity.ok(ApiResponse.ok(noteService.listOwn()));
    }

    @Operation(summary = "Update a note (faculty: own notes only -> 403 otherwise; admins: any)",
            description = "Deletion is intentionally not exposed here - use DELETE /api/admin/notes/{id}.")
    @PutMapping("/notes/{id}")
    public ResponseEntity<ApiResponse<NoteResponse>> updateNote(
            @PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(noteService.updateOwned(id, request)));
    }

    // ---------------------------------------------------------------- courses

    @Operation(summary = "Create a course (faculty/admin) - active immediately")
    @PostMapping("/courses")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(courseService.createAsOwner(
                        request, currentUserService.requireUser().getId())));
    }

    @Operation(summary = "List my courses (faculty: own only; admins: all)")
    @GetMapping("/courses")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> listCourses() {
        return ResponseEntity.ok(ApiResponse.ok(courseService.listOwn()));
    }

    // ------------------------------------------------------------------ files

    @Operation(summary = "Upload a PDF/image to Cloudinary (faculty/admin)",
            description = "Same pipeline as POST /api/admin/files; returns the URL to persist "
                    + "on the content record. Deleting assets stays admin-only.")
    @PostMapping("/files")
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "misc") String folder) {
        // Keep the folder segment path-safe (mirrors FileController).
        String safeFolder = folder.replaceAll("[^a-zA-Z0-9_\\-]", "");
        return ResponseEntity.ok(ApiResponse.ok(
                cloudinaryService.upload(file, safeFolder.isBlank() ? "misc" : safeFolder)));
    }
}
