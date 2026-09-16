# 1. Overview — Product Vision & Core Flows

> **Source of truth: the signed Senior Design Team Contract (02 Sep 2026).**
> This file is the contract expressed for builders. Where this file and the code
> disagree, the contract wins and the code is wrong. Changing documented scope needs
> a team majority vote (Team Rule 8).

## What CampusBridge is

**A verified, regional campus marketplace for the Cincinnati metro area — with the
community features that keep it alive between transactions.**

The marketplace is the product. The directory, messaging and feeds are not separate
features bolted on; they exist to solve the **cold-start / liquidity problem** that
kills campus marketplaces (Chen, 2021). A single campus never generates enough
simultaneous buyers and sellers, and national platforms show a Midwest student items
from the West Coast. CampusBridge pools every Cincinnati-area school into **one
verified regional network**, and gives students reasons to return between purchases.

> The repository's README history describes a "University Student Directory." That
> framing is obsolete. The directory is one surface inside the Community tab.

## Schools

Six supported institutions: **University of Cincinnati, Xavier, Northern Kentucky
University, Miami University, Cincinnati State, Mount St. Joseph.**
Each maps to an institutional email domain.

> The database currently seeds **eight** — the six above plus Thomas More University
> and Cincinnati Christian University. The contract requires "at least six." Treat
> the seeded list as the working set; the six above are the ones themed and tested.

## Roles

| Role | Established by | Can do |
|---|---|---|
| **Visitor** | Not signed in | Landing page only |
| **Student** | Clerk account on a verified institutional email | Everything except moderation |
| **Admin** | Clerk metadata flag | Delete listings, suspend accounts, process reports, manage schools and categories |

Authorization is **ownership-based** (you may edit your own listing) plus the single
admin flag. There are no other roles.

## The app: four tabs

Navigation is four tabs, not five. The student directory lives **inside Community**.

| Tab | Contains |
|---|---|
| **Marketplace** | Listings (sell / rent / free / looking-for), photos, categories, status, search and filters, course-code textbook search, favourites, My Listings, purchase history, report |
| **Messages** | One inbox — Marketplace / Groups / Direct. Real-time chat, unread counts, photos, offers, mark-sold, seller reviews, block and report |
| **Community** | Student directory, groups by major and graduation year, course study groups, posts with comments and likes, peer mentorship, events board |
| **Support** | Per-school essentials hub (food pantry, emergency aid, counselling), anonymous requests fulfilled through the donate section |

Shared across every tab: student profile, campus map, notification centre, home feed,
admin tools, school-based theming, dark mode, accessibility.

## Core user flows

1. **Sign up** — Clerk, restricted to institutional email domains, verified by
   one-time link, with two-factor authentication.
2. **Profile sync** — a `user.created` webhook creates the student record keyed by
   **Clerk user ID**; the email domain maps to the school.
3. **Complete profile** — name, school, major, graduation year, bio, photo, and
   per-field privacy settings.
4. **List an item** — title, description, category, price, condition, pickup
   location, up to five photos.
5. **Find an item** — filter by school, category, price range, condition; sort by
   date or price; search textbooks by course code.
6. **Transact** — message the seller in-platform, make an offer, agree a safe campus
   meetup spot from the map, mark sold, leave a review.
7. **Stay engaged** — directory, groups, study groups, events, mentorship.
8. **Get help** — browse the school's essentials hub, or submit an anonymous request.
9. **Stay safe** — block, report; an admin actions every report from one view.

## Success criteria

Straight from the contract's objectives. These are how the project is graded — each
is measurable, and each must be demonstrable at the final presentation.

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

## In scope

Everything in the four tabs above, plus: image upload, admin and moderation,
institutional-domain restriction, two-factor authentication, per-school theming, the
campus map, notification centre, home feed, WCAG 2.1 AA accessibility, containerised
deployment with dependency scanning and metrics.

## Explicitly out of scope

- **Payments, escrow, or money movement.** Transactions are arranged in-platform and
  settled in person. If payments are ever added it must be through a PCI-compliant
  third party that assumes that liability.
- **Native mobile apps.** Responsive web only. React Native / Expo is a *stretch goal*
  after the core is delivered, reusing the same backend and Clerk.
- **Registrar or enrollment-system integration.** All data comes from users directly —
  this is deliberate, and keeps the platform outside FERPA's scope.
- **Organizations / multi-tenancy.** Clerk supports it; CampusBridge does not use it.
- **Anything outside the Cincinnati metropolitan region.** Regional density is the
  product thesis, not a limitation to be removed.

## Prohibited listings

Enforced as content policy, surfaced at listing creation and actionable by admins:
alcohol, tobacco, illegal substances, firearms, medications, live animals, recalled
products, **pirated textbooks** (17 U.S.C. § 106), and **coursework, exams or solution
manuals** — which violate participating universities' academic-integrity policies.
