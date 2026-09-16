# 5. Progress — Dynamic Build Tracker

> **This is the only file that changes constantly.** Update it whenever a sub-task
> finishes. Treat it as memory across sessions: read it before starting work, append
> to it before stopping.

**Last updated:** 2026-09-16 · **Baseline commit:** `c0c21d6`
**Current phase:** Phase A — make the PostgreSQL deployment actually run

---

## 🔴 Blocking issue

**The app crash-loops on PostgreSQL.** `docker compose up` does not produce a working
application, and neither does the published GHCR image. Only the default H2 profile runs.

Reproduced 2026-09-16 (throwaway Postgres 16, `SPRING_PROFILES_ACTIVE=dev`):

```
Started EnterpriseDevGroupProjectApplication in 4.334 seconds
org.springframework.jdbc.BadSqlGrammarException: bad SQL grammar
  [SELECT id, name FROM university ORDER BY name]
  at SupportResourceSeeder.run(SupportResourceSeeder.java:47)
Caused by: PSQLException: ERROR: relation "university" does not exist
Application run failed → Commencing graceful shutdown
```

Hibernate created its 17 tables; `university`, `student` and `app_user` were absent.

Root cause: `db/init/*.sql` is never mounted to `/docker-entrypoint-initdb.d/` in
`docker-compose.yml` (the path appears only in a comment at `application.yml:26`), and
both Postgres profiles set `spring.sql.init.mode: never`. CI builds the container but
never starts it, so nothing caught this.

---

## Next tasks — in order

### Phase A · Make it run (unblocks everything)

- [ ] **A1. Add Flyway; make the schema one thing.** Add `flyway-core`. Write
      `V1__baseline.sql` covering all 20 tables (17 JPA + `university`, `student`,
      `app_user`). Set `ddl-auto: validate`. Delete `h2-schema.sql`, `h2-data.sql`,
      `db/init/`. Seed reference data in `V2__seed_universities.sql`.
- [ ] **A2. Drop H2.** Remove the dependency and the H2 console config; make the
      default profile point at Postgres. Add Testcontainers so tests run on Postgres 16.
- [ ] **A3. CI runs the container it builds.** `docker compose up -d`, poll
      `/actuator/health` until healthy, fail the job otherwise. Would have caught the
      blocking issue at the commit that introduced it.

### Phase B · Correctness

- [ ] **B1. Key identity on Clerk `sub`.** Add `clerk_user_id`; migrate 16 entity
      email columns and 37 `CurrentUser.emailOf` call sites; keep email as display data
      only. Reverses ADR-005.
- [ ] **B2. Ownership tests.** `community`, `messages`, `support`, `school` have none.
      Assert user A cannot read or mutate user B's conversation, listing, or request.
- [ ] **B3. JaCoCo `check` gate** with a real threshold (coverage is reported today
      but never enforced). Add CodeQL, Trivy and dependency-review to CI.
- [ ] **B4. Delete dead code.** `@Cacheable(value = "students")` at
      `StudentService.java:46` is a no-op — there is no `@EnableCaching`; either wire
      caching or remove the annotation. Drop `app_user.password`.
- [ ] **B5. Pin the Java version.** `pom.xml` 21 / CI 21 / Dockerfile Temurin 25.

### Phase C · Scale

- [ ] **C1. Paginate every list endpoint.** No `Pageable` exists anywhere; endpoints
      return unbounded `List<T>` and there are 5 `findAll()` calls.
- [ ] **C2. Add indexes.** Zero `@Index` declared, yet queries filter on
      `seller_email`, `category`, `status`, and conversation participants.

### Phase D · Consolidation & polish

- [ ] **D1. Legacy JDBC → JPA.** Fold `controller/` + `domain/` + `dto/` + `model/` +
      `repository/` + `service/` into a `students/` feature package. Delete
      `util/SqlUtils` and `SchoolRepository`'s raw SQL.
- [ ] **D2. Move `/student` → `/api/students`**, then collapse the hardcoded route
      lists in `SecurityConfig` and `SpaForwardingConfig` to one rule.
- [ ] **D3. Add springdoc-openapi** (Swagger UI).
- [ ] **D4. Frontend tests.** Vitest + Testing Library; one Playwright happy path.
- [ ] **D5. Docs.** Move the ADRs below into `docs/adr/`, rewrite the README
      quickstart (it still describes the directory-only app).

---

## Completed

### Authentication — Clerk replaces Spring form login (2026-09-16)
- Spring Boot is an OAuth2 resource server validating Clerk JWTs against JWKS; sessions
  stateless; CSRF disabled (bearer tokens are never ambient).
- Removed `CustomUserDetailsService`, `CustomerUserDetails`, `DaoAuthenticationProvider`,
  `BCryptPasswordEncoder`, and all password handling. `app_user.password` now nullable.
- `POST /student` takes the email from the verified token and overwrites any body value,
  so a caller cannot create a profile under another address.
- Added `email` to Clerk's default session token via `session.claims` instance config.
- `ExceptionTranslator` gained a `ResponseStatusException` handler (the catch-all was
  turning `401`s into `500`s).
- Verified end-to-end with a real minted token: no token `401`, bad token `401`, valid
  token `200`, profile `404` → `POST` `201` → `200`, duplicate `409`, and a spoofed body
  email correctly ignored.
- Commits `2d7ec86`, `cff8206`.

### Frontend — React SPA replaces static HTML (2026-09-16)
- React 19 + TS + Vite 8 + Tailwind 4 + react-router 7 + `@clerk/clerk-react`.
- Vite builds into `src/main/resources/static`; Spring serves it; `SpaForwardingConfig`
  forwards client routes to `index.html`.
- Feature packages added: marketplace, messages, community, support, school.
- Static HTML pages removed (`c8b3d14`). Commits `f314eea`, `c0c21d6`.

### Ops — already in place
- Multi-stage Dockerfile (node build → maven build → JRE runtime), non-root user,
  healthcheck. Compose with app + Postgres 16 + Prometheus + Grafana on split networks.
- CI: `./mvnw verify` + JaCoCo artifact, frontend lint/type-check/build, multi-arch
  container build with GHA cache. Dependabot, CODEOWNERS, release workflow to GHCR.
- Actuator with `show-details: when_authorized`; only `/actuator/health` is public.

**Test suite:** 63 backend tests green (`./mvnw test`, verified 2026-09-16). Zero
frontend tests.

---

## Architectural decision log

| # | Decision | Rationale | Status |
|---|---|---|---|
| **001** | Clerk as the identity provider | Removes password storage, verification, reset and device-trust from our scope; a capstone should not hand-roll credential handling | Accepted |
| **002** | Modular monolith, package-by-feature | One deployable for a 5-person team; feature packages are the seams to extract services from later if ever needed | Accepted |
| **003** | SPA served from Spring static resources | One artifact, one port, one deploy; no CORS and no second container | Accepted |
| **004** | Custom `email` claim on the session token | Lets the API identify a caller without a Clerk Backend API round-trip per request | Accepted |
| **005** | Ownership keyed on email | Expedient: the legacy `student`/`app_user` tables already keyed on email | **To be reversed — B1.** Clerk emails are mutable, so a changed email orphans that user's rows across 16 entity columns |
| **006** | Clerk modal, not inline `mountSignIn` | The inline SignIn component cannot render the new-device verification step and redirects out to Clerk's hosted Account Portal; the modal renders it in-app | Accepted |
| **007** | `jwk-set-uri` **and** `issuer-uri` | `jwk-set-uri` makes key loading lazy so the app boots when Clerk is briefly unreachable; `issuer-uri` still validates `iss` | Accepted |
| **008** | PostgreSQL in every environment | H2-in-test / Postgres-in-prod hid the blocking crash above; dialect parity is worth more than in-memory speed | Accepted, pending A2 |
| **009** | Flyway over `ddl-auto: update` | `update` never drops or narrows, so prod drifts silently with no rollback and no review; collapses 3 schema sources into 1 | Accepted, pending A1 |
| **010** | No Kubernetes / Redis / Kafka / GraphQL | None address a measured bottleneck; the real scale defects are missing pagination and missing indexes | Accepted |

---

## How to update this file

1. Tick the task under **Next tasks**.
2. Move a finished group into **Completed** with the date and commit.
3. Append any new decision to the **decision log** with its rationale.
4. If a decision reverses an earlier one, update that row's Status — do not delete it.
