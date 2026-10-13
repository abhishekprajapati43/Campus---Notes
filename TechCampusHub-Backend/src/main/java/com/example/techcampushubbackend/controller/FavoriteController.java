package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.EngagementDtos.FavoriteRequest;
import com.example.techcampushubbackend.dto.EngagementDtos.FavoriteResponse;
import com.example.techcampushubbackend.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Per-user favorites: add, remove, list. */
@Tag(name = "Favorites", description = "Student favorites")
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "List my favorites, optionally filtered by itemType")
    @GetMapping
    public ResponseEntity<ApiResponse<List<FavoriteResponse>>> list(
            @RequestParam(required = false) String itemType) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.listMine(itemType)));
    }

    @Operation(summary = "Add a favorite (idempotent)")
    @PostMapping
    public ResponseEntity<ApiResponse<FavoriteResponse>> add(
            @Valid @RequestBody FavoriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Added to favorites", favoriteService.add(request)));
    }

    @Operation(summary = "Remove a favorite")
    @DeleteMapping("/{itemType}/{itemId}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @PathVariable String itemType, @PathVariable Long itemId) {
        favoriteService.remove(itemType, itemId);
        return ResponseEntity.ok(ApiResponse.ok("Removed from favorites", null));
    }
}
