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
@Table(name = "notes", indexes = @Index(name = "idx_notes_created_by", columnList = "created_by"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Note extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 120)
    private String subject;

    /** Course/programme label, e.g. MCA, BTech. */
    @Column(length = 60)
    private String course;

    /** Branch/specialisation, e.g. CSE. */
    @Column(length = 40)
    private String branch;

    /** Academic year the notes belong to, e.g. 2026. Quoted: YEAR is reserved in H2/PG. */
    @Column(name = "\"year\"")
    private Integer year;

    @Column
    private int semester;

    @Column(length = 600)
    private String description;

    /** Cloudinary PDF URL - only the URL is persisted. */
    @Column(name = "file_url", nullable = false, length = 600)
    private String fileUrl;

    /** Cloudinary public id of the same asset, kept so it can be deleted. */
    @Column(name = "storage_path", length = 300)
    private String storagePath;

    /** Optional link: notes belonging to a purchasable course bundle. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course courseBundle;

    /** User id of the faculty/admin who uploaded this note (soft reference to users.id). */
    @Column(name = "created_by")
    private Long createdBy;
}
