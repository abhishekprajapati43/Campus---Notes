package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.ContentDtos.TimetableRequest;
import com.example.techcampushubbackend.dto.ContentDtos.TimetableResponse;
import com.example.techcampushubbackend.service.ContentStreamService;
import com.example.techcampushubbackend.service.TimetableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

/** Timetables: student reading + admin CRUD. */
@Tag(name = "Timetables", description = "Class timetables")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;
    private final ContentStreamService contentStreamService;

    @Operation(summary = "List visible timetables (student)")
    @GetMapping("/timetables")
    public ResponseEntity<ApiResponse<List<TimetableResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(timetableService.listVisible()));
    }

    @Operation(summary = "Get one timetable (student)")
    @GetMapping("/timetables/{id}")
    public ResponseEntity<ApiResponse<TimetableResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(timetableService.getVisible(id)));
    }

    @Operation(summary = "Download timetable file (streams through the backend)",
            description = "Bytes are relayed server-side under the same visibility "
                    + "check as reading the timetable - the storage URL is never exposed.")
    @GetMapping("/timetables/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        contentStreamService.stream(timetableService.requireVisibleFileUrl(id), response);
    }

    @Operation(summary = "List all timetables (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/timetables")
    public ResponseEntity<ApiResponse<List<TimetableResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(timetableService.listAll()));
    }

    @Operation(summary = "Create timetable (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/timetables")
    public ResponseEntity<ApiResponse<TimetableResponse>> create(
            @Valid @RequestBody TimetableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(timetableService.create(request)));
    }

    @Operation(summary = "Update timetable (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/timetables/{id}")
    public ResponseEntity<ApiResponse<TimetableResponse>> update(
            @PathVariable Long id, @Valid @RequestBody TimetableRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(timetableService.update(id, request)));
    }

    @Operation(summary = "Delete timetable (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/timetables/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        timetableService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Timetable deleted", null));
    }
}
