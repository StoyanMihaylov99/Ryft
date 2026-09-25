package com.application.ryft.seed;

import com.application.ryft.identity.user.service.UserService;
import com.application.ryft.identity.workspace.service.WorkspaceService;
import com.application.ryft.seed.data.DemoDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Populates a fresh database with a realistic multi-project demo (see {@code seed.data.DemoDataSet}) —
 * Phase 7's "seed script" roadmap item. Off by default and gated by {@code app.seed.enabled}
 * ({@code SEED_DEMO_DATA} env var), a plain property rather than a dedicated Spring profile: this is a
 * single boolean toggle with no other configuration that should change alongside it (unlike, say,
 * {@code oauth2} client registration, which really does need a distinct profile's worth of config), and
 * every other opt-in behavior in this codebase (OAuth2 client credentials, cookie security, CORS
 * origins) is already a plain {@code app.*} property with an env-var-backed default — a profile would be
 * the odd one out here, and would additionally require every deployment (including the one this seeder
 * is ultimately for — the live demo's guest login) to manage an active-profiles list instead of setting
 * one environment variable. Disabled by default in {@code application.yaml}, so it never runs in tests
 * or a normal local/production boot; {@code seed.DemoDataSeederIT} is the one test that turns it on. Only
 * this class — the actual auto-triggering entry point — carries the {@code @ConditionalOnProperty} gate;
 * {@link DemoDataInstaller} and {@code DemoProjectInstaller} underneath are ordinary, always-registered
 * beans (dormant unless something calls them), which is what makes {@code seed.DemoDataInstallerFailureIT}
 * able to exercise {@link DemoDataInstaller#install()} directly, in isolation, without needing this
 * runner or the property at all.
 *
 * <p><b>The real idempotency guarantee:</b> the demo account's email ({@code demo@ryft.dev}) is checked
 * before anything else runs, and every other seeding step is skipped if it's already registered.
 * {@link DemoDataInstaller#install()} is one atomic {@code @Transactional} unit, so that check is exact,
 * not approximate: the demo user row commits if and only if the *entire* seed — every user, the
 * workspace, and all three projects — committed with it. A failure at any point (this seeder deliberately
 * does not catch one; see below) rolls the whole attempt back, so a crashed or interrupted run never
 * leaves a half-seeded database for a later restart to mistake for a completed one, and the next restart
 * retries the full install from scratch rather than silently no-op-ing on partial data.
 *
 * <p><b>Only ever seeds an empty database.</b> v1 has exactly one workspace, and the seed has to create
 * it (the demo user becomes its Owner). If a workspace already exists without the demo user — e.g. a
 * developer's local database with their own account — the seeder logs a warning and skips rather than
 * crashing startup or mixing fake users/projects into someone's real workspace.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserService userService;
    private final WorkspaceService workspaceService;
    private final DemoDataInstaller installer;

    public DemoDataSeeder(UserService userService, WorkspaceService workspaceService, DemoDataInstaller installer) {
        this.userService = userService;
        this.workspaceService = workspaceService;
        this.installer = installer;
    }

    /**
     * Deliberately lets a seeding failure propagate uncaught: this only ever runs against a demo/staging
     * deployment (see the README's "never enable against a real production environment" note next to the
     * demo credentials), so failing application startup loudly — rather than logging and continuing with
     * a half-seeded database — is the correct trade-off here.
     */
    @Override
    public void run(ApplicationArguments args) {
        if (userService.findByEmail(DemoDataSet.DEMO_EMAIL).isPresent()) {
            log.info("Demo data already present ({} is registered) - skipping seeding.", DemoDataSet.DEMO_EMAIL);
            return;
        }
        if (workspaceService.getCurrentWorkspaceId().isPresent()) {
            log.warn("Skipping demo data seeding: this database already has a workspace set up, and the demo "
                    + "seed only runs against an empty database. Start from empty volumes "
                    + "(docker compose down -v) to seed.");
            return;
        }
        log.warn("Seeding demo data - this should never run against a real production database.");
        installer.install();
        log.warn("Demo data seeded: workspace 'Ryft Demo', {} users, {} projects.", DemoDataSet.users().size(),
                DemoDataSet.projects().size());
    }
}
