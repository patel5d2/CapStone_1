# S1-01 / D-SCHOOLS — Institutional domain evidence and the ADR-014 ballot

**Prepared 2026-09-18 for the weekly meeting. Nothing here is decided.**
This is the evidence pack and ballot for [S1-01](../phase-0/backlog.md#s1-01--prepare-institutional-eligibility-and-school-mapping-decisions-2-points)
and the `D-SCHOOLS` gate. The vote belongs to the team under Team Rule 8; record the
outcome in the minutes and in the [decision log](../../context/5_progress.md#architecture-decision-log).
Domain research does not change the Clerk instance or the admission policy, and this
document changes neither.

It unblocks S1-03 (verified domain-to-school registration) and S2-04 (automatic school
theming), both of which are blocked on `D-SCHOOLS`, not on more research.

---

## 1. Candidate institutions and their student email domains

Every row was read from a page published by the institution itself. Where the
institution publishes a *student* address domain that differs from its
faculty/staff domain, both are listed — that difference is the whole reason this
list exists.

| # | Institution | Student address domain | Other domains seen | Evidence | Confidence |
|---|---|---|---|---|---|
| 1 | University of Cincinnati | `mail.uc.edu` | `ucmail.uc.edu` (faculty/staff), `uc.edu` (institutional web/marketing) | [UC news, Office 365 migration](https://www.uc.edu/news/articles/2019/03/n2077021.html): sign in as "username@mail.uc.edu (for students)" or "username@ucmail.uc.edu (for faculty/staff)"; [UCIT Get Started](https://www.uc.edu/about/ucit/help/get-started.html) confirms `mail.uc.edu` as the student mail host | High — but the citation is from 2019; re-confirm with UCIT before launch |
| 2 | Northern Kentucky University | `mymail.nku.edu` | `nku.edu` (faculty/staff) | [NKU IT FAQs](https://www.nku.edu/it/resources/faqs/index.html): "Your email address is composed of your username then '@nku' (for faculty and staff) or '@mymail.nku.edu' (for students)" | High |
| 3 | Xavier University | `xavier.edu` | none published | [Xavier Technology Account Setup guide (PDF)](https://www.xavier.edu/family-hub/documents/account-setup.pdf): "Your Xavier email address is your username + @xavier.edu." | High |
| 4 | Miami University | `miamioh.edu` | `muohio.edu` (legacy, **unverified**) | [Student Email Policy](https://miamioh.edu/policy-library/students/undergraduate/academic-regulations/student-email-policy.html): the official address is "UniqueID@MiamiOH.edu" | High for `miamioh.edu`; the legacy alias is not confirmed by any Miami page read here |
| 5 | Cincinnati State Technical and Community College | `cincinnatistate.edu` | none published | [ITS — Get Started with SurgeMail (PDF)](https://web4.cincinnatistate.edu/files/its/projects/office365/Get%20Started%20with%20SurgeMail.pdf) and [ITS — Ask IT FAQs (PDF)](https://web4.cincinnatistate.edu/files/swebapps/docs/askit/ITS-AskITQuestionsandAnswers.pdf): the mailbox ("SurgeMail") is created as `username@cincinnatistate.edu` | Medium-high — the PDFs are ITS-published, but both are scanned/encoded and were read through search extraction rather than rendered text. Confirm with the Technology Helpdesk (513-569-1234 opt. 1) |
| 6 | Thomas More University | `thomasmore.edu` | none published | [MyTMU IT Services — Email Accounts](https://mytmu.thomasmore.edu/ICS/IT_Services/Home.jnz?portlet=Free-form_Content_3): Thomas More email is Office 365 with `realm=thomasmore.edu`; the [new student checklist](https://www.thomasmore.edu/wp-content/uploads/New-Student-Checklist-6-1.pdf) gives the address as username@thomasmore.edu | Medium-high — the checklist PDF is image-based and was read through search extraction |
| 7 | Mount St. Joseph University | **not confirmed** (`msj.edu` is the institutional domain) | `msj.edu` on every published staff address (`iss.helpdesk@msj.edu`, `library@msj.edu`, `Registrar@msj.edu`) | [myMount Technology Guide](https://mymount.msj.edu/ICS/Technology/) names "Mount email" but never states the student address domain; no MSJ page read here publishes it | **Low — do not put this school in a mapping table until ISS confirms.** One call to 513-244-4357 settles it |

**Six institutions have a confirmed student domain (rows 1–6). Mount St. Joseph is a
seventh candidate whose domain is an open item, not a guess.** The plan in
[1_overview.md](../../context/1_overview.md#schools) names Mount St. Joseph as one of
its six; if the team wants that list exactly, row 7 has to be confirmed first.

## 2. Findings that change the list

**Cincinnati Christian University closed. It cannot be a supported school.** Its board
voted on 28 October 2019 to shut down degree programs at the end of the fall 2019
semester and withdrew from the Higher Learning Commission after being placed on
show-cause status — [WCPO](https://www.wcpo.com/news/local-news/hamilton-county/cincinnati/cincinnati-christian-university-decides-not-to-fight-for-accreditation-will-close-in-2020),
[Chronicle of Higher Education](https://www.chronicle.com/article/cincinnati-christian-u-will-shut-its-doors/),
[Inside Higher Ed](https://www.insidehighered.com/quicktakes/2019/08/16/conflicts-threaten-accreditation-cincinnati-christian).
It has no enrolled students and issues no `ccuniversity.edu` addresses. This is
evidence for Open Question 4, which asked whether Thomas More and Cincinnati Christian
are in or out: **Thomas More is a live institution with a confirmed domain; Cincinnati
Christian is not an institution any more.** The team still votes; the question is no
longer symmetric.

Consequences, none of them in this story's scope:
- `V2__seed_schools.sql` has been applied and must not be edited. Removing the row
  needs a new migration, and `V3__seed_demo_students.sql` seeds three fabricated
  students on `ccuniversity.edu` that reference it. Open Question 9 already commits to
  removing the demo students by a later migration; this is the same migration.
- Eight seed rows were never evidence of eligibility. Seven candidates, one of them
  unconfirmed, is what the evidence actually supports.

**The demo seed contradicts NKU's published domain.** `V3__seed_demo_students.sql`
puts its four NKU students on `@nku.edu`, which NKU publishes as the *faculty and
staff* domain; real NKU students are on `@mymail.nku.edu`. Demo data is not evidence
either way, but once a domain-to-school map exists, those four rows will either map to
nothing or force `nku.edu` into the map for the wrong reason. Fix it in the same
migration that drops the closed school, not by editing an applied one.

**UC is the only candidate whose student domain is a subdomain of a domain it also
uses for staff.** Any implementation that matches on "ends with `uc.edu`" will admit
`ucmail.uc.edu` too. That may be fine; it should be a decision rather than an accident.

## 3. ADR-014 — what is actually being voted on

The decision log currently records ADR-014 as *Proposed: any `.edu`*, and
`InstitutionalAccessPolicy` already implements that (it grants `ROLE_STUDENT` to any
address whose domain ends in `.edu`). **Merged code is not a vote (Rule 8); the vote is
still open.**

**These are two separable questions, and conflating them is the trap.**

- **Admission breadth** — whose token may use the API at all. That is ADR-014.
- **School mapping** — which school a verified address belongs to, for the directory,
  filters and theming (objectives 2, 5 and 8). **A mapping table is required either
  way.** Voting "any `.edu`" does not remove the need for the table in section 1; it
  only decides what happens to an address that is not in it.

### Option A — regional allowlist of named schools

Only addresses on a domain in the mapping table are admitted.

- Matches "verified, regional campus marketplace" and the product thesis of regional
  density; an unsupported school never reaches a half-working experience.
- Every school added or re-domained is a data change and a deploy or admin action. The
  registrar of a supported school issuing a new subdomain (UC has `mail.` and `ucmail.`;
  NKU has `mymail.`) locks out real students until someone notices.
- Objective 1 asks for a verified institutional email, not a verified *listed* school,
  so this is stricter than the contract requires.
- Objective 2 asks that a student from any of ≥6 schools registers with **no admin
  setup**. An allowlist satisfies that for listed schools and fails it for everyone
  else by design.

### Option B — any `.edu` (what the code does today)

Any address on a `.edu` domain is admitted; the mapping table only assigns a school.

- No data change when a registrar moves a domain; no student is locked out by an
  omission from our list.
- Admits every `.edu` in the world, including schools nowhere near Cincinnati, which
  weakens the regional thesis and leaves those students with no school, no theme and a
  directory they do not belong in.
- `.edu` is US-only and registry-restricted, so it is a meaningful institutional
  signal, but it is not a *regional* one.

### Option C — any `.edu` admits, mapping decides the experience

Admission as in B. A verified address whose domain is in the table gets its school,
theming and school-scoped surfaces. One that is not gets in with school unset, the base
palette, and an explicit "your school is not supported yet" state.

- Keeps objective 2's "no admin setup" true for supported and unsupported schools alike,
  and keeps objective 8 well-defined (theming is only claimed for mapped schools).
- Costs an unmapped-school UI state that A and B do not need, and leaves a directory
  containing students from schools the product does not serve.

**The trade-off in one line:** A needs a data change every time the school list moves;
B admits any `.edu` in the world; C admits them but stops pretending they have a school.

### The list is expected to grow — treat it as a constraint, not a footnote

The team intends to add schools after launch. That is not a hypothetical maintenance
cost to be weighed against the options; it is a stated requirement, and it fixes one
thing regardless of which option wins:

**Adding a school must be a data change — rows, entered by an admin — never a code
change, a migration written by hand, or a redeploy.** A hard-coded list fails that on
the first addition, and fails objective 2's "no admin setup" the first time a registrar
introduces a new subdomain.

It also sharpens the ballot. Option A's cost is *per addition*: until the row exists,
every student at that school is locked out, so onboarding a school is a release-shaped
event. Under B and C the same student can already sign in; adding the row upgrades them
from "no school" to themed and school-scoped, so onboarding is additive and nothing is
urgent. **If the list is going to move often, A is the option that pays for it each
time.**

The admin surface for this already exists in the plan — "manage schools and categories"
in Sprint 11 — so §4's mapping table is what that screen will edit. Designing the table
for hand-editing now means rebuilding it then.

## 4. Unsupported and ambiguous domains — behavior for S1-03

Concrete enough for S1-03 to implement, and branching only where the vote genuinely
changes the answer. The response shape is the existing `ExceptionWrapper`
(`{status, message, path}`) produced by `InstitutionalAccessDeniedHandler`; these are
proposed additions to it, not current behavior.

| Case | Example | Status | Body message |
|---|---|---|---|
| No token, or a token that fails JWKS validation | — | **401** | unchanged (Spring's entry point) |
| Token's email is not on a `.edu` domain | `someone@gmail.com` | **403** | existing `NOT_INSTITUTIONAL_MESSAGE` — unchanged |
| Token's email is not verified by Clerk | `student@uc.edu`, unverified | **403** | "Verify your school email address with your school, then sign in again." |
| Lookalike domain | `student@uc.edu.example.com` | **403** | as not-institutional — already handled by the suffix check, keep the test |
| Verified `.edu`, domain **not** in the mapping table | `student@stanford.edu` | **A: 403** — "CampusBridge supports schools in the Cincinnati area. We do not support *stanford.edu* yet." · **B/C: 200**, school unset | name the domain and offer a route to ask for it; never a bare 403 |
| Verified `.edu`, domain maps to **more than one** school | a shared or ambiguous domain | **200** with school unset **and** a prompt to pick from the matching schools | "We could not tell which school *example.edu* belongs to. Choose yours." Never guess, and never pick the first row |
| Verified `.edu`, domain maps to exactly one school | `student@mymail.nku.edu` | **200** | school assigned, no prompt, no admin step |

Notes for whoever implements S1-03:

- Match on the **whole domain**, not a suffix, or `mail.uc.edu` and `ucmail.uc.edu`
  collapse into one rule and `uc.edu.example.com` gets a second chance at passing.
- The map has to be **data, not a constant in code**, or objective 2's "no admin setup"
  is false the first time a domain changes. That means a table keyed by domain with
  a school foreign key, added in its own Flyway migration under S1-03 — not this story.
- **More schools are coming.** Adding one is inserting a school row plus one row per
  domain, through the admin surface, with no deploy and no code edit. Nothing may read
  the school set at startup and cache it for the process lifetime, and no enum, constant
  or `switch` may name a school — that includes the theme palettes S2-04 will add, which
  otherwise become the second hard-coded list to update.
- One domain must be able to map to one school and one school to several domains
  (UC alone has two; NKU has two if staff are ever admitted), and a school must be able
  to gain a domain later without touching the rows already there.
- Whatever is decided, it is enforced server-side on every protected route, as
  `InstitutionalAccessPolicy` already does. The SPA gate decides what is *shown* and
  proves nothing.

## 5. The ballot

Three votes, in this order. Each needs a majority and a line in the minutes.

1. **Supported school list.** The six confirmed institutions in section 1, plus Mount
   St. Joseph conditional on ISS confirming its student domain, minus Cincinnati
   Christian University (closed). Resolves Open Question 4.
2. **ADR-014 — admission breadth.** A, B or C. The code currently does B; a vote for A
   or C is a change request against `InstitutionalAccessPolicy`, not a bug report.
3. **Unsupported-domain behavior.** Section 4 as written, with the branch chosen by
   vote 2.

## 6. What this document does not do

- It does not change the Clerk instance, `InstitutionalAccessPolicy`, the schema or the
  seed data. No code or migration is touched by this story.
- It does not record a decision. ADR-014 stays *Proposed* until the minutes say
  otherwise, and a merged PR containing this file is not a vote.
- It does not confirm Mount St. Joseph's student domain, or re-confirm UC's 2019
  citation. Both need a human to ask the institution.
- It does not verify any domain against a live token. That evidence belongs to S1-03,
  which must exercise registration at six institutions for real.
