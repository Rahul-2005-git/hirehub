package com.hirehub.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for every integration test. Boots the full Spring context against PostgreSQL.
 *
 * <p><b>Why a container instead of H2:</b> the schema relies on PostgreSQL-specific
 * features — {@code pg_trgm}, a GIN index, a functional index on {@code lower(location)},
 * {@code TIMESTAMPTZ}, and CHECK constraints. H2 in PostgreSQL-compatibility mode either
 * rejects or silently ignores several of them, so tests would pass against a database that
 * does not resemble production. Flyway runs here exactly as it does in production.</p>
 *
 * <p>By default, a singleton Testcontainers database is used. To run integration tests
 * without Docker, set {@code TEST_DB_URL}, {@code TEST_DB_USERNAME} and
 * {@code TEST_DB_PASSWORD} to a dedicated local PostgreSQL test database.</p>
 *
 * <p>The test database must be disposable: the tests run Flyway migrations and create
 * test data in it.</p>
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    private static final String RESOLVED_DB_URL;
    private static final String RESOLVED_DB_USERNAME;
    private static final String RESOLVED_DB_PASSWORD;
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        String envOrPropUrl = getPropertyOrEnv("TEST_DB_URL");
        if (envOrPropUrl != null && !envOrPropUrl.isBlank()) {
            RESOLVED_DB_URL = envOrPropUrl;
            RESOLVED_DB_USERNAME = getPropertyOrEnv("TEST_DB_USERNAME", "hirehub");
            RESOLVED_DB_PASSWORD = getPropertyOrEnv("TEST_DB_PASSWORD", "hirehub");
            POSTGRES = null;
        } else if (isDockerAvailable()) {
            POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
            POSTGRES.start();
            RESOLVED_DB_URL = POSTGRES.getJdbcUrl();
            RESOLVED_DB_USERNAME = POSTGRES.getUsername();
            RESOLVED_DB_PASSWORD = POSTGRES.getPassword();
        } else if (isLocalPostgresRunning()) {
            RESOLVED_DB_URL = "jdbc:postgresql://localhost:5432/hirehub";
            RESOLVED_DB_USERNAME = getPropertyOrEnv("TEST_DB_USERNAME", "hirehub");
            RESOLVED_DB_PASSWORD = getPropertyOrEnv("TEST_DB_PASSWORD", "hirehub");
            POSTGRES = null;
        } else {
            POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
            POSTGRES.start();
            RESOLVED_DB_URL = POSTGRES.getJdbcUrl();
            RESOLVED_DB_USERNAME = POSTGRES.getUsername();
            RESOLVED_DB_PASSWORD = POSTGRES.getPassword();
        }
    }

    private static String getPropertyOrEnv(String name) {
        return getPropertyOrEnv(name, null);
    }

    private static String getPropertyOrEnv(String name, String defaultValue) {
        String val = System.getProperty(name);
        if (val != null && !val.isBlank()) {
            return val;
        }
        val = System.getenv(name);
        if (val != null && !val.isBlank()) {
            return val;
        }
        return defaultValue;
    }

    private static boolean isDockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isLocalPostgresRunning() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 5432), 300);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> RESOLVED_DB_URL);
        registry.add("spring.datasource.username", () -> RESOLVED_DB_USERNAME);
        registry.add("spring.datasource.password", () -> RESOLVED_DB_PASSWORD);
    }
}
