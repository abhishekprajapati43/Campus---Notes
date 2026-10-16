package com.example.techcampushubbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    /** BCrypt-encoded password. Never exposed through the API. */
    @Column(nullable = false)
    private String password;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Academic profile captured at sign-up, e.g. "MCA", "B.Tech". */
    @Column(length = 60)
    private String course;

    /** Branch/specialisation, e.g. "CSE", "AI_ML". */
    @Column(length = 60)
    private String branch;

    /** Quoted: YEAR is reserved in H2/PostgreSQL. */
    @Column(name = "\"year\"")
    private Integer year;

    @Column
    private Integer semester;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "verification_token", length = 64)
    private String verificationToken;

    @Column(name = "reset_token", length = 64)
    private String resetToken;

    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * SHA-256 hex hash (64 chars) of the faculty member's raw 12-digit Aadhaar
     * number. The raw value is hashed in {@code AuthService.register} and never
     * persisted or logged; this column must NEVER be exposed in API responses.
     */
    @Column(name = "aadhaar_number", length = 64)
    private String aadhaarNumber;

    /** Postal address captured during faculty registration. */
    @Column(columnDefinition = "TEXT")
    private String address;

    /** Gender captured at sign-up (optional, shown on the profile pages). */
    @Column(length = 20)
    private String gender;

    /** Student roll / enrollment number captured at sign-up. */
    @Column(name = "enrollment_number", length = 40)
    private String enrollmentNumber;

    /** Faculty only: home department, e.g. "Computer Science". */
    @Column(length = 120)
    private String department;

    /** Faculty only: highest qualification, e.g. "M.Tech". */
    @Column(length = 120)
    private String qualification;

    /** Faculty only: teaching/industry experience, e.g. "5 years". */
    @Column(length = 60)
    private String experience;

    /** Faculty sign-up declaration (privacy/accuracy consent); always false for students. */
    @Column(name = "declaration_accepted", nullable = false)
    @Builder.Default
    private boolean declarationAccepted = false;

    /**
     * Registration moderation state. Faculty sign-ups are created as
     * {@link RegistrationStatus#PENDING} and only become usable once an
     * administrator approves them. The column is deliberately nullable so
     * Hibernate can add it to a populated table without a failure; rows that
     * pre-date it read as {@link RegistrationStatus#APPROVED} through
     * {@link #registrationStatusOrDefault()}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", length = 20)
    @Builder.Default
    private RegistrationStatus registrationStatus = RegistrationStatus.APPROVED;

    /** Never returns null - legacy rows without a stored status count as approved. */
    public RegistrationStatus registrationStatusOrDefault() {
        return registrationStatus == null ? RegistrationStatus.APPROVED : registrationStatus;
    }
}
