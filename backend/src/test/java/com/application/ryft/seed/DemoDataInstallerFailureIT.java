package com.application.ryft.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.application.ryft.identity.user.service.UserService;
import com.application.ryft.identity.workspace.service.WorkspaceService;
import com.application.ryft.projects.dto.CreateProjectRequest;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.repository.ProjectMemberRepository;
import com.application.ryft.projects.repository.ProjectRepository;
import com.application.ryft.projects.service.ProjectService;
import com.application.ryft.projects.service.ProjectServiceImpl;
import com.application.ryft.seed.data.DemoDataSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@link DemoDataInstaller#install()}'s atomicity (see its class javadoc, and the HIGH-severity
 * crash-safety fix it documents): a failure partway through must roll back the *entire* attempt, not just
 * the steps after some already-committed prefix, so a later restart retries the full install rather than
 * mistaking a half-seeded database for a completed one.
 *
 * <p>Deliberately does not set {@code app.seed.enabled=true} and does not touch {@code DemoDataSeeder} at
 * all — {@link DemoDataInstaller} is called directly, in isolation (see its javadoc for why it's an
 * ordinary, always-registered bean rather than one gated by that property). This sidesteps a real problem
 * with testing this through the real {@code ApplicationRunner}: {@code DemoDataSeeder.run()} fires
 * automatically during context startup, before any {@code @Test} method gets a chance to arm a forced
 * failure — by the time a test method runs, an unstubbed automatic run would have already either
 * succeeded or fatally failed context startup itself. Arming the failure via a {@code @TestConfiguration}
 * bean definition (evaluated while the context builds, before any {@code ApplicationRunner} executes) and
 * then calling {@link DemoDataInstaller#install()} directly from inside the test method avoids that
 * entirely.
 *
 * <p>Uses its own dedicated, freshly-started containers rather than the shared
 * {@code AbstractIntegrationTest} singleton, for the same reason {@code DemoDataSeederIT} does: seeding
 * assumes it may be the first caller of {@code WorkspaceService.completeSetup}, which would throw
 * {@code WorkspaceAlreadySetUpException} if any other IT class in the shared container had already set
 * one up first.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(DemoDataInstallerFailureIT.FailureInjectionConfig.class)
class DemoDataInstallerFailureIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired
    private DemoDataInstaller installer;

    @Autowired
    private UserService userService;

    @Test
    void aFailurePartwayThroughRollsBackTheEntireSeed() {
        // PHX (the first project in DemoDataSet.projects()) is fully built - users, workspace, every
        // Phoenix issue/sprint/comment/saved filter - before NOVA's creation is reached and forced to
        // fail, so this genuinely exercises "roll back real partial progress", not just an immediate
        // first-call failure.
        assertThatThrownBy(installer::install).isInstanceOf(IllegalStateException.class);

        assertThat(userService.findByEmail(DemoDataSet.DEMO_EMAIL))
                .as("the demo user, registered first, must not survive a rolled-back install")
                .isEmpty();
    }

    @TestConfiguration
    static class FailureInjectionConfig {

        @Bean
        @Primary
        ProjectService failingProjectService(ProjectRepository projectRepository,
                ProjectMemberRepository projectMemberRepository, WorkspaceService workspaceService,
                UserService userService) {
            return new ProjectServiceImpl(projectRepository, projectMemberRepository, workspaceService, userService) {
                @Override
                @Transactional
                public ProjectResponse create(UUID callerId, CreateProjectRequest request) {
                    if ("NOVA".equals(request.key())) {
                        throw new IllegalStateException("Simulated failure seeding NOVA - forced by test");
                    }
                    return super.create(callerId, request);
                }
            };
        }
    }
}
