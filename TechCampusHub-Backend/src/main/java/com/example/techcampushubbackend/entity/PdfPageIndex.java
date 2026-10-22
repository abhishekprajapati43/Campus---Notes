package com.example.techcampushubbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One extracted page of an uploaded PDF, powering the smart content
 * search. Rows are written once when the document is uploaded (or
 * re-uploaded) - searches only ever read this table, they never touch
 * the stored file.
 *
 * <p>{@code category} + {@code contentId} point back at the source row
 * (notes / pyq / tnp / timetable / resources) so a hit can be opened in
 * the in-app viewer at the exact page. {@code courseId} mirrors the
 * source's course bundle: {@code null} means free content, otherwise the
 * student must hold an approved, unexpired purchase to see the hit.
 *
 * <p>The full-text column {@code content_tsv} is a Postgres generated
 * column (GIN-indexed) added by {@code SearchIndexBootstrap}; it is
 * intentionally not mapped here.
 */
@Entity
@Table(name = "pdf_page_index", uniqueConstraints = {
        @UniqueConstraint(name = "uq_pdf_page_document_page",
                columnNames = {"category", "content_id", "page_number"})
}, indexes = { @Index(name = "idx_pdf_page_index_course_id", columnList = "course_id") })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfPageIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Source bucket: notes | pyq | tnp | timetable | resources. */
    @Column(nullable = false, length = 20)
    private String category;

    /** id of the source row (note/pyq/... entity). */
    @Column(name = "content_id", nullable = false)
    private Long contentId;

    /** Display name of the document (source title, fallback file name). */
    @Column(name = "pdf_title", length = 300)
    private String pdfTitle;

    /** Course bundle id; null = free/public content. */
    @Column(name = "course_id")
    private Long courseId;

    /** Human-readable course label for the result card. */
    @Column(name = "course_label", length = 160)
    private String courseLabel;

    /**
     * Normalised program key (e.g. "mca", "btech") of the source row, kept
     * for PYQ and timetable documents so student search can exclude other
     * programs' papers inside the same SQL. Null = general content (or a
     * category without a program, like notes), visible to every student.
     */
    @Column(name = "program", length = 30)
    private String program;

    /** Subject (or topic/exam type) of the source document. */
    @Column(length = 200)
    private String subject;

    /** 1-based page number inside the PDF. */
    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    /** Text extracted from this page (PDFBox). */
    @Column(columnDefinition = "TEXT")
    private String content;
}
