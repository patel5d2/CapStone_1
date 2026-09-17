# 2. Architecture — Tech Stack & Systems

> **Read the Status column before writing code.** `BUILT` = in the repo and working.
> `PARTIAL` = present but incomplete or broken. `PLANNED` = agreed, not yet installed.
> Never import a `PLANNED` dependency; add it in its own sprint task first.

## Architecture style

**Modular monolith.** One Spring Boot deployable serving a compiled React SPA from its
own static resources, organised *package-by-feature*. Chosen over microservices for a
five-person team on a two-semester timeline — see ADR-002.

```
Browser ──► Spring Boot (:8080) ──► PostgreSQL 16
  │           ├── /              React SPA (built by Vite into static resources)
  │           ├── /api/**        REST + WebSocket, Clerk JWT required
  │           └── /actuator/**   health public, everything else authenticated
  ├───────► Clerk               sign-in UI, 2FA, token issuance, JWKS
  ├───────► Cloudinary / S3     listing and profile images
  └───────► Google Maps JS API  campus pins, meetup spots, events
                 ▲
  Clerk ─────────┘ user.created webhook ──► POST /api/webhooks/clerk
```

## Invariants

Rules the codebase must never violate. A violation is a bug, not a preference. Several
map directly to graded contract objectives.

1. **Identity comes only from a verified Clerk JWT.** Never from a request body, query
   parameter, header, or client-supplied id. *(OWASP A01)*
2. **Every mutation checks ownership before it writes.** Load the row, compare its owner
   to the caller, reject on mismatch. A `WHERE owner = ?` clause alone is insufficient —
   a missing row and a forbidden row must not be indistinguishable. *(OWASP A01)*
3. **Only verified institutional accounts may post, message, or browse listings.** The
   landing page is the only anonymous surface.
4. **Personal contact details are never exposed to another user.** All communication is
   in-platform. Email addresses are never returned by a public-facing API.
5. **Profile field visibility is the user's choice.** Any field the user marks private is
   filtered server-side, never merely hidden in the client.
6. **All SQL is parameterised.** No string-concatenated queries, ever. *(OWASP A03)*
7. **Schema changes happen only through a migration.** Never `ddl-auto`, never a manual
   `ALTER`, never an edit to an applied migration.
8. **No secret reaches the client, the repo, or a log line.** Configuration comes from
   environment variables. *(Team Rule 9)*
9. **Controllers never return JPA entities.** Always a `dto/*Response`.
10. **Every list endpoint is paginated and bounded.** No unbounded `List<T>` — objective 4
    is search under one second at 10,000 listings.
11. **Contrast meets WCAG 2.1 AA in every school theme and in dark mode.** Theming may
    never reduce accessibility below the standard.

## Tech stack

### Backend

| Concern | Choice | Status |
|---|---|---|
| Language | Java **21** (LTS) | BUILT — pin it; Dockerfile currently uses 25 |
| Framework | Spring Boot **4.1.1** | BUILT |
| Build | Maven via `./mvnw` | BUILT |
| Web | `spring-boot-starter-webmvc` | BUILT |
| Persistence | Spring Data JPA / Hibernate | BUILT |
| Legacy persistence | `NamedParameterJdbcTemplate` + `util/SqlUtils` | PARTIAL — **delete**, see ADR-011 |
| Migrations | **Flyway**, `ddl-auto: validate` | BUILT — S0-1; `db/migration/V1__baseline.sql` |
| Database | PostgreSQL **16** | BUILT — every profile, including the default |
| Dev/test database | H2 in-memory | **Tests only** — `src/test/resources/`; Testcontainers is S0-2 |
| Auth | Clerk + `spring-boot-starter-oauth2-resource-server` | BUILT |
| Clerk webhook sync | `user.created` → student row | PLANNED — Sprint 1 |
| Real-time messaging | **WebSocket (STOMP)** | PLANNED — Sprint 6, objective 6 |
| Image storage | **Cloudinary or S3** | PLANNED — Sprint 1, objective 3 |
| Validation | `spring-boot-starter-validation` | BUILT |
| Boilerplate | Lombok | BUILT |
| Metrics | Actuator + `micrometer-registry-prometheus` | BUILT |
| API docs | springdoc-openapi | PLANNED |

### Frontend

| Concern | Choice | Status |
|---|---|---|
| Framework | React **19** | BUILT |
| Language | TypeScript **~6.0** | BUILT |
| Bundler | Vite **8** | BUILT |
| Styling | Tailwind CSS **4** (`@tailwindcss/vite`) | BUILT |
| Routing | `react-router-dom` **7** | BUILT |
| Auth | `@clerk/clerk-react` **5** | BUILT |
| Icons | `lucide-react` | BUILT |
| Lint | `oxlint` | BUILT |
| Maps | **Google Maps JavaScript API** | PLANNED — Sprint 9 |
| WebSocket client | **STOMP over SockJS** | PLANNED — Sprint 6 |
| Unit tests | **Vitest + Testing Library** | PLANNED — zero frontend tests today |

### Testing, CI & ops

| Concern | Choice | Status |
|---|---|---|
| Backend tests | JUnit 5 + Mockito (63 passing) | BUILT |
| DB tests | **Testcontainers (Postgres 16)** | PLANNED — Sprint 0 |
| E2E | **Playwright** | PLANNED |
| Coverage | JaCoCo report | PARTIAL — no `check` gate |
| Code scanning | **CodeQL + Trivy + dependency-review** | PLANNED — objective 10 |
| Container | Multi-stage Dockerfile, non-root, healthcheck | BUILT |
| Orchestration | Docker Compose (app, db, prometheus, grafana) | PARTIAL — app crash-loops |
| CI | GitHub Actions | PARTIAL — builds the image, never starts it |
| Registry | GHCR, multi-arch amd64 + arm64 | BUILT |

**Do not add:** Kubernetes, Redis, Kafka, GraphQL, a service mesh, or a separate
frontend host. None address a measured bottleneck. Native mobile is a stretch goal only.

## Schema ownership — resolved in S0-1

**Flyway is the only thing that creates or changes tables.** Verified 16 Sep 2026: a
clean Postgres 16 database, `./mvnw spring-boot:run`, V1 and V2 applied, `ddl-auto:
validate` accepted the result, and a restart reported *"Schema is up to date. No
migration necessary."*

The defect this replaced: Hibernate created its 17 entity tables, but `university`,
`student` and `app_user` are plain JDBC — not `@Entity` — so `ddl-auto` never created
them, `db/init/*.sql` was never mounted to `/docker-entrypoint-initdb.d/`, and both
Postgres profiles set `spring.sql.init.mode: never`. Nothing created those three tables
on Postgres, so `SupportResourceSeeder` hit `relation "university" does not exist` at
startup and every `/student` call returned 500.

Three competing schema sources (`h2-schema.sql`, `db/init/`, `ddl-auto: update`) are now
one. `h2-schema.sql` and `h2-data.sql` moved to `src/test/resources/` and feed the H2
test database only; `db/init/` is deleted. **Still open for objective 11:** CI does not
start the container it builds (S0-3), and monitoring wiring is incomplete (S0-10).

## Identity model

**Key on the Clerk user ID (`sub`), never on email.** Emails are mutable in Clerk; a
changed email orphans that user's rows.

- `user.created` webhook → create `student` with `clerk_user_id`.
- Email domain → school mapping at creation.
- Admin role → a Clerk metadata flag, read from the token.
- Email is display data, subject to invariant 4 and the user's privacy settings.

**Current state:** ownership is keyed on **email** across 16 entity columns and 37
`CurrentUser.emailOf(...)` call sites. Migrating this is Sprint 1 (ADR-005 reversal).

## Database schema

**JPA-managed (17 tables):** `listing`, `listing_photo`, `listing_favorite`,
`listing_report`, `conversation`, `conversation_participant`, `message`, `blocked_user`,
`user_report`, `post`, `post_comment`, `post_like`, `app_group`, `group_membership`,
`event`, `support_resource`, `anonymous_request`

**Hand-managed legacy (3 tables, raw SQL — to be folded into JPA):** `university`,
`student`, `app_user`. `app_user.password` is dead; Clerk owns credentials.

**Missing columns the contract requires on `listing`:** `condition`, `pickup_location`.
Present: `sellerEmail`, `title`, `description`, `category`, `listingType`, `status`,
`price`, `courseCode`, `schoolId`, `photoUrls`, `createdAt`, `updatedAt`.

**Planned tables:** `school_domain`, `offer`, `seller_review`, `notification`,
`meetup_spot`, `profile_privacy`.

## Data flows

**Authentication** — SPA initialises Clerk → user signs in (institutional email, 2FA) →
Clerk issues a 60-second JWT carrying a custom `email` claim → `AuthTokenBridge`
publishes `getToken` into `lib/authToken.ts` → `lib/api.ts` attaches a fresh bearer
token per request → Spring validates signature against the Clerk JWKS and the `iss`
claim → `CurrentUser` resolves the caller.

**Webhook sync (planned)** — Clerk `user.created` → `POST /api/webhooks/clerk` → verify
the Svix signature → create the student row keyed by `clerk_user_id` → map the email
domain to a school. The webhook endpoint is the **only** unauthenticated `/api` route,
and it authenticates by signature.

## REST surface

44 endpoints today. Bases: `/api/marketplace`, `/api/messages/conversations`,
`/api/community/{posts,groups,events}`, `/api/support`, `/api/users/{block,report}`,
`/api/schools`, and the legacy `/student`.

`/student` is the only base outside `/api` — moving it to `/api/students` also collapses
the hardcoded route lists in `SecurityConfig` and `SpaForwardingConfig` into one rule.

## Environment variables

**Backend** (`.env`, git-ignored)

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `default` · `dev` · `prod` |
| `DATABASE_HOST` / `DATABASE_PORT` | Postgres location |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Postgres credentials |
| `DEV_DATABASE_NAME` / `PROD_DATABASE_NAME` | Database name per profile |
| `CLERK_ISSUER` | Clerk issuer URL, validates `iss` |
| `CLERK_JWKS_URI` | JWKS endpoint; keys load lazily, not at boot |
| `CLERK_SECRET_KEY` | Backend API. **Server-only.** |
| `CLERK_WEBHOOK_SECRET` | *(planned)* Svix signature verification |
| `CLOUDINARY_URL` *or* `AWS_S3_BUCKET` / `AWS_REGION` | *(planned)* image storage |
| `JAVA_OPTS` | JVM tuning |
| `GF_SECURITY_ADMIN_USER` / `_PASSWORD` | Grafana login |

**Frontend** (`frontend/.env`, git-ignored)

| Variable | Purpose |
|---|---|
| `VITE_CLERK_PUBLISHABLE_KEY` | Clerk publishable key, baked in at build. Must match `CLERK_ISSUER`. |
| `VITE_GOOGLE_MAPS_API_KEY` | *(planned)* Maps JS API, HTTP-referrer restricted |

## External integrations

**Clerk** — app `app_3JQAD8C90nX8hkoj6wkJ8u783sk`, development instance.
- Sign-up requires username + email + password; 15-character password minimum; email
  verified by code; **device trust enabled**.
- The default session token carries a custom `email` claim via `session.claims`.
  **Removing that claim breaks every endpoint.**
- **Two-factor authentication is currently OFF** (`second_factor_strategies: []`) and
  Clerk does **not** restrict sign-up by domain. Both are required by objective 1 and
  both are dashboard work that no code change here can do.
- The application enforces the email half independently of that dashboard state:
  `InstitutionalAccessPolicy` refuses any token whose address is not on a `.edu` domain,
  and `SecurityConfig` applies it to `/api/**` and `/student/**`. The 2FA half is
  **switched on** (`campusbridge.auth.require-two-factor` and `VITE_REQUIRE_TWO_FACTOR`,
  both `true` since 2026-09-16) — but Clerk has no second-factor strategy enabled, so no
  token can satisfy it and the API refuses every account until the dashboard is
  configured. The agreed factor is **TOTP plus backup codes**
  (decision 015); the code reads `fva` and `user.twoFactorEnabled` and never names a
  strategy, so swapping factors is a dashboard change only.
- Managed with the `clerk` CLI (`clerk link`, `clerk env pull`, `clerk doctor`).

**Cloudinary / S3** *(planned)* — images; signed uploads, never a client-side secret.
**Google Maps JS API** *(planned)* — referrer-restricted browser key.

## Security & compliance obligations

From the contract's Ethical and Legal Considerations — these bind the architecture:

- **OWASP Top Ten** assessed before the final demo, with focus on **broken access
  control** and **injection**. No high-severity findings (objective 10).
- **TLS** for all traffic. *(Unresolved: local runs are plain HTTP on :8080 — TLS
  terminates at the deployment boundary; record how at deploy time.)*
- **Passwords as adaptive hashes** — *discharged by Clerk*, which owns credential
  storage. CampusBridge stores no passwords. Record this so a reader looking for
  bcrypt in the codebase understands why there is none.
- **Ohio Rev. Code § 1349.19** — a written breach-notification procedure is required.
- **FERPA posture** — no registrar data; data minimisation; user-controlled field
  visibility.
- **WCAG 2.1 AA** — including contrast across every school theme.
- **ACM Code of Ethics** — reject a convenient feature that needlessly exposes user data.
