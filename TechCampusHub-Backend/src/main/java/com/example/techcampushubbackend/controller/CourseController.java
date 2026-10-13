package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.CourseDtos.CourseRequest;
import com.example.techcampushubbackend.dto.CourseDtos.CourseResponse;
import com.example.techcampushubbackend.service.CourseService;
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
 * Course catalog: public browsing for everyone, CRUD restricted to admins.
 */
@Tag(name = "Courses", description = "Public catalog and admin management")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "List active courses (public)")
    @GetMapping("/courses")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> listPublic() {
        return ResponseEntity.ok(ApiResponse.ok(courseService.listPublic()));
    }

    @Operation(summary = "Get one course (public)")
    @GetMapping("/courses/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(courseService.get(id)));
    }

    // ------------------------------------------------------------------ admin

    @Operation(summary = "List all courses incl. inactive (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/courses")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(courseService.listAll()));
    }

    @Operation(summary = "Create course (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/courses")
    public ResponseEntity<ApiResponse<CourseResponse>> create(
            @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(courseService.create(request)));
    }

    @Operation(summary = "Update course (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/courses/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(courseService.update(id, request)));
    }

    @Operation(summary = "Deactivate course (admin, soft delete)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/courses/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        courseService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Course deactivated", null));
    }
}
