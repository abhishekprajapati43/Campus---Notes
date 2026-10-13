package com.example.techcampushubbackend.security;

import com.example.techcampushubbackend.config.AppProperties;
import com.example.techcampushubbackend.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Issues and validates stateless HS256 JSON Web Tokens.
 *
 * <p>Claims: {@code sub} = e-mail, {@code uid} = user id, {@code role} = ADMIN|STUDENT.</p>
 */
@Service
public class JwtService {

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(AppProperties properties) {
        this.key = Keys.hmacShaKeyFor(
                properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.getJwt().getExpirationMs();
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
