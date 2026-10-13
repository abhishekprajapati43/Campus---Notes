package com.example.techcampushubbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A certificate the admin publishes as an external link (Google Drive, an
 * institute portal, ...) that every student can open from certificates.html.
 *
 * <p>Distinct from {@link Certificate}, which models a certificate issued to one
 * specific student. This table backs the public "certificate links" gallery.</p>
 */
@Entity
@Table(name = "certificate_links")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateLink extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    /** Destination the student is sent to. */
    @Column(nullable = false, length = 600)
    private String url;

    @Column(length = 600)
    private String description;

    /** Display order on the certificates page. */
    @Column(name = "sort_order")
    private int sortOrder;

    /** Hidden links stay visible to admins but never to students. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
