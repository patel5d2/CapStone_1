# S0-6 — Sized candidate backlog, Sprints 1–5

Prepared 2026-09-16. These are **draft estimates and candidate sprint assignments**, not team commitments or evidence of implementation. Stories retain the recorded schedule; the team must select work against actual capacity and record owners during sprint planning. No owners or approval records are invented. Existing prototypes reduce implementation work but do not replace acceptance checks or the seven [Definition of Done](../../context/6_rules.md#definition-of-done) items.

[CSV for import/mapping](backlog.csv) contains the same stories, stable IDs, points, dependency IDs, objectives, scope status and acceptance criteria. **GitHub target: `Shamak10/CapStone_1`.** No Projects items have been created by this artifact. GitHub's import path depends on the chosen tool/API; this normalized CSV is suitable for a reviewed importer or manual creation, not a claim of a native GitHub Projects CSV upload. See [GitHub setup](github-setup.md) for access requirements and field mapping.

## Estimation and acceptance conventions

- Points use 1/2/3/5/8 relative effort and uncertainty; they are not person-days. This first estimate has no measured velocity behind it.
- Dependencies named `S0-*` refer to the [Sprint 0 tracker](../../context/5_progress.md#sprint-0--plan-and-set-up-sep-14--25). Dependency IDs `D-*` below are external decision gates, not completed work.
- Objective numbers map to the eleven [contract criteria](../../context/1_overview.md#success-criteria). A supporting mapping is not a promise that this story alone satisfies the whole criterion.
- Required outcomes remain required even where the proposed architecture needs a vote. Optional stories `S2-P01/P02` are excluded from required totals and must not block core acceptance.
- Each story additionally needs review, PR merge, CI, Chrome/Safari/Firefox, phone, keyboard and sprint-demo evidence per the team DoD. Local code completion or writing this backlog does not close a story.
- Sprint 5 spans Thanksgiving (Nov 26); reserve presentation time and reduce work selected to demonstrated capacity. University/presentation dates still need team confirmation.

## External decisions

| ID | Required decision/evidence | Blocks |
|---|---|---|
| D-IDENTITY | Rule 8 record for ADR-012 and a reviewed legacy ownership/backfill plan | S1-02 and ownership slices |
| D-SCHOOLS | Final supported school set (≥6), verified exact domains/aliases, admission breadth and unsupported-school handling | S1-03, S2-04 |
| D-IMAGES | Cloudinary vs S3 decision and authorized test credentials/provider setup | S1-05 |
| D-DIRECTORY | Resolve email lookup vs no-contact exposure without weakening invariant 4 | S1-07 |
| D-2FA | Factor decision evidence, authorized Clerk configuration, enrollment and real claim verification | S1-09 |
| D-TEST-TOOLS | Approve specific frontend test dependencies before adding them | S2-07 |
| D-SCOPE | Rule 8 scope approval for notification centre/home feed and other explicit additions | S2-P01, S2-P02; later optional work |
| D-LISTING-VALUES | Review condition values, pickup validation and type-specific price/rental rules | S3-01, S4-05 |

## Candidate sprint load


| Sprint | Dates | Core points | Optional points | Candidate stories |
|---|---|---:|---:|---:|
| 1 | 2026-09-28 – 2026-10-09 | 51 | 0 | 12 |
| 2 | 2026-10-12 – 2026-10-23 | 24 | 4 | 9 |
| 3 | 2026-10-26 – 2026-11-06 | 24 | 0 | 7 |
| 4 | 2026-11-09 – 2026-11-20 | 24 | 0 | 7 |
| 5 | 2026-11-23 – 2026-12-04 | 12 | 0 | 4 |

Sprint 1 is deliberately surfaced as a large risk: identity migration spans all feature modules and external authentication/storage decisions. These totals must be cut or redistributed by the team if capacity does not support them. Do not silently call deferred foundations complete.

## Sprint 1

### S1-01 — Prepare institutional eligibility and school mapping decisions (2 points)

**Scope:** Required outcome; policy decision. **Status:** Draft; unassigned. **Dependencies:** S0-5. **Objectives:** 1;2;8.

- Record a reviewed candidate list of at least six schools, exact email domains and aliases with supporting evidence.
- Present the regional allowlist versus any-.edu question (ADR-014) and unsupported-domain behavior for a Rule 8 decision; attach meeting evidence when decided.
- Do not infer eligibility or approved support from the eight existing seed rows.

Domain investigation does not itself change the Clerk instance or admission policy.

### S1-02 — Add immutable identity foundation with a safe migration plan (5 points)

**Scope:** Required identity integrity; ADR-012 proposal. **Status:** Draft; unassigned. **Dependencies:** S0-1;S0-2;D-IDENTITY. **Objectives:** 1;6;10.

- Use a new Flyway migration and student model mapping with a unique Clerk subject and documented legacy backfill rules.
- Fresh and populated Postgres 16 database checks pass without assigning unverified Clerk identities to legacy rows.
- Resolve callers from verified JWT sub; test missing and mismatched subjects; preserve compatibility until each ownership slice migrates.

### S1-03 — Implement verified domain-to-school registration (5 points)

**Scope:** Required outcome; reviewed mapping design. **Status:** Draft; unassigned. **Dependencies:** S1-01;S1-02;D-SCHOOLS. **Objectives:** 1;2.

- Enforce approved institutional eligibility and verified email server-side on every protected route.
- Map a verified domain to its school without per-student admin setup; ambiguous and unsupported domains receive a usable error.
- Exercise successful registration at at least six supported institutions and negative cases for unverified, lookalike and disallowed domains.

### S1-04 — Synchronize Clerk identity through verified idempotent webhooks (5 points)

**Scope:** Recorded implementation plan; ADR-012 proposal. **Status:** Draft; unassigned. **Dependencies:** S1-02;S1-03. **Objectives:** 1;2;10.

- Verify webhook signatures over the raw request before processing; invalid signatures cannot write records.
- Replaying user.created produces one student; retries after partial failure converge without duplicate ownership.
- Document event ordering, pending-profile state and supported updates; ensure profile completion works after asynchronous creation.

### S1-05 — Provide authenticated image upload foundation (5 points)

**Scope:** Required outcome; provider decision. **Status:** Draft; unassigned. **Dependencies:** S0-1;S1-02;D-IMAGES. **Objectives:** 3;10.

- Integrate the reviewed Cloudinary or S3 option with server-controlled upload authorization and no browser secret.
- Validate size and actual image type, enforce ownership and constrain returned asset references; rejected uploads leave actionable feedback.
- Document asset deletion/orphan cleanup and verify upload failure/retry against an authorized test account.

### S1-06 — Add complete profile API fields and validation (3 points)

**Scope:** Recorded profile plan. **Status:** Draft; unassigned. **Dependencies:** S1-02;S1-03. **Objectives:** 2;5;8.

- Provide name, mapped school, major, graduation year, bio and profile image fields with migration-backed length/type validation.
- Only the subject owning the profile can edit it; ownership/school eligibility cannot be reassigned through request fields.
- Self-profile reads and updates work on Postgres; invalid data returns field-level 400 responses.

### S1-07 — Enforce profile privacy and remove contact-bearing DTOs (5 points)

**Scope:** Required privacy invariant; conflict remains open. **Status:** Draft; unassigned. **Dependencies:** S1-06;D-DIRECTORY. **Objectives:** 5;6;10.

- Persist reviewed per-field preferences and filter private profile fields on the server.
- Other students never receive personal email/contact details in directory, listings, feed, support or chat payloads; self-account data uses a separate authorized response.
- Test defaults, preference updates, partial-name search across schools and raw API responses, including direct ID access.

Directory lookup-by-email behavior requires the recorded conflict decision; do not weaken invariant 4.

### S1-08 — Build profile completion and privacy controls (3 points)

**Scope:** Recorded profile plan. **Status:** Draft; unassigned. **Dependencies:** S1-05;S1-06;S1-07. **Objectives:** 2;5;8.

- First-time students can complete required fields, upload a photo and save field visibility with labeled controls.
- Loading, validation, save failure/retry and saved state are explicit; refresh retains the profile.
- Complete the flow at 375px and by keyboard; profile controls remain reachable from the mobile user menu.

### S1-09 — Enroll and verify institutional two-factor sign-in (3 points)

**Scope:** Required outcome; instance configuration decision. **Status:** Draft; unassigned. **Dependencies:** S1-03;D-2FA. **Objectives:** 1;10.

- Record the factor decision and authorized instance configuration; enroll authorized test/team accounts with recovery codes.
- Inspect a real token from the matching instance to validate second-factor claim shape without retaining secrets.
- Enforce corresponding frontend/backend gates together; verify permitted institutional 2FA sessions and denied missing-factor/noninstitutional sessions.

TOTP plus backup codes is proposed ADR-015. Dashboard state and enrollment are external prerequisites; do not claim email OTP alone is 2FA.

### S1-10 — Migrate marketplace ownership from email to stable student identity (5 points)

**Scope:** ADR-012 implementation slice. **Status:** Draft; unassigned. **Dependencies:** S1-02. **Objectives:** 3;6;10.

- Migrate seller, favorite and reporter ownership with explicit unmatched-row handling and no edits to applied migrations.
- The same Clerk subject retains listings and favorites after an email change.
- Another subject cannot edit, delete or impersonate the owner; remove email-based authorization in this module.

### S1-11 — Migrate messaging identity and memberships (5 points)

**Scope:** ADR-012 implementation slice. **Status:** Draft; unassigned. **Dependencies:** S1-02. **Objectives:** 6;10.

- Migrate participant, sender, blocker/blocked and reporter/reported identities preserving conversation membership.
- An email change preserves inbox membership, unread state and blocks; unauthorized subjects cannot enter a conversation.
- Backfill/constraint checks pass against populated Postgres with report and block records.

### S1-12 — Migrate community and support identity (5 points)

**Scope:** ADR-012 implementation slice. **Status:** Draft; unassigned. **Dependencies:** S1-02. **Objectives:** 7;10.

- Migrate group creator/member, post/comment author, likes, event creator and anonymous requester to stable identity.
- An email change preserves ownership; another student cannot mutate those records or discover anonymous requester identity.
- After all ownership slices land, audit remaining email-based authorization and remove it only when migration compatibility is no longer needed.

## Sprint 2

### S2-1 — Complete and verify four-tab navigation consolidation (2 points)

**Scope:** Recorded navigation requirement; existing work in progress. **Status:** Draft; unassigned. **Dependencies:** S0-4. **Objectives:** 5.

- Keep Marketplace, Messages, Community, Support in the same order at every width.
- Place Directory inside Community; /directory redirects to /community?tab=directory and browser navigation keeps the selected surface.
- Preserve loading/error/empty feedback and profile access; record phone and keyboard evidence.

ID preserved from context/5_progress.md; source already contains consolidation, so estimate is remaining verification and fixes, not a rewrite.

### S2-02 — Build desktop sidebar and tablet rail (3 points)

**Scope:** Recorded responsive design plan. **Status:** Draft; unassigned. **Dependencies:** S2-1. **Objectives:** 8.

- At 1024px and above render the 240px sidebar; at 640–1023px render a 72px icon rail with accessible labels/tooltips.
- Active destination has an indicator in addition to accent color; main content never hides beneath navigation.
- Profile/preferences stay reachable and keyboard focus remains visible at both breakpoint edges.

### S2-03 — Build mobile shell and safe-area navigation (3 points)

**Scope:** Recorded responsive design plan. **Status:** Draft; unassigned. **Dependencies:** S2-1. **Objectives:** 3;8.

- Below 640px show four bottom destinations with labels, safe-area spacing and 44px target goals.
- Top bar retains school badge, search and user access; page actions and message composer clear the bottom bar.
- At 375px and text enlargement verify no horizontal page overflow and no inaccessible controls.

### S2-04 — Apply accessible school themes automatically at login (5 points)

**Scope:** Contract objective. **Status:** Draft; unassigned. **Dependencies:** S1-03;S1-06;S2-02;D-SCHOOLS. **Objectives:** 2;8.

- Resolve the signed-in school and apply reviewed token overrides automatically for every supported school; unknown/unset school uses the base palette.
- Source palettes from official brand guidance and retain neutral content surfaces.
- Record actual contrast pairs in light/dark modes: ordinary text at least 4.5:1, large text and required control indicators at least 3:1; test login and account switch.

### S2-05 — Add theme and text-size preferences (3 points)

**Scope:** Recorded accessibility plan. **Status:** Draft; unassigned. **Dependencies:** S2-04. **Objectives:** 8.

- System/light/dark preference controls both semantic colors and dark utilities and persists across reload.
- Text size scales body and navigation labels without clipping or losing actions.
- Keyboard and school-theme checks cover both override directions and reduced-motion preference.

### S2-06 — Repair shared keyboard and announcement primitives (5 points)

**Scope:** Recorded WCAG 2.1 AA requirement. **Status:** Draft; unassigned. **Dependencies:** S2-1. **Objectives:** 10.

- Modals set initial focus, contain focus, close on Escape and return focus to their trigger.
- Tabs expose selected state and associated panels and support expected keyboard movement; toast/status feedback is announced with named dismiss controls.
- Exercise real listing/profile/community flows by keyboard and screen reader; record defects and fixes without claiming an automated pass proves conformance.

### S2-07 — Establish frontend flow and accessibility verification (3 points)

**Scope:** Recorded testing plan; dependency approval needed. **Status:** Draft; unassigned. **Dependencies:** S2-03;S2-06;D-TEST-TOOLS. **Objectives:** 3;8;10.

- Add only reviewed test dependencies in a dedicated tooling change and document how to run them.
- Cover four-tab routing, profile entry, errors and modal focus with behavior-based assertions.
- Record Chrome, Safari and Firefox checks at phone and desktop widths; CI executes the approved automated checks.

### S2-P01 — Explore notification centre (3 points)

**Scope:** Scope proposal; not committed. **Status:** Draft; unassigned. **Dependencies:** D-SCOPE;S1-02. **Objectives:** Supporting UX only.

- If approved, define event sources, retention, unread semantics and server-authorized recipient access.
- Design populated/empty/error states and cap display badges at 99+.
- If not approved, defer the feature without blocking required messaging delivery.

### S2-P02 — Explore home feed placeholder (1 points)

**Scope:** Scope proposal; not committed. **Status:** Draft; unassigned. **Dependencies:** D-SCOPE. **Objectives:** Supporting UX only.

- If approved, decide where the surface lives without adding a fifth primary tab.
- Label any placeholder clearly and link to useful existing destinations.
- Keep final home-feed delivery in the Sprint 10 proposal and avoid a misleading empty entry point.

## Sprint 3

### S3-01 — Add listing condition, pickup and photo metadata (3 points)

**Scope:** Contract listing requirements. **Status:** Draft; unassigned. **Dependencies:** S0-1;S0-2;S1-05;S1-10;D-LISTING-VALUES. **Objectives:** 3.

- Create a new migration for condition and pickup location with reviewed values/limits and safe handling of existing rows.
- Define stable photo order and asset metadata sufficient for the chosen storage provider.
- Fresh/upgrade database checks pass; zero to five photos can be represented without treating the current list table as ordered.

### S3-02 — Complete listing creation API and photo constraints (5 points)

**Scope:** Contract listing requirements. **Status:** Draft; unassigned. **Dependencies:** S3-01. **Objectives:** 2;3;10.

- Validate title, description, category, type, price rules, condition, pickup location and at most five owned image references.
- Actor and school derive from authorized profile policy; noninstitutional and unauthorized requests are denied.
- Creation is atomic; invalid photos or fields do not leave a partial listing and return usable 400 errors.

### S3-03 — Create mobile listing composer with five-photo upload (5 points)

**Scope:** Contract listing requirements. **Status:** Draft; unassigned. **Dependencies:** S3-02;S2-03;S2-06. **Objectives:** 3.

- One flow collects required fields, uploads/reorders/removes up to five photos and previews the result.
- Show per-upload progress/failure/retry, retain valid input after error, and surface prohibited-item policy before submit.
- Time authorized student creation sessions on a 375px phone flow; record sample size, individual times and median against the under-two-minute target.

### S3-04 — Implement authorized edit, delete and status transitions (3 points)

**Scope:** Recorded marketplace requirements. **Status:** Draft; unassigned. **Dependencies:** S3-02. **Objectives:** 3;10.

- Owner can edit a listing and move through reviewed AVAILABLE/PENDING/SOLD transitions; other students are denied.
- Deletion requires confirmation and has explicit asset cleanup and report-retention behavior reviewed before changing cascade semantics.
- Expired/not-found/forbidden paths and concurrent edits show actionable feedback without overwriting another owner.

### S3-05 — Provide My Listings management (3 points)

**Scope:** Recorded marketplace plan. **Status:** Draft; unassigned. **Dependencies:** S3-04. **Objectives:** 3.

- Owner sees their listings by status with bounded results and actions appropriate to each state.
- Create/edit/status changes appear after refresh; empty state leads to listing creation.
- Mobile cards and keyboard actions support long titles and upload failures without clipping.

### S3-06 — Finish listing details and safe seller contact entry (3 points)

**Scope:** Recorded marketplace plan. **Status:** Draft; unassigned. **Dependencies:** S3-02;S1-11. **Objectives:** 3;6.

- Detail shows photo gallery, title, price/type, condition, school, pickup and availability with meaningful image alternatives.
- Message seller opens or reuses the authorized listing conversation without exposing email/contact fields.
- Sold/deleted listing and own-listing cases have explicit actions; real-time under-two-second delivery remains Sprint 6 work.

### S3-07 — Demonstrate listing creation across supported schools (2 points)

**Scope:** Contract acceptance evidence. **Status:** Draft; unassigned. **Dependencies:** S3-03;S3-05;S3-06;S1-09. **Objectives:** 1;2;3.

- Demonstrate registration and listing creation for authorized test identities at at least six supported schools without manual per-student setup.
- Exercise five photos and phone timing; record browser/device/network and any failures.
- Link evidence to objectives 1–3; do not mark a missed target as passed.

## Sprint 4

### S4-01 — Bound marketplace search with stable pagination (5 points)

**Scope:** Contract search requirement. **Status:** Draft; unassigned. **Dependencies:** S3-02. **Objectives:** 4;10.

- Every marketplace collection endpoint enforces a documented maximum page size and deterministic sort with tie-breaker.
- Query input is validated/parameterized; paging produces no duplicates or omissions on the fixed benchmark fixture.
- Client preserves search/filter state while moving through pages and shows result count/loading/error states.

### S4-02 — Add reviewed search indexes and 10,000-listing benchmark (5 points)

**Scope:** Contract performance requirement. **Status:** Draft; unassigned. **Dependencies:** S4-01. **Objectives:** 4.

- Seed a disposable reproducible 10,000-listing dataset and document distributions, hardware, network and cold/warm conditions.
- Use query plans to select migration-backed indexes for measured search/filter/sort paths.
- Measure request-to-visible-results timing and server timing; record all samples and p50/p95 against the under-one-second objective with a stated workload.

The contract summary does not define a percentile/workload; record the evaluation agreement instead of silently replacing the target with a favorable average.

### S4-03 — Complete search filters and sorts (3 points)

**Scope:** Recorded marketplace plan. **Status:** Draft; unassigned. **Dependencies:** S4-01;S3-01. **Objectives:** 4.

- Combine text, school, category, price and condition filters with date/price sorts and an explicit clear action.
- Mobile filters retain selections, apply predictably and return focus when closed.
- Empty results, invalid range and server error states have useful recovery; all combinations stay bounded.

### S4-04 — Complete course-code textbook discovery (2 points)

**Scope:** Recorded marketplace plan. **Status:** Draft; unassigned. **Dependencies:** S4-03. **Objectives:** 4.

- Normalize reviewed course-code formatting consistently at input and query boundaries.
- Course-code search finds matching textbook listings and combines correctly with school/category filters.
- Case/spacing/empty-code cases and large-dataset performance are recorded.

### S4-05 — Complete free, looking-for and rental discovery (3 points)

**Scope:** Recorded marketplace plan. **Status:** Draft; unassigned. **Dependencies:** S4-03;D-LISTING-VALUES. **Objectives:** 3;4.

- Type-specific creation and discovery make FREE, LOOKING_FOR and RENT clear without adding payments.
- Price/nullability and rental description rules are reviewed and enforced in API and form validation.
- Filter/type badges and empty states work on phone and desktop; no unapproved rental duration or deposit workflow is implied.

### S4-06 — Complete favorites and listing reports (3 points)

**Scope:** Recorded plan; report requirement. **Status:** Draft; unassigned. **Dependencies:** S4-01;S1-10. **Objectives:** 9;10.

- Favorite/unfavorite is idempotent per student and available through a bounded saved-items view.
- Report form captures a validated reason with confirmation, error and duplicate-handling behavior.
- Report storage retains sufficient identity/target evidence for the later admin queue; current report storage alone does not satisfy objective 9.

### S4-07 — Run search and marketplace regression acceptance (3 points)

**Scope:** Contract evidence and quality. **Status:** Draft; unassigned. **Dependencies:** S4-02;S4-03;S4-04;S4-05;S4-06. **Objectives:** 3;4;10.

- Repeat mobile listing timing and the agreed 10,000-listing workload after all filter/type changes.
- Verify unauthorized mutations and personal-contact payload exclusions across marketplace flows.
- Record browser, phone, keyboard and performance evidence with reproducible data and unresolved failures.

## Sprint 5

### S5-01 — Prepare fall design report and traceable evidence (3 points)

**Scope:** Recorded presentation plan. **Status:** Draft; unassigned. **Dependencies:** S3-07;S4-07. **Objectives:** 1;2;3;4;5;8;11.

- Update architecture, ERD, wireframes and requirement-to-evidence table to match demonstrated behavior.
- Distinguish shipped features, measured targets, pending requirements and proposed scope in the report.
- Include limitations for messaging/moderation/security/uptime and links to reproducible evidence.

### S5-02 — Rehearse the fall demonstration (2 points)

**Scope:** Recorded presentation plan. **Status:** Draft; unassigned. **Dependencies:** S5-01. **Objectives:** 1;2;3;4;5;8.

- Use fabricated/authorized data in a repeatable script covering institutional access, profile, listing, search, directory and theming.
- Run the actual demo environment, rehearse failure recovery and verify no secret or personal contact information is shown.
- Record timing and presenter handoffs only after the team assigns them; final presentation date remains subject to university confirmation.

### S5-03 — Resolve fall acceptance defects and preserve regression evidence (5 points)

**Scope:** Quality buffer; capacity proposal. **Status:** Draft; unassigned. **Dependencies:** S3-07;S4-07. **Objectives:** 1;2;3;4;5;8;10;11.

- Triage demonstrated defects by affected objective and severity; split specific fixes into linked issues when known.
- Reproduce, fix and re-check selected defects; do not fill buffer with unapproved feature work.
- Record remaining failures and revise readiness claims honestly.

Five points is a provisional buffer, not an estimate for unlimited unknown defects.

### S5-04 — Run retrospective and replan spring scope (2 points)

**Scope:** Recorded planning process. **Status:** Draft; unassigned. **Dependencies:** S5-02;S5-03. **Objectives:** 6;7;9;10;11.

- Compare estimates with actual delivery and revise capacity using evidence.
- Carry required messaging, feeds, moderation, security and availability work into Sprints 6–12 with dependencies visible.
- Record team decisions on optional scope, winter break and spring university dates; update the shared tracker and approved board.

## Board import acceptance

The S0-6 story remains pending until these candidate stories are reviewed and represented in the selected GitHub Project with Sprint, Estimate, Scope, Status, dependency references and objective mappings. Verify ID uniqueness before import and update existing matching items rather than creating duplicates. Record the project URL, resulting issue/item URLs and import date when remote access is available. No token, assignment or remote approval is embedded in these files.
