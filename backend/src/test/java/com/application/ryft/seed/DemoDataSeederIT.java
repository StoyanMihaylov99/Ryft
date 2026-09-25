package com.application.ryft.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.application.ryft.identity.user.service.UserService;
import com.application.ryft.identity.workspace.service.WorkspaceService;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.service.ProjectService;
import com.application.ryft.search.dto.SavedFilterResponse;
import com.application.ryft.search.service.SavedFilterService;
import com.application.ryft.seed.data.DemoDataSet;
import com.application.ryft.sprints.dto.BurndownResponse;
import com.application.ryft.sprints.dto.SprintResponse;
import com.application.ryft.sprints.entity.SprintState;
import com.application.ryft.sprints.service.BurndownService;
import com.application.ryft.sprints.service.SprintService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Deliberately does *not* extend {@link com.application.ryft.AbstractIntegrationTest}: that base class
 * shares one Postgres container (and hence one cumulative database, including "the" v1 workspace row —
 * see {@code WorkspaceControllerIT}'s and {@code ProjectControllerIT}'s class javadocs) across every
 * other {@code *IT} class in the same Failsafe JVM. This test needs to see a genuinely empty database —
 * seeding assumes it may be the one calling {@code WorkspaceService.completeSetup} for the very first
 * time, which throws {@code WorkspaceAlreadySetUpException} if any earlier-running IT class already
 * created a workspace row. A per-class {@code @Testcontainers} pair, scoped to just this test, sidesteps
 * that entirely — and doesn't reintroduce the stale-cached-container risk
 * {@code AbstractIntegrationTest} documents, since {@code app.seed.enabled=true} makes this class's
 * Spring context configuration unique in the whole test suite, so its context (and the containers behind
 * it) is never a caching candidate for, or reused from, any other test class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.seed.enabled=true")
@Testcontainers
class DemoDataSeederIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Autowired
    private DemoDataSeeder demoDataSeeder;

    @Autowired
    private UserService userService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private IssueService issueService;

    @Autowired
    private SprintService sprintService;

    @Autowired
    private SavedFilterService savedFilterService;

    @Autowired
    private BurndownService burndownService;

    @Test
    void seedsRealisticDemoDataOnStartupAndIsANoOpOnASecondRun() {
        UUID demoUserId = userService.findByEmail(DemoDataSet.DEMO_EMAIL).orElseThrow().id();

        assertThat(workspaceService.getCurrentWorkspaceId()).isPresent();

        List<ProjectResponse> projects = projectService.listForCaller(demoUserId);
        assertThat(projects).extracting(ProjectResponse::key).containsExactlyInAnyOrder("PHX", "NOVA", "ATLS");

        List<IssueResponse> phoenixIssues = issueService.listForProject(demoUserId, "PHX");
        assertThat(phoenixIssues).hasSizeGreaterThanOrEqualTo(14);
        assertThat(phoenixIssues).anyMatch(issue -> issue.type() == IssueType.EPIC);

        IssueResponse story = phoenixIssues.stream()
                .filter(issue -> issue.title().equals("Design new account dashboard layout"))
                .findFirst()
                .orElseThrow();
        assertThat(issueService.listSubtasks(demoUserId, story.key())).isNotEmpty();

        List<SprintResponse> phoenixSprints = sprintService.listForProject(demoUserId, "PHX");
        assertThat(phoenixSprints).hasSize(3);
        assertThat(phoenixSprints.stream().filter(sprint -> sprint.state() == SprintState.COMPLETED).count())
                .isEqualTo(2);
        assertThat(phoenixSprints.stream().filter(sprint -> sprint.state() == SprintState.ACTIVE).count())
                .isEqualTo(1);

        List<SavedFilterResponse> savedFilters = savedFilterService.list(demoUserId, "PHX");
        assertThat(savedFilters).hasSize(2);
        assertThat(savedFilters).anyMatch(SavedFilterResponse::isShared);

        // The most recently completed sprint's endDate is pinned to "today" (see SeedSprint's javadoc)
        // specifically so its actual-burndown line shows a real drop, not a flat line at committedPoints
        // - this is the regression check for that fix.
        SprintResponse mostRecentCompletedSprint = phoenixSprints.stream()
                .filter(sprint -> sprint.name().equals("Sprint 2"))
                .findFirst()
                .orElseThrow();
        BurndownResponse burndown = burndownService.getBurndown(demoUserId, mostRecentCompletedSprint.id());
        assertThat(burndown.actualBurndown()).isNotEmpty();
        assertThat(burndown.actualBurndown().getLast().remainingPoints())
                .as("the most recently completed sprint's actual burndown must show a visible drop, not stay"
                        + " flat at committedPoints")
                .isLessThan(burndown.committedPoints());

        // Idempotency: re-running the ApplicationRunner (as would happen on every app restart with the
        // property still enabled) must not touch anything, since the demo user is already registered.
        demoDataSeeder.run(new DefaultApplicationArguments());

        assertThat(projectService.listForCaller(demoUserId)).hasSize(3);
        assertThat(issueService.listForProject(demoUserId, "PHX")).hasSize(phoenixIssues.size());
        assertThat(sprintService.listForProject(demoUserId, "PHX")).hasSize(3);
        assertThat(savedFilterService.list(demoUserId, "PHX")).hasSize(2);
    }
}
