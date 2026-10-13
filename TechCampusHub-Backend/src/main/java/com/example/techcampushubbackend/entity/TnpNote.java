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

/** Training &amp; Placement (TNP) notes: interview prep, aptitude, resume tips. */
@Entity
@Table(name = "tnp_notes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnpNote extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    /** Topic area, e.g. Aptitude, Interviews, Resume. */
    @Column(length = 120)
    private String topic;

    @Column(length = 600)
    private String description;

    /** Cloudinary PDF URL. */
    @Column(name = "file_url", nullable = false, length = 600)
    private String fileUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course courseBundle;
}
