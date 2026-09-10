package com.application.ryft;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base for integration tests that need a real Postgres (per this project's coding standard:
 * no H2). {@code @ServiceConnection} points the app's datasource at the container automatically, so
 * subclasses need nothing beyond {@code extends AbstractIntegrationTest}. Only *IT-suffixed classes
 * run under Failsafe during {@code mvn verify}; plain unit tests never touch Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
}
