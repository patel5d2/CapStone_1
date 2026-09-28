# 1. Overview — Product Vision & Core Flows

> **Requirements authority: the signed Senior Design Team Contract (02 Sep 2026).**
> The contract itself is not checked into this repository. This file preserves the
> team's recorded objectives and distinguishes them from implementation and proposals;
> it does not establish new contract scope. Scope changes need a team majority vote
> and meeting record (Team Rule 8). See [progress](5_progress.md) for evidence and gaps.

## What CampusBridge is

**CampusBridge aims to be a verified, regional campus marketplace for the Cincinnati
metro area, with community features that keep it useful between transactions.**

The marketplace is the product. The product hypothesis is that a regional network
offers students more nearby buyers and sellers than a single-campus service. The
directory, messaging and feeds give students reasons to return between purchases.
This is the intended product; school verification, moderation and other requirements
remain incomplete in the current implementation.

The earlier directory-only design is no longer in the checkout: `docs/design-document.md`
was deleted in `25327b6`, and the file that replaced it
([context/future-specs/1_design-document.md](future-specs/1_design-document.md)) describes
the current marketplace design, not the directory-only one. Recover the original from
`git show 25327b6^:docs/design-document.md` if it is needed. The target navigation places
the directory inside Community, which the shell now does.

## Schools

The plan names six institutions: **University of Cincinnati, Xavier, Northern Kentucky
University, Miami University, Cincinnati State, Mount St. Joseph.** The objective is
registration and listing creation at at least six schools, with institutional-domain
verification and no per-student admin setup.

The H2 and legacy PostgreSQL seed scripts contain **eight school records**, adding
Thomas More University and Cincinnati Christian University. Seed records are not
evidence of current institutional eligibility or tested support. The launch list and
email-domain mapping need confirmation; automatic school theming is not implemented.

## Intended roles

| Role | Established by | Can do |
|---|---|---|
| **Visitor** | Not signed in | Landing and authentication pages; no protected application data |
| **Student** | Clerk account on a verified institutional email | Everything except moderation |
| **Admin** | Proposed Clerk metadata capability | Delete listings, suspend accounts, process reports, manage schools and categories |

The target authorization model combines ownership checks with an admin capability.
Currently the app validates Clerk JWTs and uses email-based ownership; admin
authorization, institutional-domain enforcement and per-field privacy are not
implemented. See [architecture](2_architecture.md) for the current identity model.

## Target navigation: four tabs

The design calls for four tabs, with the student directory **inside Community**.
These are roadmap surfaces, not a claim that every listed feature is complete.
The shell now has four destinations in this order: Marketplace, Messages, Community
and Support, with the directory as a sub-tab of Community (S2-1). The target
responsive shell — sidebar, rail and bottom bar — is still Sprint 2 work.

| Tab | Contains |
|---|---|
| **Marketplace** | Listings (sell / rent / free / looking-for), photos, categories, status, search and filters, course-code textbook search, favourites, My Listings, report |
| **Messages** | One inbox — Marketplace / Groups / Direct. Real-time chat, unread counts, photos, block and report |
| **Community** | Student directory, groups by major and graduation year, posts with comments and likes, events board |
| **Support** | Per-school essentials hub (food pantry, emergency aid, counselling), anonymous requests fulfilled through the donate section |

Shared design requirements include student profiles, admin tools, school-based
theming, dark mode and accessibility. The full roadmap still needs scope confirmation
where it goes beyond the recorded contract objectives below.

**Proposed additions awaiting a recorded scope decision:** offers, seller reviews,
purchase history, peer mentorship, course study groups, campus map and meetup spots,
notification centre, and home feed. Their appearance in the sprint plan does not
constitute team approval or completed implementation.

## Intended core user flows

1. **Sign up** — Clerk, restricted to approved institutional email domains, with
   verified email and two-factor authentication. The verification method and factor
   configuration need deployment verification; a JWT alone does not prove eligibility.
2. **Profile sync (proposed implementation)** — a `user.created` webhook creates the
   student record keyed by **Clerk user ID**; the email domain maps to the school.
   Today profile creation is an explicit API action and ownership uses email.
3. **Complete profile** — name, school, major, graduation year, bio, photo, and
   per-field privacy settings.
4. **List an item** — title, description, category, price, condition, pickup
   location, up to five photos.
5. **Find an item** — filter by school, category, price range, condition; sort by
   date or price; search textbooks by course code.
6. **Transact** — message the seller in-platform, arrange an in-person exchange, mark
   sold. Structured offers, mapped meetup spots and seller reviews are proposed additions.
7. **Stay engaged** — directory, groups, events. Study groups and mentorship are proposals.
8. **Get help** — browse the school's essentials hub, or submit an anonymous request.
9. **Stay safe** — block, report; an admin actions every report from one view.

## Success criteria

The following eleven objectives are recorded here from the team's contract summary.
They remain the acceptance targets; they are not measured results or a declaration
that the current app satisfies them. Evidence and remaining work belong in the
[objectives scoreboard](5_progress.md#contract-objectives--scoreboard).

| # | Criterion | Target |
|---|---|---|
| 1 | Account validation | 100% of accounts on a verified institutional email, **2FA enabled** |
| 2 | Multi-institution support | A student from **any** of ≥6 schools registers and lists with **no admin setup** |
| 3 | Listing creation | Median **< 2 minutes** on mobile, with up to 5 photos |
| 4 | Search performance | Results in **< 1 second** against **10,000 seeded listings** |
| 5 | Directory | Partial-match search across all supported institutions |
| 6 | Messaging | Delivery and visibility in **< 2 seconds**, no personal contact details shared |
| 7 | Discussion feeds | Post, reply and report on both school and major feeds |
| 8 | School theming | UI colours switch **automatically on login** for every supported school |
| 9 | Moderation | **Every reported listing actionable from one admin view** |
| 10 | Security | **No high-severity OWASP Top Ten findings** at the final demonstration |
| 11 | Reliability | **99% availability** over the evaluation period, per the monitoring dashboards |

## Recorded delivery scope

The eleven objectives require the marketplace, directory, messaging, discussion
feeds, image upload, admin and moderation, institutional-domain restriction,
two-factor authentication and per-school theming. The recorded delivery plan also
includes WCAG 2.1 AA accessibility and containerised deployment with dependency
scanning and metrics. Preserve these requirements while resolving implementation
gaps; proposals above need a recorded scope decision before being treated as
additional commitments.

## Explicitly out of scope

- **Payments, escrow, or money movement.** Transactions are arranged in-platform and
  settled in person. Adding payments requires a separate scope, provider and compliance
  review; choosing a provider does not by itself settle legal responsibility.
- **Native mobile apps.** Responsive web only. React Native / Expo is a *stretch goal*
  after the core is delivered, reusing the same backend and Clerk.
- **Registrar or enrollment-system integration.** The product plan uses user-supplied
  profile data. This boundary is a data-minimisation choice, not a legal determination
  about FERPA; any institutional relationship or data integration needs its own review.
- **Organizations / multi-tenancy.** Clerk supports it; CampusBridge does not use it.
- **Anything outside the Cincinnati metropolitan region.** Regional density is the
  product thesis, not a limitation to be removed.

## Intended prohibited-listing policy

The recorded policy prohibits:
alcohol, tobacco, illegal substances, firearms, medications, live animals, recalled
products, **pirated textbooks**, and **coursework, exams or solution manuals**.
The policy must be surfaced at listing creation and supported by admin review.
Reports can currently be stored, but the moderation queue and enforcement are
incomplete; do not describe the policy as already enforced.
