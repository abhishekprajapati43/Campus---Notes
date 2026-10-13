package com.example.techcampushubbackend.security;

import com.example.techcampushubbackend.entity.Role;

/**
 * Authenticated principal stored in the SecurityContext for every request
 * carrying a valid JWT.
 */
public record AuthenticatedUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
