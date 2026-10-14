package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.PurchaseDtos.ExtendValidityRequest;
import com.example.techcampushubbackend.dto.PurchaseDtos.PurchaseApproveRequest;
import com.example.techcampushubbackend.dto.PurchaseDtos.PurchaseCreateRequest;
import com.example.techcampushubbackend.dto.PurchaseDtos.PurchaseResponse;
import com.example.techcampushubbackend.dto.PurchaseDtos.PurchaseReviewRequest;
import com.example.techcampushubbackend.dto.PurchaseDtos.SetValidityRequest;
import com.example.techcampushubbackend.service.PurchaseService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Purchases: students submit UPI references and track their access;
 * admins approve/reject requests and extend course validity.
 */
@Tag(name = "Purchases", description = "Course purchase requests and admin review")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @Operation(summary = "Request a course purchase with a UPI transaction reference")
    @PostMapping("/purchases")
    public ResponseEntity<ApiResponse<PurchaseResponse>> requestPurchase(
            @Valid @RequestBody PurchaseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Purchase request submitted for review",
                        purchaseService.request(request)));
    }

    @Operation(summary = "My purchase history")
    @GetMapping("/purchases/mine")
    public ResponseEntity<ApiResponse<List<PurchaseResponse>>> myPurchases() {
        return ResponseEntity.ok(ApiResponse.ok(purchaseService.listMine()));
    }

    @Operation(summary = "My active (approved, unexpired) courses")
    @GetMapping("/purchases/my-courses")
    public ResponseEntity<ApiResponse<List<PurchaseResponse>>> myActiveCourses() {
        return ResponseEntity.ok(ApiResponse.ok(purchaseService.listMyActive()));
    }

    // ------------------------------------------------------------------ admin

    @Operation(summary = "List purchases, optionally filtered by status (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/purchases")
    public ResponseEntity<ApiResponse<List<PurchaseResponse>>> list(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseService.listByStatus(status)));
    }

    @Operation(summary = "Approve a pending purchase (admin)",
            description = "Optionally pass validityDays (e.g. 30/90/180/365) or an "
                    + "exact validUntil to override the course's default validity.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/purchases/{id}/approve")
    public ResponseEntity<ApiResponse<PurchaseResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) PurchaseApproveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Purchase approved", purchaseService.approve(id, request)));
    }

    @Operation(summary = "Reject a pending purchase (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/purchases/{id}/reject")
    public ResponseEntity<ApiResponse<PurchaseResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) PurchaseReviewRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(ApiResponse.ok("Purchase rejected",
                purchaseService.reject(id, reason)));
    }

    @Operation(summary = "Extend an approved purchase's validity (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/purchases/{id}/extend-validity")
    public ResponseEntity<ApiResponse<PurchaseResponse>> extend(
            @PathVariable Long id, @Valid @RequestBody ExtendValidityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Validity extended",
                purchaseService.extendValidity(id, request.days())));
    }

    @Operation(summary = "Set an exact expiry date on an approved purchase (admin)",
            description = "Replaces the current expiry with the given date (must be in the future).")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/purchases/{id}/validity")
    public ResponseEntity<ApiResponse<PurchaseResponse>> setValidity(
            @PathVariable Long id, @Valid @RequestBody SetValidityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Validity updated",
                purchaseService.setValidity(id, request.validUntil())));
    }

    @Operation(summary = "Delete a purchase record (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/purchases/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        purchaseService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Purchase deleted", null));
    }
}
