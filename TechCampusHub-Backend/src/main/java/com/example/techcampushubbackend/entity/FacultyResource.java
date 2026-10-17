package com.example.techcampushubbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Extra faculty uploads that do not belong to the four legacy content
 * collections (notes / TNP / PYQ / timetables): videos, assignments and
 * other files. Like every other content record, deletion stays
 * admin-only - there is no DELETE route under {@code /api/faculty}.
 */
@Entity
@Table(name = "faculty_resources")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacultyResource extends Auditable {

    /** VIDEO | ASSIGNMENT | OTHER */
    public enum Kind {
        VIDEO,
        ASSIGNMENT,
        OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 600)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    /** Cloudinary URL - only the URL is persisted, never the binary. */
    @Column(name = "file_url", nullable = false, length = 600)
    private String fileUrl;

    /** Cloudinary public id of the same asset, kept so it can be deleted. */
    @Column(name = "storage_path", length = 300)
    private String storagePath;

    /** Original file name, shown in the manage list. */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /** Optional link: resource bundled inside a purchasable course. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course courseBundle;

    /** User id of the faculty member who uploaded this resource. */
    @Column(name = "created_by")
    private Long createdBy;
}
