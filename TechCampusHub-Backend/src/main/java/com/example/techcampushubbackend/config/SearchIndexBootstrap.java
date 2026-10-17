package com.example.techcampushubbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Adds the Postgres-only pieces of the smart search index that Hibernate's
 * {@code ddl-auto=update} cannot express: the generated {@code content_tsv}
 * column and its GIN index. Runs once per startup and is idempotent
 * ({@code IF NOT EXISTS} everywhere).
 *
 * <p>Skipped entirely on non-Postgres URLs (the H2 test profile).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchIndexBootstrap implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.contains("postgresql")) {
            return; // FTS DDL is PostgreSQL-only
        }

        try {
            jdbcTemplate.execute("""
                    ALTER TABLE pdf_page_index
                    ADD COLUMN IF NOT EXISTS content_tsv tsvector
                    GENERATED ALWAYS AS (to_tsvector('english', coalesce(content, ''))) STORED
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_pdf_page_index_tsv
                    ON pdf_page_index USING GIN (content_tsv)
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_pdf_page_index_course
                    ON pdf_page_index (course_id)
                    """);
            log.info("PDF search index ready (content_tsv + GIN)");
        } catch (Exception e) {
            log.error("Could not prepare the PDF search index: {}", e.getMessage());
        }
    }
}
