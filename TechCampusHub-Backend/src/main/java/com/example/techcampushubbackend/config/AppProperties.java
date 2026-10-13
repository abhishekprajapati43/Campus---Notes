package com.example.techcampushubbackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe binding for the {@code app.*} configuration namespace.
 * Every value can be overridden through environment variables (see docs/ENVIRONMENT.md).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl = "http://localhost:8080";
    private final Jwt jwt = new Jwt();
    private final Security security = new Security();
    private final Admin admin = new Admin();
    private final Mail mail = new Mail();
    private final Cloudinary cloudinary = new Cloudinary();
    private final Cors cors = new Cors();

    @Getter
    @Setter
    public static class Jwt {
        /** HS256 secret - must be at least 32 characters in production. */
        private String secret = "dev-only-secret-change-me-0123456789abcdefghijklmnopqrstuvwxyz";
        private long expirationMs = 86_400_000L;
    }

    @Getter
    @Setter
    public static class Security {
        /** When true, login is rejected until the e-mail address is verified. */
        private boolean requireEmailVerification = false;
    }

    @Getter
    @Setter
    public static class Admin {
        /** Bootstrap admin account created at startup if it does not exist. */
        private String email = "admin@techcampushub.com";
        private String password = "Admin@1234";
        private String name = "Administrator";
    }

    @Getter
    @Setter
    public static class Mail {
        private String from = "no-reply@techcampushub.com";
    }

    @Getter
    @Setter
    public static class Cloudinary {
        private String cloudName = "";
        private String apiKey = "";
        private String apiSecret = "";
        private String folder = "techcampushub";

        public boolean isConfigured() {
            return cloudName != null && !cloudName.isBlank()
                    && apiKey != null && !apiKey.isBlank()
                    && apiSecret != null && !apiSecret.isBlank();
        }
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(
                List.of("http://localhost:5500", "http://localhost:3000", "http://localhost:5173"));
    }
}
