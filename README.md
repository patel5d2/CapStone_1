# CampusBridge

[![CI](https://github.com/patel5d2/CapStone_1/actions/workflows/main.yml/badge.svg)](https://github.com/patel5d2/CapStone_1/actions/workflows/main.yml)

**A verified, regional campus marketplace for the Cincinnati metro area — with the
community features that keep it alive between transactions.**

Senior Design Capstone · University of Cincinnati · College of Education, Criminal
Justice and Human Services · School of Information Technology · 2026–2027

Current release: `v0.1.1`

---

## The problem

Campus marketplaces fail for a structural reason: a single campus never produces enough
simultaneous buyers and sellers. Existing options each break differently — Rumie and
UniExchange are mobile-only with isolated per-campus waitlists; Facebook Marketplace
opens listings to an unverified public; university social-media groups are unmoderated
and confined to one school; and national apps show a Cincinnati student a couch in
California. Two-sided marketplaces churn out when they lack **local density** (Chen,
2021; Karl, 2024).

## The solution

CampusBridge pools **every accredited Cincinnati-area institution into one verified
regional network** — connecting students by geographic proximity rather than by
enrollment. Six supported schools: University of Cincinnati, Xavier, Northern Kentucky
University, Miami University, Cincinnati State, Mount St. Joseph.

- **Verification** — institutional email, one-time link, two-factor authentication.
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
| [1_overview.md](context/1_overview.md) | Product, roles, flows, scope, and the 11 graded success criteria |
| [2_architecture.md](context/2_architecture.md) | Stack with build status, **invariants**, schema, data flows, env vars |
| [3_patterns.md](context/3_patterns.md) | Code conventions, OWASP rules, error handling, naming |
| [4_ui_design.md](context/4_ui_design.md) | Design tokens, four-tab navigation, school theming, WCAG 2.1 AA |
| [5_progress.md](context/5_progress.md) | **Current sprint**, objective scoreboard, decision log, open questions |
| [6_rules.md](context/6_rules.md) | Team rules, Definition of Done, agent constraints |

The signed team contract is the source of truth. `context/` is that contract expressed
for builders; this README is the summary.

---

## Running it

### ⚠️ Known issue — the Docker path is broken

`docker compose up` currently **crash-loops**: the app starts, then
`SupportResourceSeeder` queries the `university` table, which is never created on
PostgreSQL. Only the H2 profile runs. This is the Sprint 0 blocker — see
[5_progress.md](context/5_progress.md). Use the local options below until it's fixed.

### Local development — hot reload

```bash
# Terminal 1 — API on http://localhost:8080 (H2 in-memory)
./mvnw spring-boot:run

# Terminal 2 — SPA on http://localhost:5173 (proxies /api and /student to :8080)
cd frontend
cp .env.example .env     # first time only
npm install              # first time only
npm run dev
```

### Single server

Build the SPA into the backend's static resources, then run Spring Boot alone on
http://localhost:8080:

```bash
cd frontend && npm install && npm run build && cd ..
./mvnw spring-boot:run
```

The compiled bundle is generated, not committed, so `npm run build` must run at least
once. The Docker image performs that build in its own stage.

### Full stack with monitoring

```bash
cp .env.example .env     # then fill in database credentials
docker compose up -d     # ⚠️ see Known issue above

# App         http://localhost:8080
# Prometheus  http://localhost:9090
# Grafana     http://localhost:3000
```

### Pre-built image

```bash
docker run -p 8080:8080 ghcr.io/patel5d2/capstone_1:latest
```

---

## Architecture

A **modular monolith**: one Spring Boot deployable serving a compiled React SPA from its
own static resources, organised package-by-feature.

```
Browser ──► Spring Boot (:8080) ──► PostgreSQL 16
  │           ├── /            React SPA
  │           ├── /api/**      REST, Clerk JWT required
  │           └── /actuator/** health public, rest authenticated
  └───────► Clerk              sign-in, 2FA, token issuance, JWKS
```

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4.1.1, Java 21, Maven |
| Persistence | Spring Data JPA · PostgreSQL 16 · Flyway *(planned)* |
| Frontend | React 19 · TypeScript · Vite 8 · Tailwind CSS 4 · React Router 7 |
| Auth | Clerk — `@clerk/clerk-react` in the SPA, OAuth2 resource server in the API |
| Images | Cloudinary or S3 *(planned)* |
| Real-time | WebSocket / STOMP *(planned)* |
| Testing | JUnit 5 · Mockito · Testcontainers *(planned)* · JaCoCo |
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

**Weekly stand-up:** Mondays 5:00 PM EST, Microsoft Teams.
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
  contrast verified in every school theme.
- **TLS** on all traffic; CampusBridge stores no passwords (Clerk owns credentials).
- **FERPA posture** — no registrar integration, data minimisation, user-controlled field
  visibility.
- **Ohio Rev. Code § 1349.19** — a written breach-notification procedure is required.
- Guided by the **ACM Code of Ethics**: reject a convenient feature that needlessly
  exposes user data.

### Prohibited listings

Alcohol, tobacco, illegal substances, firearms, medications, live animals, recalled
products, pirated textbooks (17 U.S.C. § 106), and coursework, exams or solution manuals.

---

## References

- ACM (2018). *ACM Code of Ethics and Professional Conduct.*
- Chen, A. (2021). *The Cold Start Problem: How to Start and Scale Network Effects.*
- Karl, H. (2024). The effects of networked marketplaces on startups. *Journal of Stock & Forex Trading, 11*, 261.
- OWASP (2025). *OWASP Top 10:2025.*
- W3C (2025). *Web Content Accessibility Guidelines (WCAG) 2.1.*
- Family Educational Rights and Privacy Act, 20 U.S.C. § 1232g (1974).
- Ohio Rev. Code § 1349.19 (2023).
