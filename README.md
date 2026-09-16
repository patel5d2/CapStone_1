# CampusBridge

[![CI](https://github.com/patel5d2/CapStone_1/actions/workflows/main.yml/badge.svg)](https://github.com/patel5d2/CapStone_1/actions/workflows/main.yml)

**A verified, regional campus marketplace for the Cincinnati metro area — with the
community features that keep it alive between transactions.**

Senior Design Capstone · University of Cincinnati · College of Education, Criminal
Justice and Human Services · School of Information Technology · 2026–2027

Project version: `0.1.1` in `pom.xml`. Documentation audited against `1b28132` on
2026-09-16; hosted release and Clerk settings were not re-verified.

---

## The problem

Campus marketplaces fail for a structural reason: a single campus never produces enough
simultaneous buyers and sellers. The project addresses the gap between isolated campus
groups and broad marketplaces with weak campus verification or regional relevance.
Its design rationale is to build **local density** across nearby institutions (Chen,
2021; Karl, 2024). This is the product thesis, not a current competitor feature audit.

## The solution

The following describes the intended product. Institutional-domain enforcement, 2FA
verification, school themes, photo uploads and admin tools are not demonstrated by the
current checkout. See the [objective scoreboard](context/5_progress.md).

CampusBridge pools **every accredited Cincinnati-area institution into one verified
regional network** — connecting students by geographic proximity rather than by
enrollment. Six named target schools: University of Cincinnati, Xavier, Northern Kentucky
University, Miami University, Cincinnati State, Mount St. Joseph.

- **Verification** — verified institutional email and two-factor authentication; the
  verification method depends on the approved Clerk configuration.
  Listings and messages are visible to verified students only.
- **Regional, not national** — listings default to the Cincinnati metro, filtered by
  school, category, price, condition and pickup location.
- **Cross-institution liquidity** — a Xavier student can buy a textbook from a UC
  student. Single-school marketplaces cannot do this.
- **Engagement beyond transactions** — directory, messaging and feeds give students a
  reason to return between purchases. This is the retention mechanism, not a side feature.
- **Moderation and safety** — admin role, reporting, a prohibited-items policy, and
  designated on-campus meetup spots.

## The app — four tabs

This is the target navigation. The current shell has five destinations, including a
separate Directory. Features below include roadmap work; scope proposals such as
offers, reviews and mentorship still need the team decision recorded in
[the overview](context/1_overview.md).

| Tab | What's in it |
|---|---|
| **Marketplace** | Listings (sell / rent / free / looking-for), photos, categories, search and filters, textbook search by course code, favourites, My Listings, report |
| **Messages** | One inbox — Marketplace / Groups / Direct. Real-time chat, offers, mark-sold, seller reviews, block and report |
| **Community** | Student directory, groups by major and graduation year, course study groups, posts, peer mentorship, events board |
| **Support** | Per-school essentials hub — food pantry, emergency aid, counselling — plus anonymous requests |

---

## 📁 Start here: `/context`

This project is built spec-first. **Before writing code — or pointing an AI agent at
this repo — read [`context/`](context/):**

| File | What it defines |
|---|---|
| [AGENTS.md](AGENTS.md) / [context/AGENT.md](context/AGENT.md) | Agent entry point and reading order |
| [1_overview.md](context/1_overview.md) | Product, roles, flows, scope, and the 11 graded success criteria |
| [2_architecture.md](context/2_architecture.md) | Stack with build status, **invariants**, schema, data flows, env vars |
| [3_patterns.md](context/3_patterns.md) | Code conventions, OWASP rules, error handling, naming |
| [4_ui_design.md](context/4_ui_design.md) | Design tokens, four-tab navigation, school theming, WCAG 2.1 AA |
| [5_progress.md](context/5_progress.md) | **Current sprint**, objective scoreboard, decision log, open questions |
| [6_rules.md](context/6_rules.md) | Team rules, Definition of Done, agent constraints |

The signed team contract governs requirements; the checked-in context records those
requirements, implementation status and proposals. The signed document and vote minutes
are not in this checkout. Source and configuration establish current implementation.

Also see the [frontend guide](frontend/README.md),
[PR checklist](docs/pull_request_template.md), and
[archived directory design](docs/design-document.md).

---

## Running it

### ⚠️ Known issue — the Docker path is broken

The checked-in `docker-compose.yml` fails YAML parsing because its first line contains
a stray `url=...` value. Verified with
`docker compose --env-file .env.example config --quiet` on 2026-09-16.
After that is corrected, a separate recorded PostgreSQL failure remains:
`SupportResourceSeeder` queries the missing `university` table. The SQL initialization
scripts are neither mounted into Postgres nor enabled by the Postgres profiles.
See [5_progress.md](context/5_progress.md). Use the default H2 development path below.

### Local development — hot reload

Prerequisites: **JDK 21**, **Node.js 22.12+ in the 22.x line** (matching CI), npm, and
network access to download dependencies and sign in through Clerk. Maven is provided by
the wrapper. H2 is in memory: restarting the backend discards development data.

```bash
# Terminal 1 — API on http://localhost:8080 (H2 in-memory)
./mvnw spring-boot:run -Dspring-boot.run.profiles=default

# Terminal 2 — SPA on http://localhost:5173 (proxies /api and /student to :8080)
cd frontend
cp -n .env.example .env  # first time only; preserves an existing file
npm ci
npm run dev
```

The frontend requires `VITE_CLERK_PUBLISHABLE_KEY`; the example matches the backend's
development defaults. A different instance requires matching `CLERK_ISSUER` and
`CLERK_JWKS_URI` in the backend process environment and the custom session claim
`email`. Spring Boot does not automatically load the root `.env` file. Current code
validates Clerk JWTs but does not enforce institutional domains or 2FA itself.

### Single server

Build the SPA into the backend's static resources, then run Spring Boot alone on
http://localhost:8080:

```bash
cd frontend
npm ci
# Configure frontend/.env as above before building.
npm run build
cd ..
./mvnw spring-boot:run -Dspring-boot.run.profiles=default
```

The compiled bundle is generated, not committed, so `npm run build` must run at least
once. The Docker image performs that build in its own stage.
Maven alone does not build the frontend. To package a complete local JAR, build the
frontend first and then run `./mvnw --batch-mode verify` from the repository root.

### Full stack with monitoring

These are the intended commands **after the Sprint 0 blockers are fixed**:

```bash
cp -n .env.example .env  # then configure local credentials; never commit them
docker compose --env-file .env.example config --quiet
docker compose up -d --build

# App         http://localhost:8080
# Prometheus  http://localhost:9090
# Grafana     http://localhost:3000
```

Compose reads the root `.env` for interpolation, but only forwards variables explicitly
listed in its service configuration. It currently omits Clerk issuer/JWKS overrides,
`PROD_DATABASE_NAME` and a frontend key build argument. Adding them only to `.env`
does not configure the app container. The current setup is a development configuration.

Monitoring is incomplete: `/actuator/prometheus` requires a token, while the scrape
configuration has no authentication; no dashboard JSON is checked in. The latency
alert also expects histogram buckets that are not configured. These files alone do
not establish the 99% availability objective.

### Pre-built image

This runs the image's default H2 profile; it does not connect to the Compose database:

```bash
docker run -p 8080:8080 ghcr.io/patel5d2/capstone_1:latest
```

Registry availability and the current `latest` digest were not checked in this audit.
The image bakes in the frontend Clerk key; a runtime environment variable cannot
replace a key already compiled into JavaScript. Use a verified version/digest for a
reproducible deployment. PostgreSQL use remains blocked by schema initialization.

---

## Architecture

A **modular monolith**: one Spring Boot deployable serving a compiled React SPA from its
own static resources, organised package-by-feature.

```
Browser ──► Spring Boot (:8080) ──► H2 (default) / PostgreSQL 16 (blocked)
  │           ├── /            React SPA
  │           ├── /api/**      REST, Clerk JWT required
  │           ├── /student/**  legacy directory REST, Clerk JWT required
  │           └── /actuator/** health public, rest authenticated
  └───────► Clerk              sign-in, token issuance, JWKS
```

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4.1.1, Java 21, Maven |
| Persistence | Spring Data JPA + legacy JDBC · H2 default · PostgreSQL 16 blocked · Flyway planned |
| Frontend | React 19 · TypeScript · Vite 8 · Tailwind CSS 4 · React Router 7 |
| Auth | Clerk — `@clerk/clerk-react` in the SPA, OAuth2 resource server in the API |
| Images | Cloudinary or S3 *(planned)* |
| Real-time | WebSocket / STOMP *(planned)* |
| Testing | JUnit Jupiter · Mockito · H2 tests · JaCoCo reports; Testcontainers planned |
| Ops | Docker · Docker Compose · Prometheus · Grafana · GitHub Actions · GHCR |

Full detail, including which pieces are built versus planned, is in
[2_architecture.md](context/2_architecture.md).

---

## Team

| Member | Role | Contact |
|---|---|---|
| Jon Soriano Sanjuan | Scrum Master & Developer | sorianjn@mail.uc.edu |
| Dharmin Patel | DevOps & QA | patel5d2@mail.uc.edu |
| Matthew Brown | Developer | brown9mc@mail.uc.edu |
| Shamak Patel | Developer | patel8sd@mail.uc.edu |
| Jessica Pham | Security & UI/UX | phamj2@mail.uc.edu |

**Recorded weekly meeting:** Mondays 5:00 PM America/New_York, Microsoft Teams.
Confirm changes with the team; this time zone follows daylight saving time.
Agenda — what you finished, what's next, what's blocking you.

**Sprints:** two weeks, Monday to Friday of the following week. Planning Monday of week 1;
stand-ups twice weekly; review and retrospective Friday of week 2.

---

## Contributing

Read [6_rules.md](context/6_rules.md) first. In short:

- **Every change goes through a pull request.** No direct pushes to `main`; at least one
  approval required.
- **Test your own work before requesting review.** A PR that breaks the build is yours to fix.
- **Never commit credentials.** Configuration comes from environment variables.
- Scope and architecture decisions are made by **majority vote** and recorded in the
  meeting minutes.

Branches: `feat/*`, `fix/*`, `chore/*`.

### Definition of Done

1. Reviewed and approved by another member
2. Merged to `main` via pull request
3. Tests pass in CI
4. Works in Chrome, Safari and Firefox
5. Works at phone screen width
6. Usable with a keyboard alone
7. Demonstrated in the sprint review

### Verification

```bash
./mvnw --batch-mode verify                        # backend: compile, test, coverage
cd frontend && npm run lint && npm run build      # frontend: lint, type-check, build
```

For documentation-only changes, check links, paths, command accuracy and
`git diff --check`. See [the verification policy](context/6_rules.md) for runtime,
authorization and browser checks. No frontend test runner or coverage threshold is
configured; a frontend build is not an end-to-end test.

---

## Project management

- **Repository** — https://github.com/patel5d2/CapStone_1
- **Project board** — https://github.com/users/patel5d2/projects/2
- **Current sprint and backlog** — [5_progress.md](context/5_progress.md)

### Releases

A `vX.Y.Z` tag runs the release workflow, which publishes
`ghcr.io/patel5d2/capstone_1` for linux/amd64 and linux/arm64, creates a GitHub release
with the executable JAR, a CycloneDX SBOM and SHA-256 checksums, and records build
provenance. The tag must match the non-SNAPSHOT version in `pom.xml`.

The container build includes the SPA, but the release JAR job currently runs Maven
without building the frontend, so its JAR lacks the SPA on a clean checkout. Trivy
scans release images and uploads SARIF with `exit-code: 0`; findings do not block a
release. CI does not start the container. CodeQL analysis and dependency-review are
not configured, and Dependabot currently omits the npm ecosystem.

---

## Design documentation

Storyboard:

![Create Account Screen](screen-1.png)
![Verify Student Screen](screen-2.png)
![Search Students Screen](screen.png)

Class diagram: ![UML Diagram](classUMLDiagram.png)

> The storyboard and class diagram predate the marketplace pivot and cover the student
> directory only. Refreshing both for the four-tab app is Sprint 0 work (S0-4, S0-5).

### Directory requirements (Fall 2026 deliverable)

The original use cases and user stories — student search, navigation, partial-match
search, profile completion and input validation — are retained in the
[archived design document](docs/design-document.md). The complete, current set of graded requirements is
the **11 success criteria** in [1_overview.md](context/1_overview.md).

---

## Security & compliance

- **OWASP Top Ten** assessed before the final demonstration, with focus on broken access
  control and injection. Target: no high-severity findings.
- **WCAG 2.1 Level AA** — keyboard operability, text alternatives, visible focus, and
  contrast to be verified in every school theme; conformance is not yet established.
- **TLS** is a deployment requirement; local examples use HTTP and termination remains
  unresolved. CampusBridge authentication stores no passwords (Clerk owns credentials);
  an unused legacy `app_user.password` column still needs cleanup.
- **FERPA posture** — no registrar integration, data minimisation, user-controlled field
  visibility as a requirement. Avoiding registrar integration alone is not a legal
  exemption; applicability depends on the data and institutional relationship.
  See the [Department of Education's FERPA overview](https://studentprivacy.ed.gov/faq/what-ferpa).
- **Ohio Rev. Code § 1349.19** — the project calls for a written breach-response and
  notification procedure; it has not been delivered in this repository.
- Guided by the **ACM Code of Ethics**: reject a convenient feature that needlessly
  exposes user data.

### Prohibited listings

Alcohol, tobacco, illegal substances, firearms, medications, live animals, recalled
products, pirated textbooks (17 U.S.C. § 106), and coursework, exams or solution manuals.
This is the recorded policy; the current listing flow has no complete enforcement or
admin moderation workflow.

---

## References

- ACM (2018). *ACM Code of Ethics and Professional Conduct.*
- Chen, A. (2021). *The Cold Start Problem: How to Start and Scale Network Effects.*
- Karl, H. (2024). The effects of networked marketplaces on startups. *Journal of Stock & Forex Trading, 11*, 261.
- OWASP (2025). *OWASP Top 10:2025.*
- W3C. [Web Content Accessibility Guidelines (WCAG) 2.1](https://www.w3.org/TR/WCAG21/).
- Family Educational Rights and Privacy Act, 20 U.S.C. § 1232g (1974).
- Ohio Rev. Code § 1349.19 (2023).
