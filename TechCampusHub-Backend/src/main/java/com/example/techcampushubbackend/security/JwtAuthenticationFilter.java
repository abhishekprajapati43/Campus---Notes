package com.example.techcampushubbackend.security;

import com.example.techcampushubbackend.dto.ErrorResponse;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <jwt>} and, when the token is valid,
 * populates the {@link org.springframework.security.core.context.SecurityContextHolder}.
 * Invalid or missing tokens simply leave the request unauthenticated; the entry
 * point then answers 401/403 as appropriate.
 *
 * <p>A token is valid for {@code app.jwt.expiration-ms} (24 h by default), so it
 * cannot be the only source of truth: the account row is re-read here on every
 * request. That is what makes an admin "Remove User" (suspend) take effect
 * immediately instead of at token expiry, and what keeps a demoted role from
 * working with an old token.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    /** Login/register must stay reachable so AuthService can explain the state. */
    private static final String AUTH_PATH_PREFIX = "/api/auth/";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(BEARER_PREFIX.length()).trim();
            AuthenticatedUser tokenUser = jwtService.parse(token);
            if (tokenUser != null) {
                User user = userRepository.findById(tokenUser.id()).orElse(null);
                if (user == null) {
                    // Account deleted after the token was issued: stay
                    // unauthenticated so the entry point answers 401.
                    tokenUser = null;
                } else if (!user.isEnabled()
                        && !request.getRequestURI().startsWith(AUTH_PATH_PREFIX)) {
                    // Suspension is enforced here, not only in
                    // CurrentUserService: admin/faculty routes protected by
                    // role checks never reach that service. Without this the
                    // account would keep working until the JWT expired.
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                            "Your account has been suspended", request.getRequestURI());
                    return;
                } else {
                    // Role and e-mail come from the database, never from a
                    // day-old claim: a demotion applies to the next request.
                    tokenUser = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
                }

                if (tokenUser != null) {
                    var authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + tokenUser.role().name()));
                    var authentication = new UsernamePasswordAuthenticationToken(
                            tokenUser, null, authorities);
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeJson(HttpServletResponse response, int status, String error, String message,
                           String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(status, error, message, path));
    }
}
