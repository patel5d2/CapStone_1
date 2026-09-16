# 6. Rules — Agent Behavioural Constraints

> These are execution limits, not style preferences. Conventions live in
> `3_patterns.md`; this file governs what you may *do*.

## Read before writing

1. Read all of `context/1_overview.md` … `context/6_rules.md` before generating code,
   running a command, or making an architectural decision.
2. Read `context/5_progress.md` first to find the current phase, the active task, and
   decisions already made. Work the tasks **in order** — Phase A blocks everything.
3. Build only what `1_overview.md` and `2_architecture.md` describe. If a request
   falls outside that scope, say so and ask rather than inventing the feature.
4. Check the Status column in `2_architecture.md` before importing anything. A
   `PLANNED` dependency **does not exist yet**; add it in its own step first.

## Ask before doing

Stop and ask; do not proceed on assumption:

- **Adding any dependency** — a Maven artifact or an npm package — beyond those marked
  `PLANNED` in `2_architecture.md`.
- **Installing a UI component library** (shadcn/ui, MUI, Radix, …). The answer is no;
  `4_ui_design.md` defines the system.
- **Adding infrastructure** — Kubernetes, Redis, Kafka, a message queue, a second
  service, another container. ADR-010 already declined these.
- **Changing Clerk instance configuration.** In particular, never remove the `email`
  session claim (ADR-004) and never disable device trust or lower the password policy.
  Weakening the security posture is the user's decision, not yours.
- **Changing the database schema outside a migration.** Once Flyway lands (A1), schema
  changes are new `V<n>__*.sql` files. Never edit an applied migration.
- **`git commit`, `git push`, opening a PR, publishing an image, or a release.** Only
  on explicit instruction.
- **Deleting or rewriting a file you have not read in full.**
- **Anything touching `.env`.** Never read it back, never print its contents, never
  commit it. Ask for non-secret values you need.

## Never

- Never put `CLERK_SECRET_KEY` — or any secret — in client code, a log line, a test
  fixture, or a committed file. Publishable keys are public; secret keys are not.
- Never trust an identity from a request body. Use the verified Clerk JWT
  (`3_patterns.md`).
- Never return a JPA entity from a controller.
- Never add a `Co-Authored-By` trailer to a commit unless the repo's
  `.claude/settings.json` sets `attribution.commit`.
- Never save working files, scratch scripts or notes to the repo root.
- Never disable, skip, or delete a failing test to make a build pass. Fix the cause,
  or report it.
- Never reformat or "clean up" files unrelated to the task.

## Verification — required before marking a task complete

Run what the change actually touches. A task is **not** complete until these pass and
you have seen the output.

**Backend change**
```bash
./mvnw --batch-mode verify        # compiles, runs all tests, writes the JaCoCo report
```

**Frontend change**
```bash
cd frontend && npm run lint && npm run build   # oxlint, then tsc -b + vite build
```

**Schema, config, Docker or profile change** — the one that catches the Phase A class
of bug:
```bash
docker compose up -d --build
curl -fsS http://localhost:8080/actuator/health   # must return {"status":"UP"}
docker compose logs app | tail -40                # must show no crash / restart loop
docker compose down
```

**Auth or endpoint change** — prove it with a real token, not by inspection:
```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/schools        # expect 401
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/schools                                            # expect 200
```
Mint a token with the Clerk CLI (`clerk api /sessions/<id>/tokens`). Delete any test
user you create.

**UI change** — state which routes you checked and at what width. If you cannot open a
browser, say so instead of implying you verified it.

## Reporting

- Report outcomes honestly. If tests fail, show the output. If you skipped a step, say
  which and why. Do not describe work as verified when it was only written.
- Distinguish *"this compiles"* from *"I ran it and saw it work."*
- If a claim in a context file turns out to be wrong, fix the file in the same change
  and note it in `5_progress.md`.

## On failure — diagnose, don't thrash

1. Read the actual error and the full stack trace before changing anything.
2. Find the root cause. Grep every caller of the function you are about to touch: one
   guard in a shared function beats a guard in each caller, and patching only the path
   in the report leaves its siblings broken.
3. Apply the smallest fix that addresses the cause, not the symptom.
4. Re-run the verification commands above.
5. Record the cause and the fix in `5_progress.md`.

Do not retry a failing command unchanged, and do not work around a denied permission —
report it and let the user decide.

## Keep progress current

When a sub-task or feature finishes, update `context/5_progress.md` in the same change:
tick the task, move completed groups down with the date and commit, and append any new
decision to the log with its rationale. A reversed decision keeps its row with an
updated Status.

`5_progress.md` is your memory between sessions. If it is stale, the next session
repeats work or breaks something already built.
