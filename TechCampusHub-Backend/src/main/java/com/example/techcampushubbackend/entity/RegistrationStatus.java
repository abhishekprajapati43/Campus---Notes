package com.example.techcampushubbackend.entity;

/**
 * Moderation state of an account's registration.
 *
 * <p>Self-registered faculty start as {@link #PENDING} and cannot log in (nor
 * use an existing token) until an administrator moves them to
 * {@link #APPROVED}. {@link #REJECTED} keeps the application on file but blocks
 * access permanently. Students and seeded admins are always
 * {@link #APPROVED}.</p>
 *
 * <p>A {@code null} column value (rows that pre-date this column) is treated
 * as {@link #APPROVED} so existing accounts are never locked out.</p>
 */
public enum RegistrationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
