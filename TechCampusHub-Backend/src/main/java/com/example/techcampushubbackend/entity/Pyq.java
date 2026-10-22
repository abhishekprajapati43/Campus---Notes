package com.example.techcampushubbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pyqs", indexes = @Index(name = "idx_pyqs_created_by", columnList = "created_by"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pyq extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 120)
    private String subject;

    @Column(length = 60)
    private String course;

    /** Branch/specialisation, e.g. CSE. */
    @Column(length = 40)
    private String branch;

    @Column
    private int semester;

    /** Year the paper belonged to, e.g. 2024. */
    @Column(name = "exam_year")
    private Integer examYear;

    @Column(length = 600)
    private String description;

    /** Cloudinary PDF URL. */
    @Column(name = "file_url", nullable = false, length = 600)
    private String fileUrl;

    /** Cloudinary public id of the same asset, kept so it can be deleted. */
    @Column(name = "storage_path", length = 300)
    private String storagePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course courseBundle;

    /**
     * users.id of the faculty/admin uploader (soft ref, no FK - mirrors notes).
     * NULL for content created through the legacy admin path.
     */
    @Column(name = "created_by")
    private Long createdBy;
}
