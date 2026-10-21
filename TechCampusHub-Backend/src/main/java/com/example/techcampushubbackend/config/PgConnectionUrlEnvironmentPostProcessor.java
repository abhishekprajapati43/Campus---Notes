package com.example.techcampushubbackend.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Converts a libpq-style connection string into Spring's JDBC settings.
 *
 * <p>Platforms like Render (Blueprint {@code fromDatabase: connectionString})
 * and Railway ({@code DATABASE_URL}) hand out URLs of the form
 * {@code postgresql://user:pass@host:5432/dbname}. Spring Boot expects a
 * {@code jdbc:postgresql://...} URL plus separate username/password
 * properties, and the PostgreSQL JDBC driver does not understand
 * {@code user:pass@host} authorities at all.
 *
 * <p>This processor reads {@value #CONNECTION_KEY_1} (falling back to
 * {@value #CONNECTION_KEY_2}) before the context refreshes and injects:
 * <ul>
 *   <li>{@code spring.datasource.url} — {@code jdbc:postgresql://host[:port]/db}
 *       plus whitelisted {@code ssl*} query parameters</li>
 *   <li>{@code spring.datasource.username} / {@code spring.datasource.password}
 *       — percent-decoded credentials from the URL</li>
 * </ul>
 *
 * <p>Precedence:
 * <ol>
 *   <li>{@code DB_URL} (and {@code DB_USERNAME}/{@code DB_PASSWORD}) — explicit
 *       overrides always win; nothing is injected when {@code DB_URL} is set.</li>
 *   <li>{@value #CONNECTION_KEY_1} / {@value #CONNECTION_KEY_2} — this processor.</li>
 *   <li>{@code PGHOST}/{@PGPORT}/… chain, then localhost defaults from
 *       {@code application.properties} (local development).</li>
 * </ol>
 *
 * <p>Registered in {@code META-INF/spring.factories}.
 */
public class PgConnectionUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** Render Blueprint key (see {@code render.yaml}). */
    public static final String CONNECTION_KEY_1 = "PG_CONNECTION_URL";
    /** Railway / generic PaaS key. */
    public static final String CONNECTION_KEY_2 = "DATABASE_URL";

    private static final String POSTGRESQL_PREFIX = "postgresql://";
    private static final String POSTGRES_PREFIX = "postgres://";
    private static final String JDBC_PREFIX = "jdbc:postgresql://";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 1. An explicit JDBC URL wins over everything else.
        if (isSet(environment.getProperty("DB_URL"))) {
            return;
        }

        // 2. Find the first configured libpq connection string.
        String raw = null;
        for (String key : new String[] { CONNECTION_KEY_1, CONNECTION_KEY_2 }) {
            String value = environment.getProperty(key);
            if (isSet(value)) {
                raw = value.trim();
                break;
            }
        }
        if (raw == null) {
            return;
        }

        // 3. Parse and inject — addFirst so it overrides the localhost
        //    defaults in application.properties without touching them.
        Parsed parsed = parse(raw);
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", parsed.jdbcUrl());
        if (parsed.user() != null
                && !isSet(environment.getProperty("DB_USERNAME"))
                && !isSet(environment.getProperty("PGUSER"))) {
            props.put("spring.datasource.username", parsed.user());
        }
        if (parsed.password() != null
                && !isSet(environment.getProperty("DB_PASSWORD"))
                && !isSet(environment.getProperty("PGPASSWORD"))) {
            props.put("spring.datasource.password", parsed.password());
        }
        environment.getPropertySources().addFirst(new MapPropertySource("libpqConnection", props));
    }

    /**
     * Parses {@code postgresql://[user[:password]@]host[:port][/database][?params]}
     * into JDBC parts. Static and side-effect free so it can be unit tested.
     *
     * @throws IllegalStateException if the URL has no usable host or database
     *                               (the message never echoes the password)
     */
    static Parsed parse(String raw) {
        String url = raw.trim();
        String scheme = null;
        if (url.toLowerCase(Locale.ROOT).startsWith(POSTGRESQL_PREFIX)) {
            scheme = POSTGRESQL_PREFIX;
        } else if (url.toLowerCase(Locale.ROOT).startsWith(POSTGRES_PREFIX)) {
            scheme = POSTGRES_PREFIX;
        }
        if (scheme == null) {
            throw new IllegalStateException(CONNECTION_KEY_1 + "/" + CONNECTION_KEY_2
                    + " must start with postgresql:// (libpq format), got scheme: "
                    + url.substring(0, Math.min(url.length(), url.indexOf(':') + 1)));
        }

        String rest = url.substring(scheme.length());
        int pathStart = rest.indexOf('/');
        if (pathStart < 0) {
            throw new IllegalStateException("connection string is missing the /database path");
        }
        String authority = rest.substring(0, pathStart);
        String pathAndQuery = rest.substring(pathStart + 1);

        // Credentials: split at the LAST '@' so raw '@' inside a password survives.
        String user = null;
        String password = null;
        String hostPort = authority;
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            String credentials = authority.substring(0, at);
            hostPort = authority.substring(at + 1);
            int colon = credentials.indexOf(':');
            if (colon >= 0) {
                user = percentDecode(credentials.substring(0, colon));
                password = percentDecode(credentials.substring(colon + 1));
            } else {
                user = percentDecode(credentials);
            }
        }

        String host;
        String port = null;
        int colon = hostPort.lastIndexOf(':');
        if (colon >= 0) {
            host = hostPort.substring(0, colon);
            port = hostPort.substring(colon + 1);
            if (!port.isEmpty() && !port.chars().allMatch(Character::isDigit)) {
                throw new IllegalStateException("connection string has a non-numeric port");
            }
        } else {
            host = hostPort;
        }
        if (host.isBlank()) {
            throw new IllegalStateException("connection string has no host");
        }

        int queryStart = pathAndQuery.indexOf('?');
        String path = queryStart >= 0 ? pathAndQuery.substring(0, queryStart) : pathAndQuery;
        String query = queryStart >= 0 ? pathAndQuery.substring(queryStart + 1) : "";
        String database = percentDecode(path);
        if (database.isBlank()) {
            throw new IllegalStateException("connection string has no database name");
        }

        StringBuilder jdbcUrl = new StringBuilder(JDBC_PREFIX).append(host);
        if (port != null && !port.isEmpty()) {
            jdbcUrl.append(':').append(port);
        }
        jdbcUrl.append('/').append(database);

        // Forward only the ssl* parameters (pgjdbc-compatible); drop the rest
        // (e.g. connect_timeout uses libpq semantics pgjdbc does not share).
        boolean firstParam = true;
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = (eq >= 0 ? pair.substring(0, eq) : pair).toLowerCase(Locale.ROOT);
            if (key.startsWith("ssl")) {
                jdbcUrl.append(firstParam ? '?' : '&').append(pair);
                firstParam = false;
            }
        }

        return new Parsed(jdbcUrl.toString(), user, password);
    }

    /** Percent-decodes UTF-8 escapes ({@code %40} → {@code @}); leaves other text as-is. */
    static String percentDecode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        StringBuilder bytes = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '%' && i + 2 < value.length()
                    && isHex(value.charAt(i + 1)) && isHex(value.charAt(i + 2))) {
                bytes.append((char) Integer.parseInt(value.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                bytes.append(ch);
            }
        }
        return new String(bytes.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean isHex(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    /** Result of a successful parse: JDBC URL plus already-decoded credentials. */
    record Parsed(String jdbcUrl, String user, String password) {
    }
}
