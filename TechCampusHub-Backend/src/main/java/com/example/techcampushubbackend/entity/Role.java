package com.example.techcampushubbackend.entity;

/**
 * Application roles. Stored as strings in the database.
 *
 * <p>Self-registration may only ever produce {@link #STUDENT} or
 * {@link #FACULTY}; {@link #ADMIN} accounts are seeded, never registered.</p>
 */
public enum Role {
    ADMIN,
    FACULTY,
    STUDENT
}
