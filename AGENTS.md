# CampusBridge — Agent Entry Point

**CampusBridge** is a verified, regional campus marketplace for the Cincinnati metro
area, with community features that sustain it between transactions. Spring Boot 4 +
React 19, one deployable, Clerk for identity.

**The signed Senior Design Team Contract governs requirements.** Its recorded objectives
are in `1_overview.md`; the signed document and approval minutes are not checked in here.
Source, dependency manifests and configuration establish what is implemented today.
Keep requirements, current behavior and proposals distinct: document a gap without
lowering the requirement or claiming the missing behavior exists.

---

## Read before doing anything

Read `5_progress.md` first, then the remaining five context files before editing code
or making architectural decisions. Reading files and inspecting repository state are
part of this preparation:

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

Work the current sprint unless the user's task explicitly directs otherwise. An audit
may inspect later features without approving their scope or marking them complete.

---

## Hard constraints

These are the ones that cause real damage if missed. Full detail lives in the files above.

- **Follow the invariants** in `2_architecture.md`. They are requirements, with known
  implementation gaps. Authenticate the actor, enforce the action's authorization,
  parameterise SQL values, and implement schema work through the planned migration task.
- **Check the Status column** in `2_architecture.md` before importing anything.
  `PLANNED` means it does not exist yet — add the dependency in its own task first.
- **Never edit `src/main/resources/static/**`.** It is Vite build output and
  `emptyOutDir: true` erases it on the next build. Change `frontend/src/` instead.
  The rest of the protected paths are in `6_rules.md`.
- **Never commit a secret.** Configuration comes from environment variables.
- **Use `6_rules.md` as the canonical approval policy.** A planned dependency is not
  installed or automatically approved. Honor authorization already given for the task;
  ask only for decisions or actions outside it. Never push directly to `main`.
- **Anything in Open Questions** (`5_progress.md`) needs a human decision, not a guess.

## Verify before claiming done

Run what the change touches and **look at the output**. Commands and known limitations
are in `6_rules.md`. Documentation-only edits need link, command and consistency checks;
they do not prove application behavior.

```bash
./mvnw --batch-mode verify                      # backend
cd frontend && npm run lint && npm run build    # frontend
docker compose --env-file .env.example config --quiet # config preflight; currently fails
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
