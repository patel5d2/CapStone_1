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
  Branch protection is a Sprint 0 action (S0-8); its live setting is not verifiable
  from the checkout. Commit history alone does not prove whether review occurred.
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

1. Read `context/5_progress.md` first, then `1_overview.md`, `2_architecture.md`,
   `3_patterns.md`, `4_ui_design.md` and this file before editing code or making an
   architectural decision. Read-only inspection is part of that preparation.
2. Work the current sprint unless the user's task explicitly directs otherwise.
   Auditing later features does not approve proposed scope or mark it complete.
3. The signed contract governs requirements; source, manifests and configuration
   establish current implementation. The signed document and vote minutes are not in
   this repository. Preserve the recorded objectives, distinguish proposals, and
   report implementation gaps rather than changing requirements to match bugs.
4. Check the Status column in `2_architecture.md` before importing anything. `PLANNED`
   means **it does not exist yet**.
5. Never violate an invariant in `2_architecture.md`. They map to graded objectives.

## Authorization and decisions

Apply the user's authorization already given for the task. Routine, reversible edits,
read-only checks and verification within that scope do not need repeated permission.
Ask when a required decision is unresolved or the following work is not authorized:

- **Adding a dependency.** `PLANNED` describes a roadmap item, not approval or an
  installed package. Add approved dependencies in their own implementation task.
- **Changing the UI component approach.** `4_ui_design.md` uses shared local primitives;
  a component library would be an architecture decision under Team Rule 8.
- **Adding infrastructure** — Kubernetes, Redis, Kafka, a queue, another service.
- **Changing Clerk instance configuration.** Never remove the `email` session claim
  (ADR-004), never disable device trust or the password policy, and never turn 2FA back
  off once Sprint 1 enables it. Weakening the security posture is the team's decision,
  and objective 1 is graded on it.
- **Changing the database schema.** Flyway is not installed yet. Complete the baseline
  task before feature schema changes; do not invent an unmanaged workaround or edit an
  applied migration. Existing H2 scripts and `ddl-auto: update` are documented debt.
- **Anything in the Open Questions list** in `5_progress.md`.
- **`git commit`, `git push`, opening a PR, publishing an image, or a release.**
- Read a file fully before deleting or rewriting it; asking is not a substitute.
- **Accessing or changing real `.env` files or credentials.** Do not print or commit
  secrets. Checked-in `.env.example` templates may be read and maintained; public
  Clerk publishable keys are not secret keys. Avoid printing resolved configuration.

## Protected paths — do not edit

| Path | Why |
|---|---|
| `src/main/resources/static/**` | **Vite build output.** `vite.config.ts` sets `emptyOutDir: true`, so edits here are silently erased on the next build. Change `frontend/src/` instead. |
| `src/main/resources/db/migration/V*.sql` once applied (planned) | Default classpath Flyway location; respect any explicitly configured alternative too. Never edit applied migrations; add a new one. |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/**` | Generated Maven wrapper |
| `frontend/package-lock.json` | Regenerate with `npm install`, never hand-edit |
| `target/**`, `frontend/node_modules/**` | Build artifacts |

## Never

- Never put a secret in client code, a log line, a test fixture, or a committed file.
- Never trust an identity from a request body — use the verified Clerk JWT. Current
  services use its custom `email` claim; Clerk `sub` keying is the proposed migration.
- Never return a JPA entity from a controller.
- Never expose another user's personal contact details (invariant 4); email-bearing
  responses are existing gaps, not a pattern to extend.
- Never interpolate untrusted values or identifiers into SQL. Bind values; select
  structural clauses only from fixed, trusted code (invariant 6).
- Never add a `Co-Authored-By` trailer unless `.claude/settings.json` sets
  `attribution.commit`.
- Never save scratch files to the repo root.
- Never disable, skip or delete a failing test to make a build pass.
- Never reformat files unrelated to the task.

## Verification — before marking anything complete

Run what the change touches and inspect the output. Record failures and limitations;
an unrelated existing failure must not become an invented passing result. Local
verification does not replace the seven team Definition of Done requirements.

**Documentation only** — check relative links, referenced paths and commands against
the checkout; run `git diff --check`; reconcile status, rules and requirements across
the entry points. Do not rerun application tests merely to change prose. Date runtime
claims and distinguish historical evidence from checks run in this session.

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
docker compose --env-file .env.example config --quiet
# After preflight passes, using a configured local environment:
docker compose up -d --build
curl -fsS http://localhost:8080/actuator/health    # must be {"status":"UP"}
docker compose logs app | tail -40                 # no crash, no restart loop
docker compose down
```

Current preflight fails on the stray `url=...` first line of `docker-compose.yml`.
After correcting that, the missing PostgreSQL legacy schema remains a separate blocker.
Do not use `down -v` on a data-bearing environment. Health success alone does not prove
Prometheus can scrape the authenticated metrics endpoint; check the target separately.

**Auth or endpoint** — prove it with a real token, never by inspection:
```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/schools          # 401
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/schools                                              # 200
```
Use a short-lived session token from an authorized test account in the matching Clerk
instance. Keep it out of shell history, logs and shared output. Use `curl -sS` for
transport diagnostics. Also check ownership and rejected access, not just authentication.
Clean up only disposable test data you created and are authorized to remove.

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

Do not repeat a failing command without new evidence or a changed condition. If the
execution environment requires approval, use its approval mechanism; never bypass a
denial. Keep unrelated user changes intact.

## Keep progress current

When a story finishes, update `5_progress.md` in the same change: tick the checkbox,
update the objectives scoreboard if it moved, append new decisions as *Proposed*, and add
anything unresolved to Open Questions. Respect the merge etiquette note at the top of
that file — five people edit it.
