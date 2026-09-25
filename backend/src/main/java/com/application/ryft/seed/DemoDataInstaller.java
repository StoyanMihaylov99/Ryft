package com.application.ryft.seed;

import com.application.ryft.identity.auth.dto.RegisterRequest;
import com.application.ryft.identity.auth.service.AuthResult;
import com.application.ryft.identity.auth.service.AuthService;
import com.application.ryft.identity.workspace.dto.CreateWorkspaceRequest;
import com.application.ryft.identity.workspace.dto.InviteRequest;
import com.application.ryft.identity.workspace.entity.WorkspaceRole;
import com.application.ryft.identity.workspace.service.WorkspaceService;
import com.application.ryft.seed.config.DemoSeedProperties;
import com.application.ryft.seed.data.DemoDataSet;
import com.application.ryft.seed.data.SeedProject;
import com.application.ryft.seed.data.SeedUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the whole seed: registers the demo users, sets up the single v1 workspace under the demo
 * user (see {@code identity.workspace.service.WorkspaceService}'s "whichever caller gets there first
 * becomes Owner" contract), invites everyone else into it, then builds each {@link SeedProject} via
 * {@link DemoProjectInstaller}.
 *
 * <p>Deliberately an ordinary, always-registered {@code @Component} — not gated by
 * {@code @ConditionalOnProperty} the way {@link DemoDataSeeder} is. It does nothing unless
 * {@link #install()} is actually called, and the only production caller of that method is
 * {@code DemoDataSeeder.run()}, which *is* gated — so "never runs by default" still holds. Being
 * unconditional here is what lets {@code seed.DemoDataInstallerFailureIT} inject this bean directly and
 * call {@link #install()} in isolation, without needing {@code app.seed.enabled=true} (and therefore
 * without the real {@code DemoDataSeeder} ApplicationRunner auto-firing at context startup and racing the
 * test's own setup).
 *
 * <p><b>{@link #install()} is one atomic {@code @Transactional} unit</b> — every step below joins that
 * single transaction (default {@code REQUIRED} propagation; nothing in this call chain uses
 * {@code REQUIRES_NEW}), so a failure anywhere — a duplicate key, a validation error, anything —
 * rolls back every write this method made, not just the ones after some already-committed prefix. This
 * is what makes the demo-user-registered check in {@code DemoDataSeeder} an exact idempotency signal
 * rather than an approximate one: if the demo user's row exists, the *entire* install committed with it;
 * if {@link #install()} throws, the demo user row (registered first, before anything else) is rolled back
 * along with everything after it, so the next restart sees no demo user and retries the full install
 * rather than silently treating a half-seeded database as "already done." The one thing that does *not*
 * roll back is the Mongo-backed activity log / notifications a completed install triggers — but those are
 * published via {@code @TransactionalEventListener(phase = AFTER_COMMIT)} (see {@code common.event}), so
 * they only fire once this transaction actually commits; a rolled-back attempt never reaches them, so
 * there's no risk of orphaned Mongo documents referencing Postgres rows that were rolled back. Accepted
 * trade-off: that one transaction holds a pooled connection (and per-project issue-key row locks) for the
 * whole install, a few seconds, while the web server is already accepting requests — fine for a one-time,
 * from-empty demo boot, and worth it for the atomicity.
 *
 * <p>Every service below takes an explicit {@code callerId} parameter rather than reading
 * {@code SecurityContextHolder} — this seeder runs from an {@code ApplicationRunner}, with no
 * authenticated HTTP request underway, so that explicit-caller-id shape (already how every service in
 * this codebase is designed, for testability) is exactly what makes seeding possible without faking a
 * security context.
 */
@Component
@EnableConfigurationProperties(DemoSeedProperties.class)
class DemoDataInstaller {

    /**
     * Placeholder credential for every seeded user except the demo account — nobody is meant to log in
     * as Alice/Bob/Carla/Diego (they exist to populate assignees/reporters/comments realistically), so
     * unlike {@link DemoSeedProperties#demoPassword()} this has no reason to be configurable.
     */
    private static final String TEAMMATE_PASSWORD = "RyftTeammate!2026";

    private final AuthService authService;
    private final WorkspaceService workspaceService;
    private final DemoProjectInstaller projectInstaller;
    private final DemoSeedProperties properties;

    DemoDataInstaller(AuthService authService, WorkspaceService workspaceService,
            DemoProjectInstaller projectInstaller, DemoSeedProperties properties) {
        this.authService = authService;
        this.workspaceService = workspaceService;
        this.projectInstaller = projectInstaller;
        this.properties = properties;
    }

    @Transactional
    void install() {
        Map<String, UUID> userIdsByEmail = registerUsers(DemoDataSet.users());
        UUID demoUserId = userIdsByEmail.get(DemoDataSet.DEMO_EMAIL);

        setUpWorkspace(demoUserId, userIdsByEmail);

        for (SeedProject project : DemoDataSet.projects()) {
            projectInstaller.install(project, demoUserId, userIdsByEmail);
        }
    }

    private Map<String, UUID> registerUsers(List<SeedUser> users) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (SeedUser user : users) {
            String password = user.email().equals(DemoDataSet.DEMO_EMAIL) ? properties.demoPassword() : TEAMMATE_PASSWORD;
            AuthResult result = authService.register(new RegisterRequest(user.email(), password, user.displayName()));
            ids.put(user.email(), result.user().id());
        }
        return ids;
    }

    private void setUpWorkspace(UUID demoUserId, Map<String, UUID> userIdsByEmail) {
        workspaceService.completeSetup(demoUserId, new CreateWorkspaceRequest("Ryft Demo", "ryft-demo"));
        for (Map.Entry<String, UUID> entry : userIdsByEmail.entrySet()) {
            if (!entry.getKey().equals(DemoDataSet.DEMO_EMAIL)) {
                workspaceService.invite(demoUserId, new InviteRequest(entry.getKey(), WorkspaceRole.MEMBER));
            }
        }
    }
}
