package com.example.techcampushubbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Broadcast (targetUser = null) or direct notification to one student. */
@Entity
@Table(name = "notifications", indexes = { @Index(name = "idx_notifications_user_id", columnList = "user_id"), @Index(name = "idx_notifications_created_by", columnList = "created_by") })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends Auditable {

    public enum Kind {
        INFO,
        UPDATE,
        OFFER,
        ALERT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    /** Content category used by the frontend styles: general/timetable/course/notes/pyq/tnp. */
    @Column(length = 40)
    private String category;

    /** all | premium | free - who should see a broadcast. */
    @Column(length = 20)
    private String audience;

    /** Email of the admin who sent it (display only). */
    @Column(name = "created_by", length = 200)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User targetUser;
}
