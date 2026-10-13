package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Liveness/readiness style endpoint for load balancers and monitoring. */
@Tag(name = "Health", description = "Service health")
@RestController
public class HealthController {

    @Operation(summary = "Service health check (public)")
    @GetMapping({"/api/health", "/api/welcome"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "status", "UP",
                "service", "TechCampusHub-Backend",
                "timestamp", Instant.now().toString())));
    }
}
