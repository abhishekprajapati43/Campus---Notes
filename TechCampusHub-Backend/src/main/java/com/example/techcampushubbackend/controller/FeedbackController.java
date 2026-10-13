package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.EngagementDtos.FeedbackRequest;
import com.example.techcampushubbackend.dto.EngagementDtos.FeedbackResponse;
import com.example.techcampushubbackend.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Feedback: students submit/read their own; admins read everything. */
@Tag(name = "Feedback", description = "Course feedback and ratings")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "Submit feedback (student)")
    @PostMapping("/feedback")
    public ResponseEntity<ApiResponse<FeedbackResponse>> submit(
            @Valid @RequestBody FeedbackRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Feedback submitted", feedbackService.submit(request)));
    }

    @Operation(summary = "My feedback (student)")
    @GetMapping("/feedback/mine")
    public ResponseEntity<ApiResponse<List<FeedbackResponse>>> mine() {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.listMine()));
    }

    @Operation(summary = "All feedback (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/feedback")
    public ResponseEntity<ApiResponse<List<FeedbackResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.listAll()));
    }
}
