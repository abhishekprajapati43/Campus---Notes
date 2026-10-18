package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.PdfPageIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Reads/writes of the PDF page index. The full-text queries are native
 * Postgres SQL against the generated {@code content_tsv} column (GIN
 * indexed) - they are only ever executed on the Postgres dev/prod
 * database, never on the H2 test profile.
 */
@Repository
public interface PdfPageIndexRepository extends JpaRepository<PdfPageIndex, Long> {

    void deleteByCategoryAndContentId(String category, Long contentId);

    /**
     * "category:contentId" of every already-indexed document (backfill).
     * PYQ and timetable rows indexed before the program column existed are
     * deliberately NOT reported here, so the backfill re-indexes them once
     * with their program - otherwise a stale null program would read as
     * "general content" and leak another program's papers into student
     * search results.
     */
    @Query(value = """
            select distinct p.category || ':' || p.content_id
            from pdf_page_index p
            where not (p.category in ('pyq', 'timetable') and p.program is null)
            """, nativeQuery = true)
    List<String> findIndexedDocumentKeys();

    /** Full-text search over every indexed page (staff accounts). */
    @Query(value = """
            select p.category, p.content_id, p.pdf_title, p.subject, p.course_label,
                   p.page_number, p.content,
                   ts_rank(p.content_tsv, q) as rank
            from pdf_page_index p, to_tsquery('english', :tsq) q
            where p.content_tsv @@ q
            order by rank desc, p.page_number asc
            limit :limit
            """, nativeQuery = true)
    List<Object[]> searchAll(@Param("tsq") String tsq, @Param("limit") int limit);

    /**
     * Full-text search restricted to free pages + the given course bundles.
     * On top of the bundle rule, students only match PYQ/timetable pages of
     * their own program: another program's rows are excluded inside the
     * same SQL (a null program reads as general content and stays visible),
     * so hits can never leak an MCA paper to a B.Tech student.
     * Notes/TNP/resources carry no program scoping.
     */
    @Query(value = """
            select p.category, p.content_id, p.pdf_title, p.subject, p.course_label,
                   p.page_number, p.content,
                   ts_rank(p.content_tsv, q) as rank
            from pdf_page_index p, to_tsquery('english', :tsq) q
            where p.content_tsv @@ q
              and (p.course_id is null or p.course_id in (:courseIds))
              and (p.category not in ('pyq', 'timetable')
                   or p.program is null or p.program = :programKey)
            order by rank desc, p.page_number asc
            limit :limit
            """, nativeQuery = true)
    List<Object[]> searchAccessible(@Param("tsq") String tsq,
            @Param("courseIds") Collection<Long> courseIds,
            @Param("programKey") String programKey,
            @Param("limit") int limit);

    @Query(value = "select distinct subject from pdf_page_index "
            + "where subject is not null and subject ilike :pattern "
            + "order by subject limit 8", nativeQuery = true)
    List<String> suggestSubjects(@Param("pattern") String pattern);

    @Query(value = "select distinct pdf_title from pdf_page_index "
            + "where pdf_title is not null and pdf_title ilike :pattern "
            + "order by pdf_title limit 8", nativeQuery = true)
    List<String> suggestTitles(@Param("pattern") String pattern);

    /**
     * Subject suggestions restricted to free pages + the given course
     * bundles: autocomplete must never reveal a restricted document's
     * subject to a student who cannot open it. PYQ/timetable subjects are
     * additionally scoped to the caller's program (null = general).
     */
    @Query(value = "select distinct subject from pdf_page_index "
            + "where subject is not null and subject ilike :pattern "
            + "and (course_id is null or course_id in (:courseIds)) "
            + "and (category not in ('pyq', 'timetable') "
            + "     or program is null or program = :programKey) "
            + "order by subject limit 8", nativeQuery = true)
    List<String> suggestSubjectsAccessible(@Param("pattern") String pattern,
            @Param("courseIds") Collection<Long> courseIds,
            @Param("programKey") String programKey);

    /** Document-title suggestions with the same access restriction. */
    @Query(value = "select distinct pdf_title from pdf_page_index "
            + "where pdf_title is not null and pdf_title ilike :pattern "
            + "and (course_id is null or course_id in (:courseIds)) "
            + "and (category not in ('pyq', 'timetable') "
            + "     or program is null or program = :programKey) "
            + "order by pdf_title limit 8", nativeQuery = true)
    List<String> suggestTitlesAccessible(@Param("pattern") String pattern,
            @Param("courseIds") Collection<Long> courseIds,
            @Param("programKey") String programKey);
}
