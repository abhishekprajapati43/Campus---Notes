package com.example.techcampushubbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "courses", indexes = @Index(name = "idx_courses_created_by", columnList = "created_by"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 40)
    private String code;

    @Column(length = 60)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Price in INR. */
    @Column(name = "price_inr", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceInr;

    /** Maximum retail price in INR; {@code null} = no discount shown on the card. */
    @Column(name = "mrp_inr", precision = 10, scale = 2)
    private BigDecimal mrpInr;

    /** Cloudinary thumbnail URL (only the URL is stored). */
    @Column(name = "thumbnail_url", length = 600)
    private String thumbnailUrl;

    /** Access granted to a purchaser, in days. */
    @Column(name = "validity_days", nullable = false)
    private int validityDays;

    /** URL-friendly identifier used in shareable course links. */
    @Column(length = 80)
    private String slug;

    /** Selling points shown on the course card, one per line. */
    @Column(columnDefinition = "TEXT")
    private String highlights;

    /** Display order in the catalog. */
    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** User id of the faculty/admin who launched this course (soft reference to users.id). */
    @Column(name = "created_by")
    private Long createdBy;
}
