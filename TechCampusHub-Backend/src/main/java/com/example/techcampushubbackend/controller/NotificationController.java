package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.EngagementDtos.NotificationRequest;
import com.example.techcampushubbackend.dto.EngagementDtos.NotificationResponse;
import com.example.techcampushubbackend.service.NotificationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Notifications: students read broadcasts + their direct messages;
 * admins send and manage them.
 */
@Tag(name = "Notifications", description = "Broadcast and direct notifications")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "My notifications (student: broadcasts + direct)")
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> mine() {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.listMine()));
    }

    @Operation(summary = "All notifications (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/notifications")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.listAll()));
    }

    @Operation(summary = "Send notification (admin; omit userId to broadcast)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/notifications")
    public ResponseEntity<ApiResponse<NotificationResponse>> send(
            @Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Notification sent", notificationService.send(request)));
    }

    @Operation(summary = "Delete notification (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/notifications/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Notification deleted", null));
    }
}
