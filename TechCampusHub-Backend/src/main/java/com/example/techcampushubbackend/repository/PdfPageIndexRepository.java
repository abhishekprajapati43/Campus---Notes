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

    boolean existsByCategoryAndContentId(String category, Long contentId);

    /** "category:contentId" of every already-indexed document (backfill). */
    @Query(value = "select distinct p.category || ':' || p.content_id from pdf_page_index p",
            nativeQuery = true)
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

    /** Full-text search restricted to free pages + the given course bundles. */
    @Query(value = """
            select p.category, p.content_id, p.pdf_title, p.subject, p.course_label,
                   p.page_number, p.content,
                   ts_rank(p.content_tsv, q) as rank
            from pdf_page_index p, to_tsquery('english', :tsq) q
            where p.content_tsv @@ q
              and (p.course_id is null or p.course_id in (:courseIds))
            order by rank desc, p.page_number asc
            limit :limit
            """, nativeQuery = true)
    List<Object[]> searchAccessible(@Param("tsq") String tsq,
            @Param("courseIds") Collection<Long> courseIds,
            @Param("limit") int limit);

    @Query(value = "select distinct subject from pdf_page_index "
            + "where subject is not null and subject ilike :pattern "
            + "order by subject limit 8", nativeQuery = true)
    List<String> suggestSubjects(@Param("pattern") String pattern);

    @Query(value = "select distinct pdf_title from pdf_page_index "
            + "where pdf_title is not null and pdf_title ilike :pattern "
            + "order by pdf_title limit 8", nativeQuery = true)
    List<String> suggestTitles(@Param("pattern") String pattern);
}
