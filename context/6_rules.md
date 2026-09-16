# 6. Rules — Team Agreements & Agent Constraints

> Two audiences, one file. **Part A** is the team's signed contract rules and sprint
> process — binding on every member. **Part B** constrains AI coding agents. An agent
> must satisfy both.

---

# Part A — Team rules

From the Senior Design Team Contract, signed 02 Sep 2026.

## Roles

| Member | Role |
|---|---|
| Jon Soriano Sanjuan | Scrum Master & Developer |
| Dharmin Patel | **DevOps & QA** |
| Matthew Brown | Developer |
| Shamak Patel | Developer |
| Jessica Pham | Security & UI/UX |

## Ways of working

- **Every change goes through a pull request.** No member pushes directly to `main`, and
  every PR needs review and approval from at least one other member. *(Rule 5)*
  → Commits `2d7ec86`–`c0c21d6` went straight to `main`. Enable branch protection (S0-8).
- **Review the code, never the person.** Disagreements are settled in the review, then by
  team vote. *(Rule 6)*
- **Test your own work before requesting review.** A PR that breaks the build is the
  author's to fix. *(Rule 7)*
- **Scope, architecture and schedule decisions are made by majority vote** and recorded in
  the meeting minutes. The Scrum Master facilitates a deadlock; the faculty advisor is the
  escalation. *(Rule 8)* → ADRs in `5_progress.md` are *Proposed* until voted.
- **Never commit credentials, connection strings or API keys.** Configuration comes from
  environment variables. *(Rule 9)*
- **Raise a blocker within one working day.** Asking early is expected, not penalised. *(Rule 10)*
- **Everyone writes documentation.** It is not one person's job. *(Rule 11)*
- **Weekly meeting at a fixed time.** If you cannot attend, send a written status update
  beforehand. *(Rule 4)*
- Notify the team **24 hours ahead** if something will delay a deliverable. *(Rule 2)*

## Sprint ceremonies

Two-week sprints, Monday to Friday of the following week.

- **Monday, week 1** — sprint planning: pick stories, estimate, assign
- **Twice weekly** — stand-up: done, next, blocked
- **Friday, week 2** — sprint review (demo) and retrospective
- **Ongoing** — the Scrum Master keeps GitHub Projects current

## Definition of Done

A story is not done until **all seven** hold:

1. Code reviewed and approved by another member
2. Merged into `main` through a pull request
3. Tests pass in CI
4. Works in Chrome, Safari and Firefox
5. Works at phone screen width
6. Usable with a keyboard alone
7. Shown in the sprint review demo

---

# Part B — Agent constraints

## Read before writing

1. Read `context/1_overview.md` … `context/6_rules.md` before generating code, running a
   command, or making an architectural decision.
2. Read `5_progress.md` first for the current sprint, the blocker and open questions.
   Work the current sprint — do not jump ahead.
3. Build only what the **contract objectives** in `1_overview.md` describe. The contract
   is the source of truth; the code is not.
4. Check the Status column in `2_architecture.md` before importing anything. `PLANNED`
   means **it does not exist yet**.
5. Never violate an invariant in `2_architecture.md`. They map to graded objectives.

## Ask before doing

- **Adding any dependency** beyond those marked `PLANNED`.
- **Installing a UI component library** — the answer is no; `4_ui_design.md` is the system.
- **Adding infrastructure** — Kubernetes, Redis, Kafka, a queue, another service.
- **Changing Clerk instance configuration.** Never remove the `email` session claim
  (ADR-004), never disable device trust or the password policy, and never turn 2FA back
  off once Sprint 1 enables it. Weakening the security posture is the team's decision,
  and objective 1 is graded on it.
- **Changing the database schema outside a migration.** Never edit an applied migration.
- **Anything in the Open Questions list** in `5_progress.md`.
- **`git commit`, `git push`, opening a PR, publishing an image, or a release.**
- **Deleting or rewriting a file you have not read in full.**
- **Anything touching `.env`** — never read it back, print it, or commit it.

## Protected paths — do not edit

| Path | Why |
|---|---|
| `src/main/resources/static/**` | **Vite build output.** `vite.config.ts` sets `emptyOutDir: true`, so edits here are silently erased on the next build. Change `frontend/src/` instead. |
| `db/migration/V*.sql` once applied | Flyway checksums them; edits break every existing database. Add a new migration. |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/**` | Generated Maven wrapper |
| `frontend/package-lock.json` | Regenerate with `npm install`, never hand-edit |
| `target/**`, `frontend/node_modules/**` | Build artifacts |

## Never

- Never put a secret in client code, a log line, a test fixture, or a committed file.
- Never trust an identity from a request body — use the verified Clerk JWT.
- Never return a JPA entity from a controller.
- Never expose an email address from a public-facing endpoint (invariant 4).
- Never concatenate SQL (invariant 6).
- Never add a `Co-Authored-By` trailer unless `.claude/settings.json` sets
  `attribution.commit`.
- Never save scratch files to the repo root.
- Never disable, skip or delete a failing test to make a build pass.
- Never reformat files unrelated to the task.

## Verification — before marking anything complete

Run what the change touches. Not complete until these pass **and you have seen the output**.

**Backend**
```bash
./mvnw --batch-mode verify
```

**Frontend**
```bash
cd frontend && npm run lint && npm run build
```

**Schema, config, Docker or profile** — catches the Sprint 0 class of bug:
```bash
docker compose up -d --build
curl -fsS http://localhost:8080/actuator/health    # must be {"status":"UP"}
docker compose logs app | tail -40                 # no crash, no restart loop
docker compose down
```

**Auth or endpoint** — prove it with a real token, never by inspection:
```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/schools          # 401
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/schools                                              # 200
```
Mint tokens with the Clerk CLI (`clerk api /sessions/<id>/tokens`). Delete test users.

**UI** — state which routes you checked and at what width. Per the Definition of Done:
Chrome, Safari and Firefox; phone width; keyboard-only. If you cannot open a browser,
say so rather than implying you verified it.

## Reporting

- Report honestly. If tests fail, show the output. If you skipped a step, say which.
- Distinguish *"this compiles"* from *"I ran it and saw it work."*
- If a context file turns out to be wrong, fix it in the same change and note it in
  `5_progress.md`.

## On failure — diagnose, don't thrash

1. Read the error and full stack trace before changing anything.
2. Find the root cause. Grep every caller of the function you are about to touch — one
   guard in a shared function beats a guard in each caller.
3. Apply the smallest fix that addresses the cause, not the symptom.
4. Re-run the verification commands.
5. Record the cause and fix in `5_progress.md`.

Do not retry a failing command unchanged, and do not work around a denied permission —
report it and let the team decide.

## Keep progress current

When a story finishes, update `5_progress.md` in the same change: tick the checkbox,
update the objectives scoreboard if it moved, append new decisions as *Proposed*, and add
anything unresolved to Open Questions. Respect the merge etiquette note at the top of
that file — five people edit it.
