package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.SearchDtos.SearchHit;
import com.example.techcampushubbackend.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Smart content search: matches topics inside the actual text of uploaded
 * PDFs (pre-indexed page by page at upload time). Students only receive
 * hits from free documents and course bundles they purchased.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Smart full-text search across indexed PDF pages")
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "Search inside indexed PDF content",
            description = "Returns ranked matching pages (document, subject, "
                    + "page number, preview) the caller is allowed to open.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SearchHit>>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(searchService.search(query, limit)));
    }

    @Operation(summary = "Search suggestions (subjects + document titles)")
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<List<String>>> suggest(
            @RequestParam("q") String query) {
        return ResponseEntity.ok(ApiResponse.ok(searchService.suggest(query)));
    }
}
