# 5. Progress — Sprint Tracker

> **The only file that changes constantly.** Read it before starting work; update it
> before stopping. It is the team's shared memory across sessions and across people.
>
> **Merge etiquette:** everyone edits this file. Touch only your own sprint's checkboxes
> and append to the logs. Never reflow or reorder sections — that turns every merge into
> a conflict.

**Updated:** 2026-09-17 · **Documentation audit baseline:** `1b28132`
**Current sprint:** **Sprint 0 — Plan and set up (Sep 14 – Sep 25, 2026)**

---

> **Tracker audit 2026-09-17.** Nine of the eleven Sprint 0 items are complete; six of
> them were still showing unticked because the tracker lagged the repository, not because
> the work was missing. Each is now ticked with the evidence that was checked. **Only
> S0-6 (GitHub Projects backlog) and S0-8 (branch protection) remain**, and both are
> GitHub account actions rather than code.

## 🟡 Blocking — partially cleared

**Both startup blockers are fixed.** `docker compose --env-file .env.example config
--quiet` now passes (the stray `url=...` first line is gone, S0-9), and PostgreSQL
schema initialization is owned by Flyway (S0-1).

Verified 2026-09-16 on this machine: clean Postgres 16 via `docker compose up -d db`,
then `./mvnw spring-boot:run` on the default profile —

```
Flyway: Successfully validated 2 migrations
Migrating schema "public" to version 1 - baseline
Migrating schema "public" to version 2 - seed schools
Started EnterpriseDevGroupProjectApplication   (ddl-auto: validate passed)
restart → Schema "public" is up to date. No migration necessary.
```

`./mvnw clean test` — 63/63 green. `GET /` serves the SPA (200).

**Objective 11 is not met yet.** Working startup is necessary, not sufficient: CI still
does not start the container it builds (S0-3) and monitoring wiring is incomplete
(S0-10). Neither the published image nor a deployed environment was exercised here.

---

## Sprint 0 — Plan and set up (Sep 14 – 25)

Plan calls for: wireframes, ERD, repo structure, Docker Compose, CI, backlog,
Definition of Done.

- [x] Definition of Done agreed — recorded in `6_rules.md`
- [x] Context folder written and reconciled with the signed contract
- [x] **S0-1 Flyway baseline.** `V1__baseline.sql` covers all 20 tables (17 JPA +
      `university`, `student`, `app_user`), `V2__seed_schools.sql` seeds the 8 schools,
      `ddl-auto: validate`. `db/init/` deleted; `h2-schema.sql` and `h2-data.sql` moved
      to `src/test/resources/` rather than deleted, so the 63 tests keep running until
      S0-2 brings Testcontainers. Note for whoever reads the original story text: the
      dependency is **`org.springframework.boot:spring-boot-flyway`** plus
      `flyway-core` and `flyway-database-postgresql` — there is no
      `spring-boot-starter-flyway`, and Boot 4 moved the auto-configuration into its own
      module. With only `flyway-core` on the classpath the migrations silently never run
      and Hibernate fails validation with `missing table [anonymous_request]`.
- [x] **S0-2 Drop H2.** Verified 2026-09-17: no `com.h2database:h2` and no
      `spring-boot-h2console` anywhere in `pom.xml`; `spring-boot-testcontainers` and
      `testcontainers-postgresql` are test-scoped. `src/test/resources/application.properties`
      now runs Postgres with `spring.flyway.enabled=true` and `ddl-auto=validate`, so tests
      exercise the same engine and the same migrations as the application. Decision 008
      (Postgres everywhere) is now true in fact, not just for the app.
- [x] **S0-3 CI starts the container it builds.** Verified 2026-09-17: `main.yml` has a
      `container-build` job ("Validate container startup") that builds and `load`s the image,
      then runs `scripts/smoke-container.sh` — `docker compose -f docker-compose.ci.yml`,
      polls `/actuator/health`, fetches `/`, checks SPA routes and content types, uploads
      diagnostics, and tears the stack down on any outcome. Team Rule 7 is enforceable.
- [x] **S0-4 Wireframes** for the four tabs at mobile and desktop widths —
      **artifacts complete (24 files), team acceptance pending (2026-09-17).**
      [Review board](../docs/phase-0/wireframes/index.html) and
      [specification](../docs/phase-0/wireframes.md): eight primary frames at 375px
      and 1440px, plus directory and mobile-chat details; editable SVGs and PNGs.
      Local rendering/layout checks passed. Review, PR/merge, CI, cross-browser,
      keyboard acceptance and sprint demo remain required by the Definition of Done.
- [x] **S0-5 ERD** covering current and planned tables. `docs/phase-0/erd.md` (341 lines)
      with `erd-current.mmd` — all **20** tables, matching `V1__baseline.sql` — and
      `erd-planned.mmd` for the Sprint 1+ additions. Same Definition-of-Done caveat as the
      wireframes: artifacts exist, team review and demo acceptance are not recorded.
- [ ] **S0-6 Backlog** in GitHub Projects, stories sized for Sprints 1–5
- [x] **S0-7 Pin Java 21** — verified 2026-09-17: they already agree. `pom.xml`
      `<java.version>21</java.version>`, `Dockerfile` `eclipse-temurin:21-jdk-alpine` and
      `21-jre-alpine`, both workflows `java-version: "21"`. The "Temurin 25" in the
      original story text was stale.
- [ ] **S0-8 Enable branch protection** on `main` (Team Rule 5)
- [x] **S0-9 Correct Compose syntax.** Stray non-YAML first line removed;
      `docker compose --env-file .env.example config --quiet` passes.
- [x] **S0-10 Complete monitoring wiring.** All three parts verified 2026-09-17.
      *Authentication:* basic auth, and `scripts/smoke-monitoring.sh` asserts
      `/actuator/prometheus` returns 401 anonymously **and** 401 on a wrong password, so the
      guard is tested rather than assumed. *Dashboards:*
      `monitoring/grafana/provisioning/dashboards/campusbridge-overview.json`, seven panels
      including HTTP latency p95, 5xx rate, JVM heap and database connections.
      *Histograms:* `percentiles-histogram` set in `application.yml`, and the smoke test
      asserts `http_server_requests_seconds_bucket` is exported and that Prometheus
      actually ingests it.
- [x] **S0-11 Align release artifacts.** Verified 2026-09-17: `release.yml` sets up Node,
      runs `npm ci` then `npm run lint && npm run build` before packaging, and then
      `scripts/verify-spa-jar.py`, which fails the release if `index.html` or any compiled
      asset is missing from the JAR. The gap is not just closed, it is guarded against
      regression. *(Configuration forwarding for Clerk and production database settings is
      still unreviewed — that half of the original story stands.)*

---

## Sprint 1 — Foundation (Sep 28 – Oct 9), started early

Started ahead of the scheduled sprint because the user directed the work explicitly.
Sprint 0's remaining items (S0-6, S0-8) are unaffected.

- [x] **S1-01 Institutional eligibility and school mapping evidence** — the research
      half. [`docs/phase-1/school-domains.md`](../docs/phase-1/school-domains.md) records
      six institutions with student email domains cited to their own IT/registrar pages
      (UC `mail.uc.edu`, NKU `mymail.nku.edu`, Xavier `xavier.edu`, Miami `miamioh.edu`,
      Cincinnati State `cincinnatistate.edu`, Thomas More `thomasmore.edu`), the
      allowlist / any-`.edu` / hybrid trade-off for ADR-014, and the unsupported and
      ambiguous domain behaviour S1-03 needs. **Mount St. Joseph's student domain is not
      confirmed** and **Cincinnati Christian University closed in 2019**.
      **Not done:** the Rule 8 vote, and the two confirmation calls (MSJ ISS, UCIT).
      The story is not closeable until the minutes are attached.
- [x] **S1-02 Stable identity foundation (ADR-012).** `V4__add_student_clerk_user_id.sql`
      adds a nullable, unique `student.clerk_user_id`; `StudentRepository` carries it
      through the id-version select and the insert, and deliberately **not** through the
      update or the directory/profile select. `CurrentUser.subjectOf(Jwt)` reads the
      verified `sub`; `StudentIdentityService.ownerEmailFor(Jwt)` resolves subject-first
      and hands back the address stored on the bound row, so an address change in Clerk
      keeps ownership while the 16 email-keyed columns wait for S1-10/11/12. A second
      Clerk account presenting a bound row's address gets 409, and **nothing binds on a
      read** — the seeded demo people sit on plausible addresses, so binding by email
      match would hand a real student a fabricated profile.
      Verified 2026-09-18: `./mvnw --batch-mode verify` **108 tests, 0 failures**;
      Flyway-managed V3→V4 upgrade of a populated throwaway Postgres 16 left all 34 rows
      intact and unassigned, with `ddl-auto: validate` green on the upgraded schema.
      **Not verified:** anything against a real Clerk token — the tests build `Jwt`
      values directly. Audit, backfill rules and evidence:
      [`docs/phase-1/identity-backfill.md`](../docs/phase-1/identity-backfill.md).
      **The scoreboard does not move:** objectives 1, 6 and 10 need the ownership
      cutover, not just the column.
- [x] **S1-04 Clerk identity webhook** — signature verification, idempotency and the
      identity record. `POST /api/webhooks/clerk` (the only unauthenticated `/api` route)
      verifies the Svix HMAC over the **raw** body before parsing or touching the
      database, on the JDK's `javax.crypto` — no new dependency. Constant-time compare,
      five-minute replay window, **fails closed** when `CLERK_WEBHOOK_SECRET` is unset.
      `V5__add_clerk_identity.sql` adds `clerk_identity`; the upsert advances a row only
      for a newer `svix-timestamp`, so a Clerk retry is a no-op and a late older event
      cannot roll an address back.
      Verified 2026-09-18: `./mvnw --batch-mode verify` **120 tests, 0 failures** (12
      new); live against the jar over real HTTP — genuine delivery 204 with one row,
      retry 204 with still one row, tampered body 401 writing nothing, missing headers
      401. Log checked: **no address and no signature** (invariant 8).
      `docker compose --env-file .env.example config --quiet` passes.
      **Partly blocked, and the gap is documented rather than filled:** the webhook does
      **not** create the student row. `student` has seven NOT NULL columns a Clerk event
      does not carry, and `university_id` needs the **S1-03 mapping, which does not exist**
      and is blocked on the unvoted D-SCHOOLS. Inventing a school is what that issue
      forbids, so the webhook records the identity and `POST /student` still creates the
      directory row. Ordering, the pending-profile state and supported updates:
      [`docs/phase-1/identity-backfill.md`](../docs/phase-1/identity-backfill.md) §8.
      **Not verified:** no delivery from Clerk itself — the endpoint and its real secret
      are dashboard work. **Objective 2 does not move.**
- [ ] **Sign-in: school email + password, then the second factor** — code written
      2026-09-18, **held pending a Clerk dashboard change**. `SignInPage` now takes an
      email and a password and hands the attempt to Clerk's `<SignIn>` when Clerk answers
      `needs_second_factor`, so TOTP or a backup code finishes it (decision 015). A
      "Forgot your password?" route into Clerk's reset UI replaces the emailed-code
      escape hatch it removes.
      **Do not merge before the dashboard flip.** Verified against the live instance
      2026-09-18: `password.enabled` and `password.required` are both `true` — a password
      is already collected at sign-up — but **`password.used_for_first_factor` is `false`**,
      so it cannot be used to sign in. Until that is turned on, this page tells the
      student password sign-in is not enabled rather than showing a field that cannot
      work, and merging it would remove the only working way in.
      **Not possible as asked:** an emailed OTP as the *second* factor. Clerk does not
      offer one (`email_address.second_factors: []`), and a code arriving in the mailbox
      that already receives account mail would not be an independent factor. SMS is
      available but decision 015 rejected it on cost.
      **Not verified:** the page has not been opened in a browser — `npm run lint` (clean)
      and `npm run build` (bundle carries the new flow) are all that was run, and the
      flow cannot be exercised end to end until the dashboard changes. Chrome/Safari/
      Firefox, phone width and keyboard-only checks are all still outstanding.
- [ ] **S1-05 Image upload foundation** — **blocked on D-IMAGES (Open Question 1)**, and
      correctly so: the story's first step is to settle the provider, and the working
      agreement forbids inventing one. Nothing was built and no dependency was added;
      image storage stays **PLANNED**. What was produced is the vote's evidence and the
      provider-independent half of the design —
      [`docs/phase-1/image-storage.md`](../docs/phase-1/image-storage.md): upload
      authorization server-side, content-type by magic bytes rather than extension,
      the asset-reference constraint, the per-file rejection contract S3-03 needs, and
      the deletion/orphan-cleanup policy. **Objective 3 does not move.**
      **Found while auditing:** `ListingService` stored `photoUrls` **verbatim from the
      request body** with no validation, so a listing could point at any URL on the
      internet. Fixed separately below rather than left open.
- [x] **Bound `photoUrls` on listing create and update** (2026-09-18, its own change,
      provider-independent). `ListingRequest` now caps the list at **5** (objective 3),
      requires each entry to match `^https://[^\s"'<>`]+$`, and caps each at 255
      characters to match the `listing_photo.photo_url` column. Both `POST` and `PUT`
      are covered, since one DTO backs both and a listing must not be editable into a
      state it could not be created in.
      Verified 2026-09-18: `./mvnw --batch-mode verify` **125 tests, 0 failures** (5 new
      — valid https accepted, six photos refused, `http://` refused, `javascript:`
      refused, and the same bound on update).
      **This is not the ownership constraint.** Restricting references to the approved
      asset host, so a caller cannot attach an asset they do not own, needs the host and
      stays with S1-05.
- [x] **S1-06 Profile API fields and validation** (2026-09-18).
      `V6__add_student_profile_fields.sql` adds `graduation_year integer`,
      `bio varchar(1000)` and `photo_url varchar(255)`, all nullable, with the names and
      types from the S0-5 planned ERD. Every bound is mirrored on both request objects
      through one `ProfileFieldBounds` holder, so the two cannot drift: an over-length
      value is a field-level 400 naming the field instead of a
      `DataIntegrityViolationException` that the catch-all turns into an opaque 500.
      **Closed a live gap the acceptance criteria name:** `updateStudent` copied
      `universityId` straight from the request body, so **any signed-in student could move
      themselves to another school by editing their own profile** — which would have moved
      their directory, theme and school-scoped surfaces with them. The stored school is
      kept and the body's value is overwritten server-side. Email was already ignored.
      Verified 2026-09-18: `./mvnw --batch-mode verify` **133 tests, 0 failures** (8 new,
      against a disposable Postgres 16 — round trip of the three fields, the bio boundary
      accepted and boundary+1 rejected, over-long surname rejected by field, year and
      photo-URL bounds, all three optional, and the school-reassignment attempt refused
      while the editable fields still changed). Flyway V5→V6 on a populated database left
      all 33 rows intact and the app started, so `validate` passed on the upgraded schema.
      **Not verified:** no real Clerk session — the read/update path was exercised through
      the service and repository, not over HTTP with a minted token, so the end-to-end
      check in the story's Verify section is outstanding. **Deliberately not done:**
      `bio`/`photo_url` are **not** added to the directory's `Student` model — what the
      directory may expose is invariant 5 and S1-07's question, not this story's. The
      school is kept, not **derived**; deriving it from the verified email domain is S1-03,
      blocked on D-SCHOOLS. The `photo_url` host constraint stays with S1-05.

---

## Landed early (Sprint 2 work, done in Sprint 0)

Started ahead of its scheduled sprint because the user directed the work explicitly.
Not a scope change: four-tab navigation is already recorded in `1_overview.md` §Target
navigation and `4_ui_design.md` §Target navigation, and restated in
`future-specs/1_design-document.md` §3.2.

**Still Sprint 2, still open:** school theming (objective 8), the user theme override,
the text-size preference, and the notification centre that shares the sidebar's bottom
slot.

- [x] **S2-1 Four-tab consolidation.** `AppShell` carries four destinations in the order
      Marketplace, Messages, Community, Support. The student directory is a sub-surface
      inside Community rendered through the `Tabs` primitive, not a peer tab;
      `/directory` redirects to `/community?tab=directory` so existing links keep
      working, and `Directory` no longer renders its own `PageHeader` because Community
      owns the heading.

      **The responsive shell landed too**, though this entry had excluded it: sidebar at
      ≥1024px (`lg:w-60`, 240px), icon rail 640–1023px (`w-18`, 72px), bottom tab bar
      below that (`min-h-13`, 52px targets). One `<aside>` whose label collapses, and one
      `NAV_ITEMS` array driving all three containers, so destinations cannot drift apart.

      Verified 2026-09-17: all five routes serve 200 (`/directory` included, for old
      links); all four labels and the redirect target are compiled into the served
      bundle; rendered in headless Chromium at 1440/820/390 in light and dark.
      **Not verified:** landing on the Directory tab after the redirect, because the
      route is behind `RequireAuth` and needs a Clerk session.

---

## Contract objectives — scoreboard

The graded criteria. Keep this honest; the final report is written from it.

| # | Objective | Status | Lands in |
|---|---|---|---|
| 1 | Institutional email + 2FA, 100% validated | ⚠️ sign-in is email OTP (one factor, not two — see session notes); **email domain enforced in the application** — any non-`.edu` token is refused 403 on every `/api/**` and `/student/**` route, with a message telling the student to use a school address. **2FA is not enforced**: the check exists behind `campusbridge.auth.require-two-factor` (default off) and the `fva` claim it reads is unverified against a live token. **Correction 2026-09-18:** the earlier note here that Clerk reports `second_factor_strategies: []` is out of date — TOTP, SMS and backup codes are now enabled on the instance; what is still missing is enrolment (`sign_in.second_factor.required` is `false`). Clerk-side sign-up restriction and enrolment remain dashboard work | Sprint 1 |
| 2 | ≥6 schools, no admin setup needed | ⚠️ 8 seeded, no domain mapping | Sprint 1 |
| 3 | Listing < 2 min on mobile, 5 photos | ❌ no image upload; missing `condition`, `pickup_location` | Sprints 1, 3 |
| 4 | Search < 1s at 10,000 listings | ❌ unbounded results, no explicit search indexes or recorded load-test evidence | Sprint 4 |
| 5 | Partial-match directory across schools | ⚠️ verified against Postgres 2026-09-16 — `LIKE '%son%'` returned 6 students across 5 schools; browser/demo acceptance still not recorded | Implementation present |
| 6 | Messaging < 2s, no contacts shared | ⚠️ 5s active-chat polling; multiple DTOs expose personal emails | Sprint 6 |
| 7 | Post/reply/report on both feed types | ⚠️ post/reply/like exist; no post-report endpoint or separate school/major feeds | Sprint 8 |
| 8 | School theming automatic on login | ❌ one palette only | Sprint 2 |
| 9 | All reports actionable from one admin view | ❌ reports stored; no admin role or view | Sprint 11 |
| 10 | No high-severity OWASP findings | ❌ no recorded OWASP assessment; release Trivy is non-blocking, no CodeQL analysis | Sprint 12 |
| 11 | 99% availability | ⚠️ Compose parses, Flyway owns the schema, CI now starts the container it builds and smoke-tests health/SPA/monitoring, Grafana dashboard and latency histograms verified. **Remaining: no deployed environment and therefore no uptime evidence** — availability cannot be measured from CI | Sprint 0 |

---

## Sprint schedule

Two-week sprints, Monday to Friday of the following week.

This is the recorded planning schedule, not evidence of approval or completion.
Entries involving scope additions in Open Question 6 remain proposals. University
calendar dates and the final presentation window need team confirmation.

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

**Historical test evidence:** 63 backend tests reported green with `./mvnw test` on
2026-09-16 in the previous progress record. This audit did not re-run them. Seven Java
test classes are checked in; no frontend test runner is configured. CI generates a
JaCoCo report without a coverage threshold. Release Trivy scans are present but use
`exit-code: 0`; no CodeQL analysis or dependency-review job is configured. The release
JAR job omits the frontend build, unlike the container build.

---

## Architecture decision log

> **016 (new, Proposed):** image storage is **Cloudinary**, not S3. Direction given
> 2026-09-18; **needs a Rule 8 record**. The deciding fact is that AWS's 2026 new-account
> credits are **$200 over six months** and this project runs to the April 2027 Expo, so
> the account owner is personally liable for the bill from roughly March 2027, while
> Cloudinary's 25-credit allowance renews every 30 days and needs no payment details —
> the same no-budget constraint that settled decision 015. Cloudinary also answers
> objective 3's mobile target by transforming on delivery, where S3 would need CloudFront
> and a Lambda resize pipeline (two more services, against decision 010's grain).
> **Separately: no SDK is required either way.** A Cloudinary signed upload is one
> hash — sorted parameters, append the API secret, SHA-256 — so whether to add the SDK at
> all is its own question, and its own change. Evidence:
> [`docs/phase-1/image-storage.md`](../docs/phase-1/image-storage.md).
>
> **015 (new, Proposed):** second factor is **TOTP plus backup codes**, enabled in Clerk
> as optional first and required only once the team has enrolled. SMS was rejected on
> cost (Clerk bills per message and the project has no budget line); email OTP is not
> offered by Clerk as a second factor and would not be an independent channel if it were.
> The application code is strategy-agnostic — it reads Clerk's `fva` claim and
> `user.twoFactorEnabled`, neither of which names a factor — so changing this later is a
> dashboard change, not a code change. Needs a Rule 8 vote.
>
> **014 (new, Proposed):** institutional access is `.edu`, any `.edu`, rather than an
> allowlist of the eight seeded schools. A per-school list would need a data change
> every time the school list moved and would lock out a student whose registrar issues
> a domain nobody here listed; objective 1 asks for a verified institutional email, not
> a verified *listed* school. The trade is that any `.edu` in the world is accepted,
> including schools outside the Cincinnati metro. Needs a Rule 8 vote.
>
> **Evidence pack for that vote (2026-09-18):**
> [`docs/phase-1/school-domains.md`](../docs/phase-1/school-domains.md) — six institutions
> with student email domains cited to the institutions' own IT and registrar pages, the
> allowlist / any-`.edu` / hybrid trade-off written out, and the unsupported and
> ambiguous domain behaviour S1-03 needs. It records evidence and a ballot; it decides
> nothing.


Per **Team Rule 8**, architecture decisions are made by majority vote and recorded in the
meeting minutes. Entries marked *Proposed* have **not** been voted on.
Existing *Accepted* labels below are retained from earlier records; their meeting
minutes are not in the checkout. Merged implementation or a configured setting alone
does not establish a team vote. Confirm that evidence before treating a new decision
as accepted. A proposed replacement does not yet supersede an implemented decision.

| # | Decision | Rationale | Status |
|---|---|---|---|
| 001 | Clerk as identity provider | Removes password storage, verification, reset, 2FA and device trust from scope | **Accepted** — merged, named as done in the sprint plan |
| 002 | Modular monolith, package-by-feature | One deployable for a 5-person team; feature packages are the seams if extraction is ever needed | **Accepted** — de facto |
| 003 | SPA served from Spring static resources | One artifact, one port, one deploy; no CORS, no second container | **Accepted** — de facto |
| 004 | Custom `email` claim on the session token | Identifies the caller without a Clerk Backend API round-trip per request | **Accepted** — required by current code; hosted configuration not re-verified |
| 005 | Ownership keyed on email | Expedient: legacy tables already keyed on email | **Current implementation; replacement proposed in 012** |
| 006 | Clerk modal over inline `mountSignIn` | Inline SignIn cannot render new-device verification and redirects to the hosted portal | **Accepted** — since superseded by `@clerk/clerk-react` |
| 007 | `jwk-set-uri` **and** `issuer-uri` | Lazy key loading so the app boots when Clerk is briefly unreachable, while still validating `iss` | **Accepted** |
| 008 | PostgreSQL in every environment | H2-in-test / Postgres-in-prod hid the crash-loop; dialect parity beats in-memory speed | **Proposed** — implemented in S0-1 for the application; tests still run on H2. Merged code is not a team vote (Rule 8) |
| 009 | Flyway over `ddl-auto` | `update` never drops or narrows, so prod drifts silently; collapses 3 schema sources into 1 | **Proposed** — implemented in S0-1. Still needs a recorded vote (Rule 8) |
| 010 | No Kubernetes / Redis / Kafka / GraphQL | None address a measured bottleneck; the real defects are pagination and indexes | **Proposed** |
| 011 | Legacy JDBC folded into JPA | Two persistence styles double the review surface and the injection surface | **Proposed** |
| 012 | **Key identity on the Clerk user ID**, synced by `user.created` webhook | Clerk emails are mutable; email keys orphan rows across 16 entity columns. Supersedes 005 | **Reported carried, minutes not in the checkout** — implemented 2026-09-18 in S1-02 (`V4`, `StudentIdentityService`) on the team's confirmation that #49 passed. Attach the minutes to close Rule 8. Audit and backfill rules: [`docs/phase-1/identity-backfill.md`](../docs/phase-1/identity-backfill.md) |
| 013 | Adaptive password hashing discharged by Clerk | The contract requires adaptive hashes; Clerk owns credential storage, so CampusBridge stores none. Recorded so a reader looking for bcrypt understands its absence | **Proposed** |

---

## Open questions

Raise at the next weekly meeting. Do not guess these in code.

1. **Image storage — Cloudinary or S3?** The plan says "or". Blocks Sprint 1.

   **Evidence pack for the vote, 2026-09-18** (no provider chosen):
   [`docs/phase-1/image-storage.md`](../docs/phase-1/image-storage.md). Cloudinary's free
   allowance renews every 30 days and needs no payment details; AWS's 2026 new-account
   credits are **$200 over six months**, which runs out before the April 2027 Expo, and
   whoever owns the account is liable after that. Neither provider needs an SDK for signed
   uploads — both signing schemes are pure JDK — so the dependency question is separable
   from the provider question. The brief also lists the size, dimension, abandonment-age
   and accepted-type values the team must choose, and the deletion/orphan policy.

   **Direction given 2026-09-18: Cloudinary** (recorded as decision 016, *Proposed* —
   it still needs a Rule 8 vote). The limits, the deletion policy and whether to add the
   SDK at all are **still open** and are on the ballot in that brief.
2. ~~**Which 2FA factor?**~~ **Resolved 2026-09-16: TOTP (authenticator app) plus backup
   codes.** Free, so no budget line, and the backup codes stop a lost phone becoming a
   locked-out student. Email OTP was considered and **is not available**: checked against
   the live instance, `email_address` reports `second_factors: []`, and Clerk offers only
   `authenticator_app`, `phone_number` and `backup_code` as second factors. Email is
   already the first-factor channel here, so a code sent there would not be an
   independent second factor.

   **The application side is ready and waiting on this.** Enablement order matters:
   (1) enable the strategy in the Clerk dashboard, leaving it optional;
   (2) enrol every team account;
   (3) confirm a real token carries the `fva` claim — the claim shape is from Clerk's
   documentation and has not been seen on a token from this instance;
   (4) set `campusbridge.auth.require-two-factor=true` **and**
   `VITE_REQUIRE_TWO_FACTOR=true` together. ~~**Steps 1–3 are still outstanding; step 4 was
   done first on 2026-09-16**~~

   **Re-verified 2026-09-18** against the instance's public `/v1/environment`:
   **step (1) is done** — `authenticator_app: ["totp"]`, `backup_code` and
   `phone_number: ["phone_code"]` are all enabled as second factors, correcting the
   `second_factors: []` reading recorded on 2026-09-16. **Steps (2) and (3) are still
   outstanding**: `sign_in.second_factor.required` is `false`, so nobody is enrolled and
   no token has yet carried an `fva` claim. Email remains unavailable as a second factor
   (`email_address.second_factors: []`), so decision 015 stands unchanged.
3. **Where does TLS terminate?** The contract requires TLS; local runs are plain HTTP.
   Needs a deployment answer before Sprint 12.
4. **Six schools or eight?** The contract says "at least six", the plan names six, the
   database seeds eight. Thomas More and Cincinnati Christian — in or out?

   **Evidence recorded 2026-09-18, still not a decision**
   ([`docs/phase-1/school-domains.md`](../docs/phase-1/school-domains.md)): the two are
   not symmetric. Thomas More is a live institution issuing `thomasmore.edu` addresses.
   **Cincinnati Christian University closed at the end of the fall 2019 semester** after
   withdrawing from the Higher Learning Commission, so it enrols no students and issues
   no `ccuniversity.edu` addresses — including the three seeded in `V3`. Six schools have
   a student domain confirmed from their own IT pages; **Mount St. Joseph's is not
   confirmed** and needs a call to ISS before it can go in a mapping table. Removing the
   closed school is a new migration — never an edit to applied `V2`/`V3`.
5. **Directory email exposure vs invariant 4.** The contract says no personal contacts are
   shared; the directory's stated purpose is finding peers by email. Which wins?
6. **Scope beyond the signed contract.** The plan adds seller reviews, offers, purchase
   history, peer mentorship, study groups, campus map, notification centre and home feed.
   None appear in the contract. Rule 8 says scope changes need a majority vote.
7. **Spring break dates** — confirm and adjust Sprints 9/10.
8. **Who owns this file on merge?** Five people updating one tracker across branches will
   conflict constantly. Agree a convention now.
9. ~~**Seed the 33 demo students into Postgres?**~~ **Resolved 2026-09-16:** seeded via
   `V3__seed_demo_students.sql` so objective 5 is demonstrable. They are fabricated
   people in a persistent database — **remove them with a later migration before the
   platform carries real accounts**, and never by editing V3, which has been applied.
   Unlike the h2-data.sql original, the seed stores no password hashes: Clerk owns
   credentials (decision 013).

10. **What happens to `app_user`?** ADR-012 puts the stable Clerk subject on `student`,
    but `app_user` is a second identity table with its own unique email, written one row
    per student by `insertNewStudent`. Keying only `student` leaves an address change
    still splitting a person across two tables. Retire it (nothing reads `password`),
    key it on the subject too, or record the gap deliberately. Raised 2026-09-18 from the
    S1-02 audit ([`docs/phase-1/identity-backfill.md`](../docs/phase-1/identity-backfill.md) §5).
    **Direction given 2026-09-18: leave it and document the gap**, so `V4` touches only
    `student` and an address change still splits `app_user` from `student`. That is a
    deliberate, recorded gap for the admin story to close — it still needs a Rule 8
    record, and it is not evidence that the split is harmless.

11. **Is `clerk_identity` the right home for the pending-profile state, or should the
    webhook create a partial `student` row?** S1-04 records the verified Clerk subject and
    address in its own table because `student` has seven NOT NULL columns a Clerk event
    cannot fill. The alternative — relaxing those columns so a half-built directory row
    can exist — is a bigger schema decision that touches the directory, its search and
    objective 5, and it is not this story's to take. Raised 2026-09-18
    ([`docs/phase-1/identity-backfill.md`](../docs/phase-1/identity-backfill.md) §8).

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
- **Profile save returned 500 for a second reason**, independent of the schema blocker:
  `first_name`/`last_name` were `varchar(20)` and `resident_city` `varchar(40)`, while
  `StudentSignupRequest` and `EditStudentDetailsRequest` carried only `@NotBlank`. Any
  ordinary long surname became a `DataIntegrityViolationException`, which the catch-all
  in `ExceptionTranslator` turns into "Something went wrong…". The baseline widens those
  columns to 100 and both request objects now carry `@Size` bounds matching the schema,
  so over-length input is a field-level 400. **Every unhandled exception in this app
  becomes an opaque 500** — when debugging, read the stack trace the handler logs rather
  than the response body.
- **Sign-in is an emailed one-time code, and that is NOT two-factor (2026-09-16).** The
  team wants a verification code emailed on every sign-in. Clerk classes `email_code` as
  a **first** factor — it is already enabled on the instance
  (`email_address.first_factors: ["email_code"]`) — so it *replaces* the password rather
  than adding to it. Both the code and the account live in the same mailbox, so one
  compromised mailbox is still one compromise: this is one factor, differently chosen.
  **Objective 1's "2FA enabled" is not satisfied by it** and still needs TOTP layered on
  top. `campusbridge.auth.require-two-factor` was briefly set true and is now **false**
  again: an email-code session can never carry the `fva` second-factor claim, so leaving
  it on refused every account. The startup log states which mode is live — read it before
  debugging a wall of 403s.
- **The 2FA enrolment gate (2026-09-16).** `RequireAuth` shows
  a "Add two-step verification" screen, with a button opening Clerk's account UI, to any
  student who has a school address but no second factor. It is behind
  `VITE_REQUIRE_TWO_FACTOR` and Vite tree-shakes it out of the bundle while that is
  false, so verifying it means building with the flag on. The dashboard change is made by
  hand rather than through the Backend API. Note that the "enabled as optional first"
  decision was overtaken by the instruction to enforce immediately.
- **Institutional email enforcement is server-side, not a client check (2026-09-16).**
  `ClerkJwtAuthenticationConverter` grants `ROLE_STUDENT` only for a `.edu` token and
  `SecurityConfig` requires it on `/api/**` and `/student/**`, so a new endpoint inherits
  the rule instead of needing its own check. The SPA gate in `RequireAuth` only decides
  what the student is *shown*; it enforces nothing and must be kept in step with
  `InstitutionalAccessPolicy`. **A non-`.edu` account cannot use the app** — including
  the `gmail.com` profile at `student.id = 1` in the dev database.
- **Profile save confirmed working end to end on Postgres (2026-09-16).** A real profile
  (`student.id = 1`) was created through the browser against the Flyway-built schema,
  Clerk session and all — the first evidence the save path works outside a test. It
  survived a later migration, so persistence across restarts holds too.
