package com.example.techcampushubbackend.utils;

import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ApiException;
import com.example.techcampushubbackend.repository.UserRepository;
import com.example.techcampushubbackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the caller of the current request. Used by services and controllers
 * instead of passing the principal around manually.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    /** @return the authenticated principal, or {@code null} when anonymous. */
    public AuthenticatedUser principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user;
    }

    /** @return the authenticated principal; throws 401 when anonymous. */
    public AuthenticatedUser requirePrincipal() {
        AuthenticatedUser principal = principal();
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }

    /** @return the caller's {@link User} entity; throws 401 when anonymous. */
    public User requireUser() {
        AuthenticatedUser principal = requirePrincipal();
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "Authenticated user no longer exists"));
        // A suspended account loses access immediately, even while its JWT is
        // still technically valid.
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account has been suspended");
        }
        return user;
    }
}
