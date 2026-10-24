package com.example.techcampushubbackend.security;

import com.example.techcampushubbackend.config.AppProperties;
import com.example.techcampushubbackend.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

/**
 * Issues and validates stateless HS256 JSON Web Tokens.
 *
 * <p>Claims: {@code sub} = e-mail, {@code uid} = user id, {@code role} = ADMIN|STUDENT.</p>
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_ROLE = "role";

    /** HS256 requires a key of at least 256 bits (32 bytes / characters). */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(AppProperties properties) {
        this.key = resolveSigningKey(properties.getJwt().getSecret());
        this.expirationMs = properties.getJwt().getExpirationMs();
    }

    /**
     * Resolves the HMAC signing key from configuration.
     *
     * <p>The repository ships with a well-known development secret. Signing
     * with it would let anybody mint an {@code ADMIN} token, so an unset,
     * blank or unchanged default secret is replaced with a random
     * per-process key: tokens then stop verifying across restarts and
     * instances (a loud, visible symptom) instead of being forgeable
     * (a silent compromise). A configured secret must be at least
     * {@value #MIN_SECRET_BYTES} characters or startup fails with a clear
     * message rather than a later {@code WeakKeyException}.
     */
    private static SecretKey resolveSigningKey(String secret) {
        String configured = secret == null ? "" : secret.trim();
        if (configured.isEmpty() || AppProperties.Jwt.DEFAULT_SECRET.equals(configured)) {
            byte[] ephemeral = new byte[48];
            new SecureRandom().nextBytes(ephemeral);
            log.warn("JWT_SECRET is not configured (or still the repository default): using a random "
                    + "signing key for this process. Sessions will not survive a restart and tokens "
                    + "will not be shared between instances. Set JWT_SECRET (32+ characters) in production.");
            return Keys.hmacShaKeyFor(ephemeral);
        }
        byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_BYTES + " characters for HS256 "
                    + "(got " + bytes.length + "). Generate one with: openssl rand -base64 48");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(Long userId, String email, Role role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_UID, userId)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /**
     * Parses and verifies a token.
     *
     * @return the authenticated principal, or {@code null} if the token is invalid/expired.
     */
    public AuthenticatedUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long uid = claims.get(CLAIM_UID, Number.class) == null
                    ? null
                    : claims.get(CLAIM_UID, Number.class).longValue();
            String email = claims.getSubject();
            String roleName = claims.get(CLAIM_ROLE, String.class);

            if (uid == null || email == null || roleName == null) {
                return null;
            }
            return new AuthenticatedUser(uid, email, Role.valueOf(roleName));
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
