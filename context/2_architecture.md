# 2. Architecture — Tech Stack & Systems

> **Read the Status column before writing code.** `PRESENT` = in the repo now.
> `PLANNED` = agreed target, not yet installed. Never write code that assumes a
> `PLANNED` dependency exists; add the dependency in its own step first.

## Architecture style

**Modular monolith.** One Spring Boot deployable that serves a compiled React SPA
from its own static resources. Internally organised *package-by-feature*. This is a
deliberate choice over microservices — see ADR-002 in `5_progress.md`.

```
Browser ──► Spring Boot (:8080) ──► PostgreSQL
  │           ├── /            React SPA (static, from src/main/resources/static)
  │           ├── /api/**      REST, Clerk JWT required
  │           └── /actuator/** health public, rest authenticated
  └───────► Clerk (accounts.dev)   sign-in UI, token issuance, JWKS
```

## Tech stack

### Backend

| Concern | Choice | Status |
|---|---|---|
| Language | Java **21** (LTS) | PRESENT — but see Version drift |
| Framework | Spring Boot **4.1.1** | PRESENT |
| Build | Maven via `./mvnw` | PRESENT |
| Web | `spring-boot-starter-webmvc` | PRESENT |
| Persistence | Spring Data JPA / Hibernate | PRESENT |
| Legacy persistence | `NamedParameterJdbcTemplate` + `util/SqlUtils` | PRESENT — **to be removed** |
| Migrations | **Flyway**, `ddl-auto: validate` | PLANNED — top priority |
| Database | PostgreSQL **16** | PRESENT (dev/prod profiles) |
| Dev/test database | H2 in-memory | PRESENT — **to be removed** |
| Auth | Clerk + `spring-boot-starter-oauth2-resource-server` | PRESENT |
| Validation | `spring-boot-starter-validation` (Jakarta) | PRESENT |
| Boilerplate | Lombok | PRESENT |
| Metrics | Actuator + `micrometer-registry-prometheus` | PRESENT |
| API docs | **springdoc-openapi** | PLANNED |

### Frontend

| Concern | Choice | Status |
|---|---|---|
| Framework | React **19** | PRESENT |
| Language | TypeScript **~6.0** | PRESENT |
| Bundler | Vite **8** | PRESENT |
| Styling | Tailwind CSS **4** via `@tailwindcss/vite` | PRESENT |
| Routing | `react-router-dom` **7** | PRESENT |
| Auth | `@clerk/clerk-react` **5** | PRESENT |
| Icons | `lucide-react` | PRESENT |
| Lint | `oxlint` | PRESENT |
| Unit tests | **Vitest + Testing Library** | PLANNED — zero frontend tests today |

### Testing, CI & ops

| Concern | Choice | Status |
|---|---|---|
| Backend tests | JUnit 5 + Mockito | PRESENT |
| DB tests | **Testcontainers (Postgres)** | PLANNED |
| E2E | **Playwright** | PLANNED |
| Coverage | JaCoCo report | PRESENT — no `check` gate, PLANNED |
| Code scanning | **CodeQL + Trivy + dependency-review** | PLANNED |
| Container | Multi-stage Dockerfile, non-root, healthcheck | PRESENT |
| Orchestration | Docker Compose (app, db, prometheus, grafana) | PRESENT |
| CI | GitHub Actions (`main.yml`, `release.yml`) | PRESENT |
| Registry | GHCR, multi-arch amd64 + arm64 | PRESENT |

**Do not add:** Kubernetes, Redis, Kafka, GraphQL, a service mesh, distributed
tracing, or a separate frontend host. None address a problem this project has.

## System boundaries

| Boundary | Contract |
|---|---|
| SPA → API | `fetch` with `Authorization: Bearer <Clerk session token>`. All JSON. |
| API → Clerk | Outbound HTTPS to the JWKS endpoint only, on first token validation. |
| API → Postgres | JDBC over the compose `backend` network. |
| Prometheus → API | Scrapes `/actuator/prometheus` on the `monitoring` network. |

The API is **stateless**: no server-side session, `open-in-view: false`, CSRF disabled
because credentials are never ambient cookies. It therefore scales horizontally;
all state is in Postgres and Clerk.

## Database schema

**JPA-managed (17 tables, created by Hibernate today, by Flyway once PLANNED lands):**

`listing`, `listing_photo`, `listing_favorite`, `listing_report`,
`conversation`, `conversation_participant`, `message`, `blocked_user`, `user_report`,
`post`, `post_comment`, `post_like`, `app_group`, `group_membership`, `event`,
`support_resource`, `anonymous_request`

**Hand-managed legacy (3 tables, raw SQL):** `university`, `student`, `app_user`

- `app_user.password` is nullable and **dead** — Clerk owns credentials.
- `SchoolRepository` reads `university` with raw JDBC and exposes it as `/api/schools`.
- **Ownership is keyed on email**, not on a user id: `sellerEmail`, `authorEmail`,
  `senderEmail`, `createdByEmail`, `requesterEmail`, `blockerEmail`, … across **16
  entity columns** and **37 `CurrentUser.emailOf(...)` call sites**. Clerk emails are
  mutable, so this is a known design defect — see ADR-005.

### ⚠ Known blocking defect

The legacy three tables are **never created on PostgreSQL**:
`db/init/*.sql` is not mounted to `/docker-entrypoint-initdb.d/` in
`docker-compose.yml`, and both Postgres profiles set `spring.sql.init.mode: never`.

Verified: the app boots, then `SupportResourceSeeder` → `SchoolRepository.findAll()`
runs `SELECT id, name FROM university`, throws `relation "university" does not exist`,
and the application shuts down. `docker compose up` crash-loops. Only the default
H2 profile runs. Fixing this is task 1 in `5_progress.md`.

## Data flows

**Authentication**
1. SPA loads, `ClerkProvider` initialises with `VITE_CLERK_PUBLISHABLE_KEY`.
2. User signs in through Clerk's hosted component; Clerk issues a short-lived JWT
   (60s lifetime) carrying a custom `email` claim.
3. `AuthTokenBridge` publishes Clerk's `getToken` into `lib/authToken.ts`.
4. `lib/api.ts` fetches a fresh token per request and sets the bearer header.
5. Spring validates the signature against the Clerk JWKS and the `iss` claim.
6. `CurrentUser.emailOf(jwt)` reads the `email` claim, rejecting `401` if absent.

**Profile creation** — `GET /student/profile` returns `404` when no directory row
exists; the SPA then `POST`s to `/student`. The server overwrites any `email` in the
body with the token's email so a caller cannot claim another address.

## REST surface (44 endpoints)

| Base | Endpoints |
|---|---|
| `/student` | `GET` search · `POST` create profile · `GET /profile` · `PUT /profile` |
| `/api/schools` | `GET` |
| `/api/marketplace` | `GET/POST /listings` · `GET/PUT/DELETE /listings/{id}` · `POST /listings/{id}/favorite|sold|report` · `DELETE /listings/{id}/favorite` · `GET /favorites` · `GET /my-listings` |
| `/api/messages/conversations` | `GET` · `POST` · `GET /{id}/messages` · `POST /{id}/messages` · `POST /{id}/read` |
| `/api/community/posts` | `GET` · `POST` · `DELETE /{id}` · `GET/POST /{id}/comments` · `POST/DELETE /{id}/like` · `POST /{id}/pin` |
| `/api/community/groups` | `GET` · `GET /mine` · `POST` · `POST /{id}/join` · `POST /{id}/leave` |
| `/api/community/events` | `GET` · `POST` |
| `/api/support` | `GET /resources` · `GET /requests` · `GET /requests/mine` · `POST /requests` · `POST /requests/{id}/fulfill` |
| `/api/users/block` | `GET` · `POST` · `DELETE /{email}` |
| `/api/users/report` | `POST` |

`/student` is the only base outside `/api` — moving it to `/api/students` is a
PLANNED task, because that also collapses the hardcoded route lists in
`SecurityConfig` and `SpaForwardingConfig` into one rule.

## Environment variables

**Backend** (`.env`, git-ignored; see `.env.example`)

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `default` (H2) · `dev` · `prod` |
| `DATABASE_HOST` / `DATABASE_PORT` | Postgres location (`db` / `5432` in compose) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Postgres credentials |
| `DEV_DATABASE_NAME` / `PROD_DATABASE_NAME` | Database name per profile |
| `CLERK_ISSUER` | Clerk issuer URL, validates the `iss` claim |
| `CLERK_JWKS_URI` | Clerk JWKS endpoint; set so keys load lazily, not at boot |
| `CLERK_SECRET_KEY` | Clerk Backend API. **Server-only. Never in client code.** |
| `CLERK_PUBLISHABLE_KEY` | Public key (mirrors the frontend value) |
| `JAVA_OPTS` | JVM tuning, e.g. `-Xmx512m` |
| `GF_SECURITY_ADMIN_USER` / `_PASSWORD` | Grafana login |

**Frontend** (`frontend/.env`, git-ignored)

| Variable | Purpose |
|---|---|
| `VITE_CLERK_PUBLISHABLE_KEY` | Clerk publishable key; baked in at build time. Must point at the same Clerk instance as `CLERK_ISSUER`. |

## External integrations

**Clerk** is the only third-party runtime dependency.
- App: `app_3JQAD8C90nX8hkoj6wkJ8u783sk`, development instance.
- Sign-up requires **username + email + password**; passwords have a **15-character
  minimum**; email is verified by code; **device trust is enabled** (new devices get
  an email challenge).
- The default session token carries a custom `email` claim, added via
  `session.claims` instance config. **Removing that claim breaks every endpoint.**
- Managed with the `clerk` CLI (`clerk link`, `clerk env pull`, `clerk doctor`).

## Version drift to fix

`pom.xml` targets Java 21, CI runs JDK 21, the Dockerfile builds *and runs* on
Temurin **25**. Pin all three deliberately.
