package com.example.techcampushubbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Smart search payloads. A hit is one matching PDF page inside a document
 * the caller may open; the preview is a plain-text excerpt and the
 * keyword mirrors the query so the UI can highlight it after escaping.
 */
public final class SearchDtos {

    private SearchDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SearchHit(
            String category,
            Long contentId,
            String title,
            String subject,
            String courseTitle,
            int pageNumber,
            String preview,
            String keyword
    ) {
    }
}
