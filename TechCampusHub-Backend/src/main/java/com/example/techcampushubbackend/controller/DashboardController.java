package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.DashboardDtos.DashboardSummary;
import com.example.techcampushubbackend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard summary endpoint. The student dashboard reads every card count
 * from this one response, so page load makes a single request.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Availability counts for the signed-in student")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Availability counts for the dashboard cards",
            description = "Free resources plus resources of course bundles the "
                    + "caller holds an approved, unexpired purchase of. Locked, "
                    + "hidden, deleted, draft and expired material is never counted.")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummary>> summary() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.summary()));
    }
}
