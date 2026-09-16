# 5. Progress — Sprint Tracker

> **The only file that changes constantly.** Read it before starting work; update it
> before stopping. It is the team's shared memory across sessions and across people.
>
> **Merge etiquette:** everyone edits this file. Touch only your own sprint's checkboxes
> and append to the logs. Never reflow or reorder sections — that turns every merge into
> a conflict.

**Updated:** 2026-09-16 · **Baseline commit:** `c0c21d6`
**Current sprint:** **Sprint 0 — Plan and set up (Sep 14 – Sep 25, 2026)**

---

## 🔴 Blocking — must clear in Sprint 0

**The app crash-loops on PostgreSQL.** `docker compose up` yields no working
application; the published GHCR image is equally broken. Only the default H2 profile
runs. **Objective 11 (99% availability) is unreachable until this is fixed**, and every
sprint from 1 onward assumes a working Postgres.

Reproduced 2026-09-16 (clean Postgres 16, `SPRING_PROFILES_ACTIVE=dev`):

```
Started EnterpriseDevGroupProjectApplication in 4.334 seconds
BadSqlGrammarException: [SELECT id, name FROM university ORDER BY name]
  at SupportResourceSeeder.run(SupportResourceSeeder.java:47)
Caused by: PSQLException: ERROR: relation "university" does not exist
Application run failed → graceful shutdown
```

Cause: `db/init/*.sql` is never mounted to `/docker-entrypoint-initdb.d/`, and both
Postgres profiles set `spring.sql.init.mode: never`. CI builds the container but never
starts it, so nothing caught it.

---

## Sprint 0 — Plan and set up (Sep 14 – 25)

Plan calls for: wireframes, ERD, repo structure, Docker Compose, CI, backlog,
Definition of Done.

- [x] Definition of Done agreed — recorded in `6_rules.md`
- [x] Context folder written and reconciled with the signed contract
- [ ] **S0-1 Flyway baseline.** Add `flyway-core`; `V1__baseline.sql` covering all 20
      tables (17 JPA + `university`, `student`, `app_user`); `ddl-auto: validate`;
      delete `h2-schema.sql`, `h2-data.sql`, `db/init/`; seed schools in
      `V2__seed_schools.sql`. **Clears the blocker.**
- [ ] **S0-2 Drop H2.** Remove the dependency and console config; default profile points
      at Postgres; Testcontainers so tests run on Postgres 16.
- [ ] **S0-3 CI starts the container it builds.** `docker compose up -d`, poll
      `/actuator/health`, fail the job otherwise. Makes Team Rule 7 enforceable.
- [ ] **S0-4 Wireframes** for the four tabs at mobile and desktop widths
- [ ] **S0-5 ERD** covering current and planned tables
- [ ] **S0-6 Backlog** in GitHub Projects, stories sized for Sprints 1–5
- [ ] **S0-7 Pin Java 21** — pom 21 / CI 21 / Dockerfile Temurin 25 disagree
- [ ] **S0-8 Enable branch protection** on `main` (Team Rule 5)

---

## Contract objectives — scoreboard

The graded criteria. Keep this honest; the final report is written from it.

| # | Objective | Status | Lands in |
|---|---|---|---|
| 1 | Institutional email + 2FA, 100% validated | ❌ **2FA off; no domain allowlist** | Sprint 1 |
| 2 | ≥6 schools, no admin setup needed | ⚠️ 8 seeded, no domain mapping | Sprint 1 |
| 3 | Listing < 2 min on mobile, 5 photos | ❌ no image upload; missing `condition`, `pickup_location` | Sprints 1, 3 |
| 4 | Search < 1s at 10,000 listings | ❌ no pagination, no indexes, never load-tested | Sprint 4 |
| 5 | Partial-match directory across schools | ✅ works | done |
| 6 | Messaging < 2s, no contacts shared | ⚠️ request/response only; directory exposes emails | Sprint 6 |
| 7 | Post/reply/report on both feed types | ⚠️ posts and reports exist; school vs major feeds not split | Sprint 8 |
| 8 | School theming automatic on login | ❌ one palette only | Sprint 2 |
| 9 | All reports actionable from one admin view | ❌ reports stored; no admin role or view | Sprint 11 |
| 10 | No high-severity OWASP findings | ❌ not assessed; no CodeQL/Trivy | Sprint 12 |
| 11 | 99% availability | ❌ **crash-loops on Postgres** | Sprint 0 |

---

## Sprint schedule

Two-week sprints, Monday to Friday of the following week.

### Fall 2026

| # | Dates | Phase | Work |
|---|---|---|---|
| 0 | Sep 14 – 25 | Plan & set up | Wireframes, ERD, repo structure, Compose, CI, backlog, DoD |
| 1 | Sep 28 – Oct 9 | Foundation | Clerk webhook sync, school mapping, `.edu` allowlist, 2FA on, **Clerk-ID keying**, profile, image storage, privacy settings |
| 2 | Oct 12 – 23 | Foundation | Four-tab shell, home feed placeholder, **school colours**, dark mode, text size, notification centre, accessibility base |
| 3 | Oct 26 – Nov 6 | Marketplace | Create/edit/delete listings, categories, photos, `condition`, `pickup_location`, status, My Listings |
| 4 | Nov 9 – 20 | Marketplace | Search, filters, sort, **pagination + indexes**, course code, free/donate, Looking-For, rental, favourites, report |
| 5 | Nov 23 – Dec 4 | Fall presentation | Design report, demo, bug fixes, retrospective, spring re-planning *(short — Thanksgiving Nov 26)* |

**Winter break Dec 5 – Jan 10. No sprints.**

### Spring 2027

| # | Dates | Phase | Work |
|---|---|---|---|
| 6 | Jan 11 – 22 | Messages | WebSockets, inbox tabs, chat from listing, unread counts, photos, block/report |
| 7 | Jan 25 – Feb 5 | Messages | Offers, mark sold, seller reviews, notifications, purchase history |
| 8 | Feb 8 – 19 | Community | Directory, groups engine, posts, comments, likes, pinned, group chats, report |
| 9 | Feb 22 – Mar 5 | Community | Peer mentorship, events board, campus map with pins and meetup spots |
| 10 | Mar 8 – 19 | Support | Essentials hub, anonymous requests, home feed, cross-tab links |
| 11 | Mar 22 – Apr 2 | Admin & buffer | Admin dashboard, moderation queue, suspend users, manage schools and categories, spillover |
| 12 | Apr 5 – 16 | Test & launch | Full testing, **OWASP Top 10**, **WCAG 2.1 AA**, bug fixes, deploy, FAQ, user guide, final report |

**Final presentation & Senior Design Expo: Apr 19 – 23, 2027.**

*Spring break likely falls in Sprint 9 or 10 — confirm against the university calendar.*

**Stretch:** React Native / Expo mobile app on the same backend and Clerk.

---

## Completed

### Authentication — Clerk replaces Spring form login (2026-09-16)
Spring Boot is an OAuth2 resource server validating Clerk JWTs against JWKS; stateless
sessions; CSRF disabled (bearer tokens are never ambient). Removed
`CustomUserDetailsService`, `CustomerUserDetails`, `DaoAuthenticationProvider`,
`BCryptPasswordEncoder` and all password handling. `POST /student` takes the email from
the verified token and overwrites any body value. Added the `email` claim to Clerk's
session token. `ExceptionTranslator` gained a `ResponseStatusException` handler.
Verified end-to-end with a minted token. Commits `2d7ec86`, `cff8206`.

### Frontend — React SPA replaces static HTML (2026-09-16)
React 19 + TS + Vite 8 + Tailwind 4 + react-router 7 + `@clerk/clerk-react`. Vite builds
into `src/main/resources/static`; `SpaForwardingConfig` forwards client routes. Feature
packages added: marketplace, messages, community, support, school. Static HTML removed
(`c8b3d14`). Commits `f314eea`, `c0c21d6`.

### Ops — in place
Multi-stage Dockerfile (node → maven → JRE), non-root, healthcheck. Compose with app +
Postgres 16 + Prometheus + Grafana. CI: `./mvnw verify` + JaCoCo, frontend
lint/type-check/build, multi-arch build with GHA cache. Dependabot, CODEOWNERS, GHCR
release workflow. Actuator `show-details: when_authorized`.

**Tests:** 63 backend tests green (`./mvnw test`, verified 2026-09-16). Zero frontend tests.

---

## Architecture decision log

Per **Team Rule 8**, architecture decisions are made by majority vote and recorded in the
meeting minutes. Entries marked *Proposed* have **not** been voted on.

| # | Decision | Rationale | Status |
|---|---|---|---|
| 001 | Clerk as identity provider | Removes password storage, verification, reset, 2FA and device trust from scope | **Accepted** — merged, named as done in the sprint plan |
| 002 | Modular monolith, package-by-feature | One deployable for a 5-person team; feature packages are the seams if extraction is ever needed | **Accepted** — de facto |
| 003 | SPA served from Spring static resources | One artifact, one port, one deploy; no CORS, no second container | **Accepted** — de facto |
| 004 | Custom `email` claim on the session token | Identifies the caller without a Clerk Backend API round-trip per request | **Accepted** — in production config |
| 005 | Ownership keyed on email | Expedient: legacy tables already keyed on email | **Superseded by 012** |
| 006 | Clerk modal over inline `mountSignIn` | Inline SignIn cannot render new-device verification and redirects to the hosted portal | **Accepted** — since superseded by `@clerk/clerk-react` |
| 007 | `jwk-set-uri` **and** `issuer-uri` | Lazy key loading so the app boots when Clerk is briefly unreachable, while still validating `iss` | **Accepted** |
| 008 | PostgreSQL in every environment | H2-in-test / Postgres-in-prod hid the crash-loop; dialect parity beats in-memory speed | **Proposed** — Sprint 0 |
| 009 | Flyway over `ddl-auto` | `update` never drops or narrows, so prod drifts silently; collapses 3 schema sources into 1 | **Proposed** — Sprint 0 |
| 010 | No Kubernetes / Redis / Kafka / GraphQL | None address a measured bottleneck; the real defects are pagination and indexes | **Proposed** |
| 011 | Legacy JDBC folded into JPA | Two persistence styles double the review surface and the injection surface | **Proposed** |
| 012 | **Key identity on the Clerk user ID**, synced by `user.created` webhook | Clerk emails are mutable; email keys orphan rows across 16 entity columns. Supersedes 005 | **Proposed** — in the sprint plan, Sprint 1 |
| 013 | Adaptive password hashing discharged by Clerk | The contract requires adaptive hashes; Clerk owns credential storage, so CampusBridge stores none. Recorded so a reader looking for bcrypt understands its absence | **Proposed** |

---

## Open questions

Raise at the next weekly meeting. Do not guess these in code.

1. **Image storage — Cloudinary or S3?** The plan says "or". Blocks Sprint 1.
2. **Which 2FA factor?** SMS costs money per message; TOTP is free. Objective 1 needs a
   decision before Sprint 1.
3. **Where does TLS terminate?** The contract requires TLS; local runs are plain HTTP.
   Needs a deployment answer before Sprint 12.
4. **Six schools or eight?** The contract says "at least six", the plan names six, the
   database seeds eight. Thomas More and Cincinnati Christian — in or out?
5. **Directory email exposure vs invariant 4.** The contract says no personal contacts are
   shared; the directory's stated purpose is finding peers by email. Which wins?
6. **Scope beyond the signed contract.** The plan adds seller reviews, offers, purchase
   history, peer mentorship, study groups, campus map, notification centre and home feed.
   None appear in the contract. Rule 8 says scope changes need a majority vote.
7. **Spring break dates** — confirm and adjust Sprints 9/10.
8. **Who owns this file on merge?** Five people updating one tracker across branches will
   conflict constantly. Agree a convention now.

---

## Session notes

- The context folder was rebuilt from the **signed contract** on 2026-09-16, replacing a
  version reverse-engineered from the code. Four entries previously marked out-of-scope
  (admin/moderation, image upload, `.edu` enforcement, 2FA) were **wrong** — all four are
  graded objectives.
- The repo is roughly **four months ahead of the contract timeline on features** (Tasks
  9–12 built) and **behind on foundations** (Tasks 7–8: database, verified registration).
  Follow the sprint plan; the contract timeline is the version the instructors hold.
- `README.md` was rewritten at the same time; it previously described a directory-only app.
