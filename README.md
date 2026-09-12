# Ryft

A self-hosted, Jira-style project management and issue tracking tool:
projects, epics, issues, sprints, Kanban/Scrum boards, configurable
workflows, and role-based permissions.

The domain (hierarchical
entities, configurable workflows, permissions, real-time collaboration)
forces genuinely hard design decisions that a typical CRUD app doesn't:
state machines, authorization models, eventual consistency between the
write path and the activity feed/notifications, and a UI that has to feel
responsive with live multi-user updates.

Modules (Java packages, each with its own `controller` / `service` /
`repository` / `dto` sub-packages):

| Module | Responsibility |
|---|---|
| `identity` | Users, auth, JWT issuance, workspace membership |
| `projects` | Projects, project settings, project members & roles |
| `issues` | Issue CRUD, hierarchy (epic → story/task/bug → subtask), labels, components, comments, attachments |
| `workflow` | Workflow schemes, statuses, transitions, validation of status changes |
| `sprints` | Backlog, sprints, sprint planning, burndown data |
| `notifications` | In-app notifications, mention detection, delivery |
| `activity` | Activity log / audit trail |
| `search` | Filters, saved filters, board query building |
| `admin` | Workspace-level settings, role management |

**v1 scope note:** v1 deliberately has no message broker or cache - Kafka
and Redis are a planned future addition once a concrete scaling need
(multi-instance deployment, hot-path decoupling) is proven, not a day-one
requirement. At this project's actual scale, direct synchronous service
calls and a single-instance WebSocket broadcast do the same job with far
less operational surface.

## Architecture

1. Client (Angular) calls the REST API → `controller` → `service`
   (validates, applies business rules - workflow transition legality,
   permission checks) → `repository` (Postgres) → returns a DTO.
2. On a meaningful state change (issue created, status changed, comment
   added, sprint started), the `service` calls `ActivityService` and
   `NotificationService` directly (plain method calls, same JVM) right
   after its own transaction commits - no broker in between.
3. `ActivityService` writes an entry to MongoDB (flexible schema per event
   type) and pushes to the affected project's WebSocket topic for live
   board updates. `NotificationService` determines who should be notified
   (assignee, watchers, mentioned users), persists an in-app notification,
   and pushes it over WebSocket to that user's personal topic.
4. The single app instance holds WebSocket sessions directly and
   broadcasts to connected clients itself - no pub/sub relay needed until
   there's more than one instance. Reads go straight to Postgres; no
   caching layer for v1.

This keeps the write path (the REST call) fast and synchronous, and since
the downstream calls happen in-process, activity logging and
notifications are effectively immediate for v1.

### Why each piece of the stack is there

- **PostgreSQL** - system of record. Relational integrity matters here
  (issues belong to projects, sprints belong to projects, workflow
  transitions must be valid) - not a document-shaped domain.
- **MongoDB** - activity log only. Event payloads vary by event type, so
  forcing them into a rigid relational schema would mean a wide,
  mostly-null table. Scoped narrowly on purpose.
- **WebSockets (STOMP)** - boards update live when a teammate moves a card;
  polling would visibly worsen the UX for the feature meant to be the
  demo's best moment. A single instance broadcasts directly to its own
  connected clients, no relay needed yet.

### Future scaling: Kafka & Redis

Left out of v1 deliberately, not by oversight:

- **Kafka** would decouple the write path from "who needs to know about
  this" - new consumers (e.g. a future webhook module) would subscribe to
  a topic instead of requiring an edit to the triggering service. Worth
  adding once enough independent consumers make direct calls unwieldy.
- **Redis** would back a WebSocket pub/sub relay once running more than
  one app instance, and cache hot reads once Postgres load justifies it.

Introducing either is meant to be a recorded, deliberate decision, not a
default.

## Security

Two-token model: a short-lived JWT **access token** proves identity on
every API call, and a long-lived, server-tracked **refresh token** is the
only thing that can mint a new access token. They're issued together by
`identity.auth.service.AuthService` and `identity.auth.service.TokenService`, on
register, login, refresh, and OAuth2 login alike.

### Password storage

Passwords are hashed with `BCryptPasswordEncoder` (`PasswordEncoderConfig`)
— a salted, adaptive one-way hash, not reversible encoding. Every
`encode()` call produces a different output for the same input (random
salt baked into the result), and there is no `decode()`; verification
(`matches()`) re-derives the hash from the salt stored in the hash itself.
OAuth-only accounts have no password hash at all (`null`), and login for
those accounts is rejected before `passwordEncoder.matches()` is even
called.

Registration/login also normalize email to `trim().toLowerCase()` before
any lookup or uniqueness check, so `User@Example.com` and
`user@example.com` can't become two accounts. Login returns the exact
same exception/message for "unknown email" and "wrong password" — a
distinguishable response would make the endpoint a user-enumeration
oracle.

### Access tokens (JWT)

- Signed RS256, via an RSA-2048 keypair generated fresh **at every
  application startup** (`JwtConfig`) — fine for a single-instance
  deployment. A restart invalidates outstanding access tokens only (the
  client's interceptor transparently refreshes); refresh tokens stay
  valid since they're persisted in Postgres, not derived from this key.
  Running more than one instance would need this key externalized/shared
  (or a JWKS endpoint), or instance B couldn't verify tokens instance A
  signed.
- Claims: `iss=ryft`, `iat`, `exp` (now + `app.jwt.access-token-ttl`,
  default **15 minutes**), `sub` = user id, plus an `email` claim.
- Verified statelessly on every request by Spring's
  `oauth2ResourceServer().jwt()` against the matching public key — no DB
  hit per request.
- Never stored anywhere persistent: returned once in the JSON response
  body and kept in memory only on the client (see
  [Client-side handling](#client-side-handling)).

### Refresh tokens & rotation

Unlike the access token, the refresh token is **not** a JWT — it's 32
random bytes (`SecureRandom`), base64url-encoded, and only its SHA-256
hash is persisted (`RefreshToken.tokenHash`, unique). A stolen database
dump doesn't hand out usable tokens. Default lifetime is 30 days
(`app.refresh-token.ttl`).

Every refresh token belongs to a **rotation family** — a `familyId` (UUID)
shared by every token descended from one login. The flow, in
`TokenService`:

1. **Issuance** (login/register/OAuth): a brand-new `familyId` is
   generated — this starts the family.
2. **Rotation** (`POST /auth/refresh`): the presented token is revoked and
   a new row is created *carrying the same `familyId`*, so the chain
   token1 → token2 → token3 → ... all trace back to one login. Each
   refresh token is single-use.
3. **Reuse detection**: if an already-revoked token is presented again,
   that's either (a) a harmless race — two tabs refreshing at nearly the
   same instant — or (b) a stolen token being replayed after the
   legitimate rotation already happened.
   - Within a short grace period (`app.refresh-token.reuse-grace-period`,
     default **10s**) and a live token still exists in the family: treated
     as the benign race, and rotation proceeds from that live token — no
     one gets logged out.
   - Otherwise: the **entire family** is revoked
     (`RefreshTokenRepository.revokeAllByFamilyId`) and
     `RefreshTokenReuseDetectedException` is thrown (→ `401`), forcing the
     legitimate user to log in again too. Attacker and victim can't be
     told apart at that point, so the whole lineage is killed to be safe.
4. **Logout** revokes only the *single* presented token — other
   devices/tabs (their own families) stay logged in.

### Cookies

The refresh token is delivered exclusively via an `httpOnly`,
`SameSite=Lax` cookie scoped to `/api/v1/auth` (`RefreshTokenCookieSupport`),
`Secure` in any real deployment (`COOKIE_SECURE`, defaults to `false` only
for local HTTP dev). It's never exposed to JavaScript, so an XSS payload
can't read it directly — though `SameSite=Lax` and the narrow cookie path
are what actually stop it from being *used* cross-site, since the cookie
still auto-attaches to same-site requests. The access token is **never**
put in a cookie at all, precisely so it isn't sent automatically alongside
unrelated requests.

Clearing the cookie on logout deliberately reuses the exact same
path/`SameSite` attributes it was set with — mismatched attributes make
the browser treat it as a different cookie and silently keep the original
alive. Cookie writes happen in the web layer (`AuthController` and
`OAuth2LoginSuccessHandler`), right after the service call returns.

### CSRF & CORS

- CSRF protection is disabled (`csrf().disable()`) — this is a bearer-JWT
  API, not cookie-session auth, for every endpoint except the four auth
  endpoints, which *do* rely on a cookie. Those are covered instead by
  `SameSite=Lax`: the browser won't attach the cookie to a cross-site
  `POST`, which is what CSRF would require.
- Session creation policy is `IF_REQUIRED`, not `STATELESS`: Spring's
  OAuth2 login needs an `HttpSession` to bridge the
  redirect → provider → callback hops. Plain JWT bearer calls never
  create one.
- CORS is an explicit origin allow-list (`app.cors.allowed-origins`,
  defaults to `http://localhost:4200`), `allowCredentials(true)` (required
  for the browser to send the refresh cookie cross-origin to the API),
  restricted to the methods/headers actually used
  (`common.config.CorsConfig`).

### OAuth2 login (Google / GitHub)

Standard Spring Security `oauth2Login` flow:
`GET /oauth2/authorization/{google|github}` → redirect to the provider →
callback at `/login/oauth2/code/{registrationId}` →
`OAuth2LoginSuccessHandler` (or `OAuth2LoginFailureHandler` if the
provider-side auth itself failed).

The two providers report profile data in incompatible shapes, normalized
by `OAuthUserInfoExtractor` into one `OAuthUserInfo`:

- **Google** — standard OIDC claims (`sub`, `email`, `email_verified`,
  `name`, `picture`).
- **GitHub** — no OIDC `id_token`, and the `/user` endpoint's `email` is
  `null` whenever the user has "keep my email private" on, even with the
  `user:email` scope granted. `GitHubOAuth2UserService` makes an extra
  authenticated call to `GET /user/emails` to find the verified primary
  address and merges it in.

Account resolution (`AuthService.loginOrRegisterOAuthUser`):

- An `OAuthIdentity` (provider + provider user id) already links to a
  user → log that user in, refresh `displayName`/`avatarUrl` from the
  provider's current values.
- No existing identity, but an account already exists with that email:
  - Provider reports the email **verified** → link the new identity onto
    that existing account.
  - Provider reports it **unverified** → reject
    (`OAuthEmailConflictException`). Silently linking here would let an
    attacker register an OAuth app claiming a victim's (unverified) email
    and take over their password account.
- No existing identity or account → create a brand-new `User` (no
  password hash — OAuth-only) plus the `OAuthIdentity` link.
- Provider returns no usable email at all → `OAuthMissingEmailException`.

On success, tokens are issued and the cookie set exactly as for
password login, then the browser is redirected to
`app.oauth2.success-redirect-uri` — **no tokens in that URL**; the SPA
immediately calls `/auth/refresh` using the cookie just set to mint an
access token. On any failure, the redirect to
`app.oauth2.failure-redirect-uri` carries only an opaque `?error=` code,
never token values or exception detail.

### Authorization rules

`SecurityConfig` requires a valid bearer JWT on every request
(`anyRequest().authenticated()`) except an explicit allow-list:
`POST /auth/register`, `/login`, `/refresh`, `/logout`, and the OAuth2
redirect/callback paths (`/oauth2/**`, `/login/oauth2/code/**`) Spring
Security registers itself. See
[Per-project roles & permissions](#per-project-roles--permissions) below
for how per-project role checks layer on top of this.

### Client-side handling

- The access token lives **only** in an in-memory Angular signal
  (`AuthService`) — never `localStorage`/`sessionStorage` — so it isn't
  readable by an XSS payload trawling browser storage. The trade-off: a
  hard page reload wipes it.
- `authInterceptor` attaches `Authorization: Bearer <token>` to outgoing
  requests (skipping the auth endpoints themselves, to avoid recursion),
  and on a `401` attempts exactly one silent refresh-and-retry before
  routing to `/login`. Concurrent 401s in the same tab share one in-flight
  refresh call instead of each triggering their own.
- `authGuard` runs the same silent-refresh check on navigation after a
  reload, since "no token in memory" doesn't mean "not logged in" — the
  refresh cookie may still be valid.

### Per-project roles & permissions

- Roles are **per-project**, not global: Owner, Admin, Member, Viewer. A
  user can be Admin on one project and Viewer on another. A separate
  workspace-level `admin` module handles workspace-wide settings only.
- Permission checks live in each module's `service` layer (not just
  `@PreAuthorize` annotations) so business rules and permission rules stay
  together and are unit-testable.

## Tech stack

**Backend:** Java 21, Spring Boot, Spring Web (REST), Spring Security +
JWT, Spring Data JPA / Hibernate, PostgreSQL, MongoDB, WebSocket (STOMP
over SockJS), Maven, JUnit 5 + Mockito + Testcontainers, Docker / Docker
Compose, GitHub Actions CI/CD, SLF4J + Logback, Spring Boot Actuator,
Micrometer + Prometheus + Grafana, OpenAPI/Swagger. (Redis and Kafka are a
deliberate future addition - see [Future scaling](#future-scaling-kafka--redis).)

**Frontend:** Angular + TypeScript, Angular CDK (drag-and-drop boards),
Angular signals for state.

**Deploy target:** containerized (Docker) on AWS ECS Fargate or Azure App
Service (decision to be finalized before Phase 7) - RDS/Azure Database for
Postgres and MongoDB Atlas. (ElastiCache/Azure Cache and a managed Kafka
service would be added alongside Redis/Kafka, if/when introduced.)

## Getting started

Prerequisites: Java 21, Node.js 22+, Docker.

Start Postgres and MongoDB:

```bash
docker-compose up -d
```

`docker-compose.yml` currently publishes Postgres on host port **5433**
(not the default 5432) — a local Postgres install already sitting on 5432
otherwise silently intercepts the connection, which surfaces as a
confusing `Unable to determine Dialect without JDBC metadata` error from
Hibernate rather than a connection-refused. Point the backend at the
matching port:

```bash
cd backend
DB_PORT=5433 ./mvnw spring-boot:run
```

(Omit `DB_PORT` — or set it back to 5432 in both `docker-compose.yml` and
here — once nothing else on your machine holds port 5432.)

Run the frontend:

```bash
cd frontend
npm install
npm start
```

Run all tests:

```bash
cd backend && ./mvnw test
cd frontend && npm test
```

## License

See [LICENSE](LICENSE).
