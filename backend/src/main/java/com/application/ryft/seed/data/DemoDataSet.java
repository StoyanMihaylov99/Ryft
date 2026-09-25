package com.application.ryft.seed.data;

import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.projects.entity.ProjectRole;
import com.application.ryft.workflow.entity.StatusCategory;
import java.util.List;

/**
 * The actual demo data, as a static, declarative structure rather than a long sequence of imperative
 * service calls — {@code DemoDataInstaller}/{@code DemoProjectInstaller} are the only code that turns
 * this into real rows, entirely through each module's public service interface. Grouped into one
 * workspace, five users, and three differently-themed projects (a mature web product, a newer analytics
 * platform, and an early-stage mobile rewrite) so the mix of sprint history, roles, and backlog depth
 * looks like three real teams at different points in their lifecycle, not one template repeated three
 * times.
 */
public final class DemoDataSet {

    public static final String DEMO_EMAIL = "demo@ryft.dev";
    public static final String ALICE_EMAIL = "alice@ryft.dev";
    public static final String BOB_EMAIL = "bob@ryft.dev";
    public static final String CARLA_EMAIL = "carla@ryft.dev";
    public static final String DIEGO_EMAIL = "diego@ryft.dev";

    private DemoDataSet() {
    }

    public static List<SeedUser> users() {
        return List.of(
                new SeedUser(DEMO_EMAIL, "Demo User"),
                new SeedUser(ALICE_EMAIL, "Alice Nakamura"),
                new SeedUser(BOB_EMAIL, "Bob Alvarez"),
                new SeedUser(CARLA_EMAIL, "Carla Jensen"),
                new SeedUser(DIEGO_EMAIL, "Diego Fischer")
        );
    }

    public static List<SeedProject> projects() {
        return List.of(phoenix(), nova(), atlas());
    }

    /** Phoenix: the flagship, most mature project — full sprint history, deepest backlog. */
    private static SeedProject phoenix() {
        SeedSprint sprint1 = new SeedSprint("Sprint 1", "Dashboard redesign kickoff", -42, -28,
                SeedSprintOutcome.COMPLETED);
        // Most recently completed sprint: endOffsetDays pinned to 0 (today) — see SeedSprint's javadoc.
        SeedSprint sprint2 = new SeedSprint("Sprint 2", "Dashboard polish & billing bugfixes", -14, 0,
                SeedSprintOutcome.COMPLETED);
        SeedSprint sprint3 = new SeedSprint("Sprint 3", "Personalization & analytics", 0, 14,
                SeedSprintOutcome.ACTIVE);

        List<SeedIssue> issues = List.of(
                SeedIssue.epic("Customer Portal Redesign")
                        .describedAs("Refresh the customer-facing account dashboard end to end.")
                        .priority(IssuePriority.MEDIUM)
                        .assignedTo(ALICE_EMAIL)
                        .movedTo(StatusCategory.IN_PROGRESS),
                SeedIssue.story("Design new account dashboard layout")
                        .describedAs("New mobile-first dashboard layout mockups for the customer account area.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(5)
                        .assignedTo(ALICE_EMAIL)
                        .reportedBy(ALICE_EMAIL)
                        .labeled("ux")
                        .inComponents("Frontend")
                        .childOf("Customer Portal Redesign")
                        .movedTo(StatusCategory.DONE)
                        .inSprint("Sprint 1"),
                SeedIssue.subtask("Create Figma mockups")
                        .priority(IssuePriority.MEDIUM)
                        .assignedTo(ALICE_EMAIL)
                        .reportedBy(ALICE_EMAIL)
                        .childOf("Design new account dashboard layout")
                        .movedTo(StatusCategory.DONE),
                SeedIssue.subtask("Get design review sign-off")
                        .priority(IssuePriority.MEDIUM)
                        .assignedTo(BOB_EMAIL)
                        .reportedBy(ALICE_EMAIL)
                        .childOf("Design new account dashboard layout")
                        .movedTo(StatusCategory.DONE),
                SeedIssue.story("Implement responsive dashboard grid")
                        .describedAs("Build the CSS grid and breakpoints for the new dashboard layout.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(8)
                        .assignedTo(BOB_EMAIL)
                        .reportedBy(BOB_EMAIL)
                        .inComponents("Frontend")
                        .childOf("Customer Portal Redesign")
                        .movedTo(StatusCategory.DONE)
                        .inSprint("Sprint 1"),
                SeedIssue.task("Migrate dashboard API to v2 schema")
                        .describedAs("Point the dashboard widgets at the new /api/v2/dashboard endpoints.")
                        .priority(IssuePriority.HIGH)
                        .storyPoints(5)
                        .assignedTo(DEMO_EMAIL)
                        .reportedBy(DEMO_EMAIL)
                        .inComponents("Backend API")
                        .childOf("Customer Portal Redesign")
                        .movedTo(StatusCategory.IN_PROGRESS)
                        .inSprint("Sprint 1")
                        .commented(new SeedComment(ALICE_EMAIL, "Blocked on the v2 endpoint deploy - any ETA @demo@ryft.dev?")),
                SeedIssue.bug("Dashboard widgets overlap on tablet width")
                        .describedAs("Widgets collide at 768-1024px viewport widths.")
                        .priority(IssuePriority.HIGH)
                        .storyPoints(3)
                        .assignedTo(ALICE_EMAIL)
                        .reportedBy(BOB_EMAIL)
                        .labeled("bug", "customer-reported")
                        .inComponents("Frontend")
                        .childOf("Customer Portal Redesign")
                        .movedTo(StatusCategory.DONE)
                        .inSprint("Sprint 2")
                        .commented(
                                new SeedComment(BOB_EMAIL, "Reported by three customers this week, bumping priority."),
                                new SeedComment(ALICE_EMAIL, "Fixed - was a flex-basis rounding issue.")),
                SeedIssue.story("Add dark mode toggle")
                        .describedAs("Persist the user's dark mode preference in account settings.")
                        .priority(IssuePriority.LOW)
                        .storyPoints(3)
                        .assignedTo(BOB_EMAIL)
                        .reportedBy(BOB_EMAIL)
                        .childOf("Customer Portal Redesign")
                        .movedTo(StatusCategory.DONE)
                        .inSprint("Sprint 2"),
                // Left unfinished (default TODO) on purpose: demonstrates SprintService#complete sweeping
                // an unfinished issue back to the backlog when Sprint 2 closes.
                SeedIssue.task("Write e2e tests for dashboard")
                        .describedAs("Playwright coverage for the redesigned dashboard.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(5)
                        .assignedTo(BOB_EMAIL)
                        .reportedBy(DEMO_EMAIL)
                        .childOf("Customer Portal Redesign")
                        .inSprint("Sprint 2"),
                SeedIssue.story("Add saved-view presets to dashboard")
                        .describedAs("Let users save and switch between dashboard filter presets.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(5)
                        .assignedTo(ALICE_EMAIL)
                        .reportedBy(ALICE_EMAIL)
                        .childOf("Customer Portal Redesign")
                        .movedTo(StatusCategory.IN_PROGRESS)
                        .inSprint("Sprint 3"),
                SeedIssue.task("Instrument dashboard analytics events")
                        .describedAs("Emit usage events for the new dashboard widgets.")
                        .priority(IssuePriority.LOW)
                        .storyPoints(3)
                        .assignedTo(DEMO_EMAIL)
                        .reportedBy(DEMO_EMAIL)
                        .inComponents("Backend API")
                        .childOf("Customer Portal Redesign")
                        .inSprint("Sprint 3"),
                SeedIssue.bug("Chart legend truncates long labels")
                        .describedAs("Legend entries longer than 20 characters get clipped without an ellipsis.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(2)
                        .assignedTo(BOB_EMAIL)
                        .reportedBy(ALICE_EMAIL)
                        .inComponents("Frontend")
                        .childOf("Customer Portal Redesign")
                        .movedTo(StatusCategory.DONE)
                        .inSprint("Sprint 3")
                        .commented(new SeedComment(ALICE_EMAIL, "cc @bob@ryft.dev can you take this one, it's quick?")),
                SeedIssue.epic("Billing & Subscriptions")
                        .describedAs("Give customers more billing flexibility and fix outstanding invoice bugs.")
                        .priority(IssuePriority.MEDIUM)
                        .assignedTo(ALICE_EMAIL)
                        .reportedBy(DEMO_EMAIL),
                SeedIssue.story("Add annual billing plan option")
                        .describedAs("Let customers switch from monthly to annual billing with a discount.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(8)
                        .assignedTo(ALICE_EMAIL)
                        .reportedBy(DEMO_EMAIL)
                        .inComponents("Billing")
                        .childOf("Billing & Subscriptions"),
                SeedIssue.task("Integrate Stripe webhook retries")
                        .describedAs("Add exponential backoff for failed Stripe webhook deliveries.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(5)
                        .assignedTo(DEMO_EMAIL)
                        .reportedBy(DEMO_EMAIL)
                        .inComponents("Billing")
                        .childOf("Billing & Subscriptions"),
                SeedIssue.bug("Invoice PDF shows wrong tax total")
                        .describedAs("The tax line sums the pre-discount amount instead of the post-discount amount.")
                        .priority(IssuePriority.HIGHEST)
                        .storyPoints(3)
                        .assignedTo(ALICE_EMAIL)
                        .reportedBy(BOB_EMAIL)
                        .labeled("bug", "customer-reported")
                        .inComponents("Billing")
                        .childOf("Billing & Subscriptions")
                        .movedTo(StatusCategory.BLOCKED)
                        .commented(new SeedComment(BOB_EMAIL, "Escalated by support, please prioritize.")),
                SeedIssue.task("Upgrade Angular to v19")
                        .describedAs("Framework upgrade, includes the new control-flow syntax migration.")
                        .priority(IssuePriority.LOW)
                        .storyPoints(8)
                        .assignedTo(BOB_EMAIL)
                        .reportedBy(DEMO_EMAIL)
                        .labeled("tech-debt"),
                SeedIssue.story("Add CSV export for reports")
                        .describedAs("Allow exporting any report table view as CSV.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(5)
                        .assignedTo(ALICE_EMAIL)
                        .reportedBy(DEMO_EMAIL)
        );

        List<SeedSavedFilter> savedFilters = List.of(
                SeedSavedFilter.named("My Issues").onlyMine(),
                SeedSavedFilter.named("All Bugs").shared().ofTypes(IssueType.BUG)
        );

        return new SeedProject("PHX", "Phoenix", "Customer-facing web portal.",
                List.of(new SeedMembership(ALICE_EMAIL, ProjectRole.ADMIN), new SeedMembership(BOB_EMAIL, ProjectRole.MEMBER),
                        new SeedMembership(CARLA_EMAIL, ProjectRole.VIEWER)),
                List.of(new SeedLabel("bug", "#E5484D"), new SeedLabel("tech-debt", "#F2994A"),
                        new SeedLabel("customer-reported", "#2F80ED"), new SeedLabel("ux", "#9B51E0")),
                List.of(new SeedComponent("Frontend"), new SeedComponent("Backend API"), new SeedComponent("Billing")),
                List.of(sprint1, sprint2, sprint3), issues, savedFilters);
    }

    /** Nova: a newer internal analytics platform — one sprint of history, one active. */
    private static SeedProject nova() {
        // Nova's only completed sprint is also its most recent, so it too gets endOffsetDays pinned to 0.
        SeedSprint sprint1 = new SeedSprint("Sprint 1", "Report builder MVP", -14, 0, SeedSprintOutcome.COMPLETED);
        SeedSprint sprint2 = new SeedSprint("Sprint 2", "Scheduled reports & performance", 0, 14,
                SeedSprintOutcome.ACTIVE);

        List<SeedIssue> issues = List.of(
                SeedIssue.epic("Self-serve Report Builder")
                        .describedAs("Let analysts build and run their own reports without an engineer.")
                        .priority(IssuePriority.MEDIUM)
                        .assignedTo(BOB_EMAIL)
                        .reportedBy(DEMO_EMAIL)
                        .movedTo(StatusCategory.IN_PROGRESS),
                SeedIssue.story("Design report builder query UI")
                        .describedAs("Drag-and-drop query builder for non-technical users.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(5)
                        .assignedTo(BOB_EMAIL)
                        .reportedBy(BOB_EMAIL)
                        .childOf("Self-serve Report Builder")
                        .movedTo(StatusCategory.DONE)
                        .inSprint("Sprint 1"),
                SeedIssue.task("Implement query execution engine")
                        .describedAs("Translate builder UI state into parameterized SQL.")
                        .priority(IssuePriority.HIGH)
                        .storyPoints(8)
                        .assignedTo(DIEGO_EMAIL)
                        .reportedBy(DIEGO_EMAIL)
                        .inComponents("Query Engine")
                        .childOf("Self-serve Report Builder")
                        .movedTo(StatusCategory.DONE)
                        .inSprint("Sprint 1")
                        .commented(new SeedComment(DIEGO_EMAIL, "Went with a query-plan cache, cut p95 latency by 40%.")),
                // Left unfinished on purpose: demonstrates the sweep-to-backlog on Sprint 1's completion.
                SeedIssue.bug("Report export times out for >10k rows")
                        .describedAs("CSV export exceeds the 30s request timeout on large reports.")
                        .priority(IssuePriority.HIGH)
                        .storyPoints(5)
                        .assignedTo(CARLA_EMAIL)
                        .reportedBy(BOB_EMAIL)
                        .labeled("bug", "performance")
                        .inComponents("Query Engine")
                        .childOf("Self-serve Report Builder")
                        .movedTo(StatusCategory.IN_PROGRESS)
                        .inSprint("Sprint 1"),
                SeedIssue.story("Add scheduled report emails")
                        .describedAs("Let users schedule a report to be emailed daily or weekly.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(5)
                        .assignedTo(BOB_EMAIL)
                        .reportedBy(DEMO_EMAIL)
                        .childOf("Self-serve Report Builder")
                        .movedTo(StatusCategory.IN_PROGRESS)
                        .inSprint("Sprint 2"),
                SeedIssue.task("Add caching layer for repeated queries")
                        .describedAs("Cache identical query executions for five minutes.")
                        .priority(IssuePriority.LOW)
                        .storyPoints(3)
                        .assignedTo(DIEGO_EMAIL)
                        .reportedBy(DIEGO_EMAIL)
                        .labeled("performance")
                        .inComponents("Query Engine")
                        .childOf("Self-serve Report Builder")
                        .inSprint("Sprint 2"),
                SeedIssue.task("Set up data warehouse nightly sync")
                        .describedAs("Nightly ETL from Postgres into the analytics warehouse.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(8)
                        .assignedTo(DIEGO_EMAIL)
                        .reportedBy(DEMO_EMAIL)
                        .inComponents("Data Pipeline"),
                SeedIssue.bug("Dashboard tile colors inconsistent with brand palette")
                        .describedAs("A few tiles still use the old blue instead of the new brand indigo.")
                        .priority(IssuePriority.LOW)
                        .storyPoints(2)
                        .assignedTo(CARLA_EMAIL)
                        .reportedBy(CARLA_EMAIL)
                        .labeled("bug")
                        .inComponents("Dashboards"),
                SeedIssue.story("Add role-based dashboard sharing")
                        .describedAs("Share a dashboard with specific workspace roles, not just everyone.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(5)
                        .assignedTo(BOB_EMAIL)
                        .reportedBy(DEMO_EMAIL)
                        .inComponents("Dashboards")
        );

        return new SeedProject("NOVA", "Nova", "Internal analytics platform.",
                List.of(new SeedMembership(BOB_EMAIL, ProjectRole.ADMIN), new SeedMembership(DIEGO_EMAIL, ProjectRole.MEMBER),
                        new SeedMembership(CARLA_EMAIL, ProjectRole.MEMBER), new SeedMembership(ALICE_EMAIL, ProjectRole.VIEWER)),
                List.of(new SeedLabel("bug", "#E5484D"), new SeedLabel("performance", "#219653")),
                List.of(new SeedComponent("Query Engine"), new SeedComponent("Dashboards"),
                        new SeedComponent("Data Pipeline")),
                List.of(sprint1, sprint2), issues, List.of());
    }

    /** Atlas: an early-stage mobile rewrite — no sprint history yet, just the first active sprint. */
    private static SeedProject atlas() {
        SeedSprint sprint1 = new SeedSprint("Sprint 1", "Rewrite kickoff", -5, 9, SeedSprintOutcome.ACTIVE);

        List<SeedIssue> issues = List.of(
                SeedIssue.epic("Atlas Rewrite Kickoff")
                        .describedAs("Rebuild the mobile app on React Native, feature-parity first.")
                        .priority(IssuePriority.MEDIUM)
                        .assignedTo(CARLA_EMAIL)
                        .reportedBy(DEMO_EMAIL)
                        .movedTo(StatusCategory.IN_PROGRESS),
                SeedIssue.story("Set up React Native project scaffold")
                        .describedAs("Bootstrap the RN project with the shared design-system package.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(3)
                        .assignedTo(CARLA_EMAIL)
                        .reportedBy(CARLA_EMAIL)
                        .childOf("Atlas Rewrite Kickoff")
                        .movedTo(StatusCategory.DONE)
                        .inSprint("Sprint 1"),
                SeedIssue.subtask("Configure ESLint & Prettier")
                        .priority(IssuePriority.LOW)
                        .assignedTo(CARLA_EMAIL)
                        .reportedBy(CARLA_EMAIL)
                        .childOf("Set up React Native project scaffold")
                        .movedTo(StatusCategory.DONE),
                SeedIssue.task("Wire up CI pipeline for mobile builds")
                        .describedAs("Fastlane + GitHub Actions for iOS/Android build artifacts.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(5)
                        .assignedTo(DIEGO_EMAIL)
                        .reportedBy(DIEGO_EMAIL)
                        .inComponents("CI/CD")
                        .childOf("Atlas Rewrite Kickoff")
                        .movedTo(StatusCategory.IN_PROGRESS)
                        .inSprint("Sprint 1"),
                SeedIssue.story("Implement login screen")
                        .describedAs("Email/password login screen matching the design system.")
                        .priority(IssuePriority.MEDIUM)
                        .storyPoints(5)
                        .assignedTo(DEMO_EMAIL)
                        .reportedBy(DEMO_EMAIL)
                        .childOf("Atlas Rewrite Kickoff")
                        .inSprint("Sprint 1"),
                SeedIssue.task("Evaluate Expo vs bare React Native")
                        .describedAs("Spike comparing build/eject tradeoffs for our native module needs.")
                        .priority(IssuePriority.LOW)
                        .storyPoints(3)
                        .assignedTo(DIEGO_EMAIL)
                        .reportedBy(DEMO_EMAIL),
                SeedIssue.bug("Splash screen flashes white on Android")
                        .describedAs("Brief white flash before the splash image renders on cold start.")
                        .priority(IssuePriority.LOW)
                        .storyPoints(2)
                        .assignedTo(CARLA_EMAIL)
                        .reportedBy(DIEGO_EMAIL)
                        .labeled("bug", "mobile")
        );

        return new SeedProject("ATLS", "Atlas", "Mobile app rewrite.",
                List.of(new SeedMembership(CARLA_EMAIL, ProjectRole.ADMIN), new SeedMembership(DIEGO_EMAIL, ProjectRole.MEMBER),
                        new SeedMembership(BOB_EMAIL, ProjectRole.VIEWER)),
                List.of(new SeedLabel("bug", "#E5484D"), new SeedLabel("mobile", "#56CCF2")),
                List.of(new SeedComponent("iOS"), new SeedComponent("Android"), new SeedComponent("CI/CD")),
                List.of(sprint1), issues, List.of());
    }
}
