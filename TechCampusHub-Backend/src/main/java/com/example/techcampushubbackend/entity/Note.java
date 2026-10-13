package com.example.techcampushubbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "notes")
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

    @Column
    private int semester;

    @Column(length = 600)
    private String description;

    /** Cloudinary PDF URL - only the URL is persisted. */
    @Column(name = "file_url", nullable = false, length = 600)
    private String fileUrl;

    /** Optional link: notes belonging to a purchasable course bundle. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course courseBundle;
}
