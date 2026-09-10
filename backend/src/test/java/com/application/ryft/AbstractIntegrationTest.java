package com.application.ryft;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for integration tests that need a real Postgres (per this project's coding standard:
 * no H2). {@code @ServiceConnection} points the app's datasource at the container automatically, so
 * subclasses need nothing beyond {@code extends AbstractIntegrationTest}. Only *IT-suffixed classes
 * run under Failsafe during {@code mvn verify}; plain unit tests never touch Docker.
 *
 * <p>Deliberately a "singleton container" started once, manually, for the whole test JVM, and never
 * stopped — Testcontainers' Ryuk reaper kills it when the JVM exits. A per-test-class container (the
 * {@code @Testcontainers}/{@code @Container} pattern) is unsafe here: Spring caches the
 * {@code ApplicationContext} across test classes with equivalent configuration, so a later class can
 * end up reusing an earlier class's cached {@code DataSource} — which still points at that earlier
 * class's container, already stopped by the time the later class runs. That produced exactly this
 * failure in CI: a freshly-started container's port was logged, but the actual connection attempt hit
 * a *different*, already-dead container's port from a previous test class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }
}
