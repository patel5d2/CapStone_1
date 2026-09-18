# S1-02 — Clerk-ID keying: audit, backfill rules and the D-IDENTITY ballot

**Prepared 2026-09-18 as the pre-vote pack; updated the same day, after the team
confirmed #49, with what actually landed.** Sections 1, 2 and 5 are unchanged findings.
Sections 3 and 4 now describe merged code rather than a plan; section 6's ballot items 1
and 2 are reported as carried, item 4 as decided by direction, item 3 as still open.
**The minutes are not in the checkout — attach them to close the Rule 8 loop.**

S1-02 implements ADR-012. This document holds the audit behind it, the backfill rules the
acceptance criteria call for, and the record of what was built.

The 37 `CurrentUser.emailOf` call sites are **untouched**, by design: their cutover is
S1-10, S1-11 and S1-12. What landed is the stable column, the one place that resolves a
caller from it, and the tests.

---

## 1. What the code actually looks like (verified 2026-09-18)

Read from the checkout today, not from the story text.

**16 ownership columns across 13 tables key on email** — this matches the story's count:

| Table | Column(s) | Feature |
|---|---|---|
| `listing` | `seller_email` | marketplace |
| `listing_favorite` | `user_email` | marketplace |
| `listing_report` | `reporter_email` | marketplace |
| `conversation_participant` | `user_email` | messages |
| `message` | `sender_email` | messages |
| `blocked_user` | `blocker_email`, `blocked_email` | messages |
| `user_report` | `reporter_email`, `reported_email` | messages |
| `app_group` | `created_by_email` | community |
| `group_membership` | `user_email` | community |
| `post` | `author_email` | community |
| `post_comment` | `author_email` | community |
| `post_like` | `user_email` | community |
| `event` | `created_by_email` | community |
| `anonymous_request` | `requester_email` | support |

**37 call sites** resolve the caller through `CurrentUser.emailOf(...)`: `grep -rc` returns
38 occurrences in `src/main`, one of which is the declaration in `CurrentUser` itself.
They cluster in `ListingController` (9), `PostController` (6), `ConversationController` (5),
`StudentController` (4), `GroupController` (4), `SupportController` (3), `BlockController` (3),
and one each in `UserReportController`, `EventController` and `ClerkJwtAuthenticationConverter`.
No production code reads the `sub` claim today; only two tests set it.

### Two places the story text and the repository disagree

Per the working agreement, stated rather than silently resolved:

1. **"Map it in the student model and keep `ddl-auto: validate` passing" does not apply
   to `student`.** All 16 `@Entity` classes are the feature tables; `student`, `app_user`
   and `university` are **legacy JDBC**, read through `StudentRepository`'s hand-written
   SQL and column aliases. Hibernate never sees the `student` table, so `validate` will
   pass whether or not `clerk_user_id` is mapped — it proves nothing here. Mapping the
   column means adding it to the `SELECT` lists, the row mapper target and the insert in
   `StudentRepository`, and the check that it worked is a query, not `validate`.
   (This is the debt ADR-011 proposes to remove; S1-02 must not do that refactor.)
2. **`app_user` is a second identity table the story does not mention.** It has its own
   `email varchar(255) NOT NULL UNIQUE`, and `StudentService.insertNewStudent` writes one
   `app_user` row per `student` row in the same transaction. A stable identity on
   `student` alone leaves `app_user` keyed on a mutable email, so an address change still
   splits a person in two. Section 5 lists the options; **which one is correct is a
   question for the team, not for this document.**

## 2. Backfill rules

The acceptance criterion is "documented legacy backfill rules … without assigning
unverified Clerk identities to legacy rows." These are those rules.

**Rule 1 — a subject is bound only when it creates the row, never by matching an email.**
`clerk_user_id` is written in exactly one place today: `insertNewStudent`, from the
verified token, when a Clerk account creates its own directory row. From S1-04 the
verified `user.created` webhook is the second. There is **no** bulk
`UPDATE student SET clerk_user_id = ...`, and nothing binds on a read.

*Corrected while implementing.* The first draft of this rule also allowed binding on
"the first authenticated request from a subject whose token carries a verified email".
That is unsafe for the reason rule 4 describes: the seeded demo rows sit on plausible
addresses, so the first real student to sign in with a matching one would silently
inherit a fabricated profile. A verified email proves who the *caller* is; it does not
prove the *row* is theirs. `StudentIdentityServiceTest.readingDoesNotBindALegacyRowToTheCaller`
is the regression test.

**Rule 2 — an unmatched row stays `NULL`, indefinitely and without penalty.** A NULL
`clerk_user_id` means "nobody has proved they own this row", which is the truth for every
legacy row until its owner signs in. Unmatched rows keep working through the email path
during the compatibility window (rule 5). `student.id = 1` is a `gmail.com` address that
`InstitutionalAccessPolicy` refuses outright, so it can never be claimed — that is the
correct outcome, not a bug to fix here.

**Rule 3 — binding is one-to-one and write-once.** `clerk_user_id` is `UNIQUE`, so one
subject owns at most one student row. If a subject presents an email whose row is already
bound to a *different* subject, the request does not steal it, does not fall back to the
email path, and does not create a second row: it fails, and it logs loudly enough to be
noticed. A verified email arriving on a second Clerk subject means either an address was
recycled by the registrar or an account was re-created; both need a human.

**Rule 4 — the fabricated demo rows must never be bindable.** This is the story's
guardrail and it is not automatic. `V3` seeds 33 students *and* an `app_user` row for each
(`INSERT INTO app_user (role, email) SELECT 'USER', s.email FROM student s`), on
plausible real addresses such as `sarah.johnson@mail.uc.edu`. Two consequences:

- A real UC student who is actually called Sarah Johnson cannot create a profile today:
  `insertNewStudent` finds the seeded `app_user` row and throws
  `EmailAlreadyExistsException` → **409**. She is locked out by a fabricated person, and
  the message tells her an account already exists, which is false.
- If binding ever matched on email, that same collision would hand a real Clerk subject a
  row with a fabricated major, grade and social link.

Rule 1 already prevents the second. For the first, the honest fix is the one Open
Question 9 already committed to: **delete the demo students in a migration before the
platform carries real accounts.** If they must outlive the first real account, then the
same migration that adds `clerk_user_id` also adds a marker (`student.seeded boolean not
null default false`, set true for exactly those rows) and binding skips marked rows —
because "we know which 33 they are" stops being true the moment someone adds a 34th.
**Marker or deletion is a team decision; both are named on the ballot in section 6.**

**Rule 5 — email lookup stays until the last slice migrates.** Every ownership check
resolves subject-first and falls back to email while the column is nullable:
`clerk_user_id = :sub` if the row has one, else `email = :email`. The fallback is removed
in S1-12, not before, and only once every one of the 16 columns has a subject-keyed
equivalent. Removing it earlier orphans exactly the data this story exists to protect.

**Rule 6 — the 16 ownership columns are not backfilled by this story.** They hold token
emails, including emails of people with no `student` row at all (nothing requires a
profile before posting). Their cutover is S1-10/11/12. What S1-02 owes them is the stable
key to point at and the rule above for how a row gets one.

## 3. The migration, as merged

`V4__add_student_clerk_user_id.sql`: a nullable `varchar(255)` column plus
`uk_student_clerk_user_id UNIQUE (clerk_user_id)`. **S1-03's domain mapping and the
demo-student removal still need versions of their own — V4 is taken.**

Nullable, so every existing row stays valid. Postgres treats NULLs as distinct in a unique
constraint, so any number of unbound rows coexist while a subject still owns at most one
row. `varchar(255)` because Clerk subjects are opaque strings; nothing parses them.

The planned separate index was dropped: PostgreSQL implements `UNIQUE` with a btree index,
so the constraint already serves lookups by `clerk_user_id`.

Because `student` is JDBC rather than JPA (§1), the column is carried by
`StudentRepository` — added to `SELECT_VERSION_UNIVERSITY_ID` and `INSERT_NEW_STUDENT`,
and deliberately **absent from `UPDATE_STUDENT_INFO`**, so a profile edit changes what the
row says and never whose row it is. It is also absent from
`SELECT_VERSION_UNIVERSITY_NAME`, the query behind the directory and the profile response,
so the subject is never serialised to a client.

## 4. Resolving the caller from `sub`, as merged

Invariant 1: identity comes from the verified token only. `CurrentUser.subjectOf(Jwt)`
sits alongside `emailOf(Jwt)` and fails 401 on a missing or blank `sub`, exactly as
`emailOf` does for a missing `email`.

`StudentIdentityService.ownerEmailFor(Jwt)` is the single resolution point:

1. Look the token's `sub` up in `student.clerk_user_id`. A hit returns **the address
   stored on that row**, not the one in the token — so a student who changed their
   address in Clerk still reaches their own data through the existing email-keyed columns.
   That is how ownership survives a rename *before* the 16 columns are cut over.
2. No hit: if a row exists on the token's address and is owned by a **different** subject,
   refuse with **409** (`PROFILE_CLAIMED_MESSAGE`). An unbound row is left alone, not
   claimed.
3. Otherwise return the token's address unchanged, which is what keeps every legacy row
   and every not-yet-created profile working.

`StudentController` uses it for `GET` and `PUT /student/profile`. `POST /student` binds
the new row to `subjectOf(...)`, and `insertNewStudent` now refuses a second profile for a
subject that already has one — checked *before* the email check, so a student who renamed
in Clerk gets a 409 instead of tripping the unique constraint as an opaque 500.

Test results, `StudentIdentityServiceTest` against a disposable PostgreSQL 16 running the
real migrations (6/6 green):

| Case | Expected | Result |
|---|---|---|
| Token with no `sub` claim | 401 | pass |
| Token with no `email` claim | 401 | pass |
| `sub` present, no student row for it | resolves by email fallback; nothing bound | pass |
| Email changed in Clerk, same `sub` | resolves to the stored address — ownership follows the subject | pass |
| A second subject presents a bound row's address | 409, and the row's owner is unchanged | pass |
| A read against a seeded legacy row | returns the address, leaves `clerk_user_id` NULL | pass |

`./mvnw --batch-mode verify`: **108 tests, 0 failures**, `ddl-auto: validate` green.

Database checks, both run 2026-09-18 against throwaway containers (since removed):

- **Fresh** — Testcontainers PostgreSQL 16 in the test run: 4 migrations validated, schema
  at v4.
- **Populated upgrade, through Flyway** — booted the built jar with
  `--spring.flyway.target=3` to reach today's state, inserted a real student profile and
  its `app_user` row alongside the 33 seeded ones, then booted again with no target.
  Flyway applied v4 to the populated database, the application started (so `validate`
  passed on the upgraded schema), and **all 34 rows survived with `clerk_user_id IS NULL`**.
- The unique constraint was exercised directly: a second row claiming an in-use subject is
  rejected with `duplicate key value violates unique constraint "uk_student_clerk_user_id"`.

**Not proven here:** none of this used a real Clerk token against a running instance. The
tests construct `Jwt` values directly, so what is verified is the resolution logic and the
schema, not the live token's claim shape. Signing in as two real Clerk accounts and
renaming one is still required for the sprint demo.

## 5. `app_user` — the question this story cannot answer alone

Three options, none of them chosen here:

- **Retire it.** Nothing reads `password`; if `role` is the only live field it can move to
  `student`, and the second unique email disappears. Interacts with the admin capability
  (objective 9), which does not exist yet.
- **Key it on the subject too**, in the same migration, and keep the two rows in step.
- **Leave it**, and accept that an address change still splits `app_user` from `student`
  until the admin story revisits it — an explicit, documented gap rather than an oversight.

## 6. The ballot for #49 (D-IDENTITY), and where each item stands

1. **ADR-012 itself** — key identity on the Clerk user ID, synced by webhook.
   **Reported carried; implemented in this change.** Minutes not in the checkout.
2. **Backfill rules** — section 2, with rule 1 corrected as noted there.
   **Reported carried; implemented.**
3. **Demo students** — delete before the first real account, or mark and exclude.
   **Still open.** Not urgent for binding (rule 1 makes binding-by-email impossible), but
   it still blocks a real student whose address collides with a seeded one — see §7.
4. **`app_user`** — retire, re-key, or document the gap. **Direction given 2026-09-18:
   leave it, document the gap.** `V4` touches only `student`, so an address change still
   splits `app_user` from `student` until the admin story revisits it. Recorded as Open
   Question 10 rather than silently accepted.

## 7. Found while auditing — not fixed here, not in scope

- **A seeded demo address blocks a real student with the same address** (409 from
  `insertNewStudent`, message claims an account exists). Real defect, reachable today,
  independent of ADR-012. Needs its own issue.
- `V3` seeds NKU students on `@nku.edu`, which NKU publishes as its faculty/staff domain;
  students are `@mymail.nku.edu`. Already recorded in
  [`school-domains.md`](school-domains.md) §2.
- `StudentRepository.findByEmail` uses `queryForObject`, which throws on a second match —
  so the `student.email` unique constraint is load-bearing for correctness, not just
  hygiene. Worth keeping in mind when the email path is finally removed.
