# CampusBridge — Agent Entry Point

**CampusBridge** is a verified, regional campus marketplace for the Cincinnati metro
area, with community features that sustain it between transactions. Spring Boot 4 +
React 19, one deployable, Clerk for identity.

**The signed Senior Design Team Contract is the source of truth.** `context/` is that
contract expressed for builders. When the code and `context/` disagree, the code is
wrong. Do not infer requirements from the codebase — it has drifted before.

---

## Read before doing anything

Read all six, in this order, before generating code, running a command, or making an
architectural decision:

1. **[`1_overview.md`](1_overview.md)** — product, roles, four tabs, user flows, scope,
   and the **11 graded success criteria**
2. **[`2_architecture.md`](2_architecture.md)** — stack with build status, **invariants**,
   database schema, data flows, environment variables, compliance obligations
3. **[`3_patterns.md`](3_patterns.md)** — directory structure, OWASP authorization and
   injection rules, error handling, logging, naming
4. **[`4_ui_design.md`](4_ui_design.md)** — design tokens, four-tab navigation, school
   theming, WCAG 2.1 AA
5. **[`5_progress.md`](5_progress.md)** — **current sprint**, the blocker, objective
   scoreboard, decision log, open questions
6. **[`6_rules.md`](6_rules.md)** — team rules, Definition of Done, agent constraints,
   protected paths, verification commands

**Start every session at `5_progress.md`.** It names the current sprint and the active
blocker. Work the current sprint; do not jump ahead.

---

## Hard constraints

These are the ones that cause real damage if missed. Full detail lives in the files above.

- **Never violate an invariant** in `2_architecture.md`. All eleven map to graded
  objectives. Identity comes only from a verified Clerk JWT; every mutation checks
  ownership; all SQL is parameterised; schema changes only via migration.
- **Check the Status column** in `2_architecture.md` before importing anything.
  `PLANNED` means it does not exist yet — add the dependency in its own task first.
- **Never edit `src/main/resources/static/**`.** It is Vite build output and
  `emptyOutDir: true` erases it on the next build. Change `frontend/src/` instead.
  The rest of the protected paths are in `6_rules.md`.
- **Never commit a secret.** Configuration comes from environment variables.
- **Ask before** adding a dependency, changing Clerk instance config, changing the
  schema outside a migration, or committing, pushing, or opening a PR.
- **Anything in Open Questions** (`5_progress.md`) needs a human decision, not a guess.

## Verify before claiming done

Run what the change touches and **look at the output**. Commands are in `6_rules.md`.

```bash
./mvnw --batch-mode verify                      # backend
cd frontend && npm run lint && npm run build    # frontend
docker compose up -d && curl -fsS localhost:8080/actuator/health   # schema/config/Docker
```

Report honestly: distinguish *"this compiles"* from *"I ran it and saw it work."* If you
skipped a step, say which.

## Update as you go

When a story finishes, update **`5_progress.md`** in the same change: tick the checkbox,
move the objective scoreboard if it shifted, append any new decision to the log as
**Proposed**, and add anything unresolved to Open Questions.

If implementation changes the architecture, scope, patterns or design system, **update
that context file before continuing** — a stale context file is worse than none.

`5_progress.md` is edited by all five team members. Touch only your sprint's checkboxes
and append to logs; never reflow or reorder sections.

## Team rules that bind agents

- **Every change goes through a pull request.** Never push to `main`.
- **Scope and architecture decisions need a team majority vote** (Rule 8). Decisions you
  propose are recorded as *Proposed* until voted — never as settled.
- A story is not done until all seven Definition of Done items hold (`6_rules.md`).
