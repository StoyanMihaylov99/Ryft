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

### Auth & authorization

- JWT access token (short-lived) + refresh token (httpOnly cookie).
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

Run the backend:

```bash
cd backend
./mvnw spring-boot:run
```

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
