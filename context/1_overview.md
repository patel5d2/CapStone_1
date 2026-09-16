# 1. Overview — Product Vision & Core Flows

> Status: authoritative. Change this file only when the product scope changes.

## What this is

A **student network for tri-state universities** (Ohio / Kentucky / Indiana). It began
as a searchable student directory and has grown into a small social platform for
verified university students: a directory, a campus marketplace, direct messaging,
community posts/groups/events, and a peer-support desk.

Covered institutions: University of Cincinnati, Northern Kentucky University, Xavier,
Miami University, Thomas More, Cincinnati State, Mount St. Joseph, Cincinnati Christian.

> The root `README.md` still describes only the original directory. This file, not the
> README, is the current product definition.

## Target audience & roles

| Role | How it is established | Can do |
|---|---|---|
| **Visitor** | Not signed in | View the landing page only |
| **Student** | Signed in via Clerk | Everything below |
| **Admin** | Not implemented | — see Out of scope |

There is exactly one implemented role today: an authenticated student. Every
authenticated user has the same permissions. Authorization is *ownership-based*
(you may edit your own listing), not role-based.

## Core user flows

1. **Sign up / sign in** — Clerk owns the entire credential flow: email, username,
   password, email verification, new-device verification, password reset. The app
   never sees a password.
2. **Complete directory profile** — a Clerk account exists before a directory record
   does. First visit to `/profile` creates the student row (name, university, grade,
   major, city, state, optional social link); later visits edit it.
3. **Directory search** — filter students by first/last name, university, city, state,
   grade, major. Partial matches supported.
4. **Marketplace** — post listings (sell / free / wanted), browse and search, favourite,
   mark sold, report.
5. **Messages** — start a conversation with another student, send messages, mark read,
   block a user, report a user.
6. **Community** — posts with comments and likes; groups you can join/leave; events.
7. **Support** — browse curated support resources; submit an anonymous request; view
   your own requests; fulfil a request.

## Explicitly out of scope for this phase

- Admin or moderator roles, and any moderation UI. Reports are *stored*, never actioned.
- Real-time delivery (WebSocket/SSE). Messaging is request/response; the client polls.
- File and image upload. `listing_photo` stores URLs only; there is no object store.
- Email or push notifications of any kind.
- Payments, escrow, or any money movement in the marketplace.
- Organizations / multi-tenancy (Clerk supports it; we do not use it).
- Mobile apps. Responsive web only.
- `.edu` domain enforcement. The original directory validated it; Clerk sign-up
  currently does not.
