package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.SearchDtos.SearchHit;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.repository.PdfPageIndexRepository;
import com.example.techcampushubbackend.repository.PurchaseRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Smart content search over the pre-built {@code pdf_page_index} table.
 *
 * <p>Matching uses PostgreSQL full-text search on the generated
 * {@code content_tsv} column (GIN indexed): every query word becomes a
 * prefix term ({@code oop:*} matches "oops"), results are ranked with
 * {@code ts_rank}. No stored file is ever opened at search time.
 *
 * <p>Access is enforced inside the same SQL query: students only receive
 * free pages ({@code course_id IS NULL}) plus pages of course bundles
 * they hold an approved, unexpired purchase of; staff read everything.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_SUGGESTIONS = 8;

    private final PdfPageIndexRepository pdfPageIndexRepository;
    private final PurchaseRepository purchaseRepository;
    private final CurrentUserService currentUserService;

    /** Ranked page hits for a query; empty for blank/unusable queries. */
    @Transactional(readOnly = true)
    public List<SearchHit> search(String query, Integer limit) {
        String tsQuery = buildTsQuery(query);
        if (tsQuery == null) {
            return List.of();
        }

        int capped = limit == null
                ? DEFAULT_LIMIT
                : Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<String> tokens = tokenize(query);
        User user = currentUserService.requireUser();

        List<Object[]> rows;
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.FACULTY) {
            rows = pdfPageIndexRepository.searchAll(tsQuery, capped);
        } else {
            List<Long> accessible = accessibleCourseIds(user.getId());
            rows = pdfPageIndexRepository.searchAccessible(tsQuery, accessible, capped);
        }

        String keyword = query == null ? "" : query.trim();
        return rows.stream()
                .map(row -> toHit(row, tokens, keyword))
                .toList();
    }

    /**
     * Autocomplete for the search box: subjects and document titles that
     * contain the typed fragment ("oop" -> OOP, OOPS Concepts, ...).
     */
    @Transactional(readOnly = true)
    public List<String> suggest(String query) {
        String fragment = query == null ? "" : query.trim();
        if (fragment.length() < 2) {
            return List.of();
        }

        String pattern = "%" + escapeLike(fragment.toLowerCase(Locale.ROOT)) + "%";
        Set<String> merged = new LinkedHashSet<>(
                pdfPageIndexRepository.suggestSubjects(pattern));
        merged.addAll(pdfPageIndexRepository.suggestTitles(pattern));

        return merged.stream().limit(MAX_SUGGESTIONS).toList();
    }

    /** Students: course bundles with an approved, unexpired purchase. */
    private List<Long> accessibleCourseIds(Long userId) {
        Set<Long> courseIds = purchaseRepository
                .findActiveByUser(userId, LocalDateTime.now()).stream()
                .map(purchase -> purchase.getCourse().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<Long> parameters = new ArrayList<>(courseIds);
        if (parameters.isEmpty()) {
            parameters.add(-1L); // keeps the SQL IN-list valid
        }
        return parameters;
    }

    private static SearchHit toHit(Object[] row, List<String> tokens, String keyword) {
        String content = row[6] == null ? "" : (String) row[6];
        return new SearchHit(
                (String) row[0],
                ((Number) row[1]).longValue(),
                (String) row[2],
                (String) row[3],
                (String) row[4],
                ((Number) row[5]).intValue(),
                snippet(content, tokens),
                keyword);
    }

    /**
     * Short excerpt around the first token occurrence so the result card
     * can show why the page matched.
     */
    private static String snippet(String content, List<String> tokens) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String lower = content.toLowerCase(Locale.ROOT);
        int matchIndex = -1;
        String matchedToken = null;
        for (String token : tokens) {
            int index = lower.indexOf(token);
            if (index >= 0 && (matchIndex < 0 || index < matchIndex)) {
                matchIndex = index;
                matchedToken = token;
            }
        }

        if (matchIndex < 0) {
            // Stemmed/prefix match inside the tsvector only - show the head.
            return truncateWithEllipsis(content, 140);
        }

        int start = Math.max(0, matchIndex - 60);
        int end = Math.min(content.length(),
                matchIndex + matchedToken.length() + 90);
        String excerpt = content.substring(start, end)
                .replaceAll("\\s+", " ").trim();

        return (start > 0 ? "…" : "")
                + excerpt
                + (end < content.length() ? "…" : "");
    }

    private static String truncateWithEllipsis(String value, int max) {
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= max
                ? singleLine
                : singleLine.substring(0, max) + "…";
    }

    /**
     * "oops concepts" -> {@code oops:* & concepts:*}: every word matches as
     * a prefix, so partial words still hit. Returns null when the query
     * holds no searchable word.
     */
    private static String buildTsQuery(String query) {
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return null;
        }
        return String.join(" & ", tokens.stream().map(t -> t + ":*").toList());
    }

    /** Lowercased alphanumeric words only (also neutralises tsquery syntax). */
    private static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase(Locale.ROOT)
                        .split("[^a-z0-9]+"))
                .filter(token -> !token.isBlank())
                .distinct()
                .limit(8)
                .toList();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
