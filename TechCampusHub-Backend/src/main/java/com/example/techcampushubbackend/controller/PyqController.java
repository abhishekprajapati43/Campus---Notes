package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.ContentDtos.PyqRequest;
import com.example.techcampushubbackend.dto.ContentDtos.PyqResponse;
import com.example.techcampushubbackend.service.ContentStreamService;
import com.example.techcampushubbackend.service.PyqService;
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

/** Previous year papers: student reading + admin CRUD. */
@Tag(name = "PYQs", description = "Previous year question papers")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PyqController {

    private final PyqService pyqService;
    private final ContentStreamService contentStreamService;

    @Operation(summary = "List visible PYQs (student)")
    @GetMapping("/pyqs")
    public ResponseEntity<ApiResponse<List<PyqResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(pyqService.listVisible()));
    }

    @Operation(summary = "Get one PYQ (student)")
    @GetMapping("/pyqs/{id}")
    public ResponseEntity<ApiResponse<PyqResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(pyqService.getVisible(id)));
    }

    @Operation(summary = "Download PYQ PDF (streams through the backend)",
            description = "Bytes are relayed server-side under the same visibility "
                    + "check as reading the paper - the storage URL is never exposed.")
    @GetMapping("/pyqs/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        contentStreamService.stream(pyqService.requireVisibleFileUrl(id), response);
    }

    @Operation(summary = "List all PYQs (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/pyqs")
    public ResponseEntity<ApiResponse<List<PyqResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(pyqService.listAll()));
    }

    @Operation(summary = "Create PYQ (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/pyqs")
    public ResponseEntity<ApiResponse<PyqResponse>> create(
            @Valid @RequestBody PyqRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(pyqService.create(request)));
    }

    @Operation(summary = "Update PYQ (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/pyqs/{id}")
    public ResponseEntity<ApiResponse<PyqResponse>> update(
            @PathVariable Long id, @Valid @RequestBody PyqRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(pyqService.update(id, request)));
    }

    @Operation(summary = "Delete PYQ (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/pyqs/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        pyqService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("PYQ deleted", null));
    }
}
