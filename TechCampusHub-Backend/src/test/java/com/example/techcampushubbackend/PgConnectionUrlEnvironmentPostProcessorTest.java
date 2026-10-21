package com.example.techcampushubbackend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.example.techcampushubbackend.config.PgConnectionUrlEnvironmentPostProcessor.Parsed;

/**
 * Unit tests for the libpq → JDBC connection-string conversion.
 * These URLs are exactly what Render's {@code connectionString} property
 * and Railway's {@code DATABASE_URL} hand to the app.
 */
class PgConnectionUrlEnvironmentPostProcessorTest {

    private final PgConnectionUrlEnvironmentPostProcessor processor =
            new PgConnectionUrlEnvironmentPostProcessor();

    // ------------------------------------------------------- parsing

    @Test
    void parsesRenderStyleConnectionString() {
        Parsed p = PgConnectionUrlEnvironmentPostProcessor.parse(
                "postgresql://techcampushub:secret123@dpg-abc123-a.5432.techcampushubdb.com/techcampushub");

        assertThat(p.jdbcUrl()).isEqualTo(
                "jdbc:postgresql://dpg-abc123-a.5432.techcampushubdb.com/techcampushub");
        assertThat(p.user()).isEqualTo("techcampushub");
        assertThat(p.password()).isEqualTo("secret123");
    }

    @Test
    void keepsExplicitPortAndDefaultsNone() {
        Parsed withPort = PgConnectionUrlEnvironmentPostProcessor.parse(
                "postgresql://u:p@dbhost:6543/mydb");
        assertThat(withPort.jdbcUrl()).isEqualTo("jdbc:postgresql://dbhost:6543/mydb");

        Parsed withoutPort = PgConnectionUrlEnvironmentPostProcessor.parse(
                "postgres://u:p@dbhost/mydb");
        assertThat(withoutPort.jdbcUrl()).isEqualTo("jdbc:postgresql://dbhost/mydb");
    }

    @Test
    void percentDecodesCredentialsAndDatabase() {
        Parsed p = PgConnectionUrlEnvironmentPostProcessor.parse(
                "postgresql://alice:p%40ss%3Aword@db.example.com:5432/campus%2Dnotes");

        assertThat(p.user()).isEqualTo("alice");
        assertThat(p.password()).isEqualTo("p@ss:word");
        assertThat(p.jdbcUrl()).isEqualTo("jdbc:postgresql://db.example.com:5432/campus-notes");
    }

    @Test
    void toleratesRawAtSignInPassword() {
        // Splitting at the LAST '@' keeps raw '@' inside the password intact.
        Parsed p = PgConnectionUrlEnvironmentPostProcessor.parse(
                "postgresql://u:p@ss@dbhost:5432/mydb");

        assertThat(p.user()).isEqualTo("u");
        assertThat(p.password()).isEqualTo("p@ss");
        assertThat(p.jdbcUrl()).isEqualTo("jdbc:postgresql://dbhost:5432/mydb");
    }

    @Test
    void forwardsOnlySslQueryParameters() {
        Parsed p = PgConnectionUrlEnvironmentPostProcessor.parse(
                "postgresql://u:p@dbhost:5432/mydb?connect_timeout=3&sslmode=require&channel_binding=prefer");

        assertThat(p.jdbcUrl()).isEqualTo("jdbc:postgresql://dbhost:5432/mydb?sslmode=require");
    }

    @Test
    void rejectsNonLibpqUrls() {
        assertThatThrownBy(() -> PgConnectionUrlEnvironmentPostProcessor.parse("mysql://u:p@h/db"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PgConnectionUrlEnvironmentPostProcessor.parse("postgresql://u:p@host"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PgConnectionUrlEnvironmentPostProcessor.parse("postgresql://u:p@/db"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------- environment integration

    @Test
    void injectsJdbcSettingsWhenConnectionUrlIsPresent() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("PG_CONNECTION_URL",
                "postgresql://techcampushub:s3cret@dpg-abc-a:5432/techcampushub");

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dpg-abc-a:5432/techcampushub");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("techcampushub");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("s3cret");
    }

    @Test
    void explicitDbUrlWinsAndNothingIsInjected() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DB_URL", "jdbc:postgresql://manual:5432/other");
        env.setProperty("PG_CONNECTION_URL", "postgresql://u:p@dbhost:5432/mydb");

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.url")).isNull();
        assertThat(env.getProperty("spring.datasource.username")).isNull();
        assertThat(env.getProperty("spring.datasource.password")).isNull();
    }

    @Test
    void explicitUsernamePasswordOverridesAreRespected() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("PG_CONNECTION_URL", "postgresql://urluser:urlpass@dbhost:5432/mydb");
        env.setProperty("DB_USERNAME", "keepme");

        processor.postProcessEnvironment(env, null);

        // URL is always injected…
        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dbhost:5432/mydb");
        // …but an explicit DB_USERNAME/DB_PASSWORD must not be shadowed here;
        // application.properties' ${DB_USERNAME:…} placeholder resolves it instead.
        assertThat(env.getProperty("spring.datasource.username")).isNull();
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("urlpass");
    }

    @Test
    void doesNothingWithoutAnyConnectionUrl() {
        MockEnvironment env = new MockEnvironment();

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.url")).isNull();
        assertThat(env.getProperty("spring.datasource.username")).isNull();
        assertThat(env.getProperty("spring.datasource.password")).isNull();
    }
}
