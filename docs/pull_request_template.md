
# Pull Request

## Problem and change

<!-- State the problem, resulting behavior and any material limitations. -->

## Related work and decisions

<!-- Link issues and the relevant sprint task. For scope, architecture or schedule
changes, link the recorded team majority decision; a proposal is not approval. -->

## Verification evidence

<!-- Record commands, results and relevant CI run links. Say "not run" with a reason
for missing checks; use N/A only when a check does not apply. Never mark an unrun
check as passed. For documentation-only changes, inspect links, commands and claims
against the source; code tests are not required solely for prose changes. -->

| Check | Result / evidence / reason not run |
|---|---|
| Backend: `./mvnw --batch-mode verify` | |
| Frontend: `npm ci`, `npm run lint`, `npm run build` from `frontend/` | |
| Schema/config/container: isolated startup, health and logs | |
| Auth/API: authorized and unauthorized behavior; ownership checks where changed | |
| UI: routes, Chrome/Safari/Firefox, phone width, keyboard-only operation | |
| Documentation: links, commands, implementation/proposal status | |

<!-- Never paste tokens, credentials, private data or environment-file contents.
Use the scoped verification guidance in context/6_rules.md. -->

The [current CI workflow](../.github/workflows/main.yml) runs Maven verification,
frontend lint/type-check/build, and an amd64/arm64 container build. It does not start
the container, test PostgreSQL startup, run browser tests or enforce a coverage
threshold. The [release workflow](../.github/workflows/release.yml) scans published
images with Trivy, but findings do not fail that scan step. A passing build is not
evidence that these other requirements passed.

## Author checklist

- [ ] I reviewed the diff and updated affected documentation and progress entries.
- [ ] I tested the affected behavior and recorded any missing verification above.
- [ ] I checked the change against the recorded scope, security rules and protected paths.
- [ ] I documented relevant risks, follow-up work and proposed decisions.

## Story closure — record when evidence exists

<!-- These are the seven team Definition of Done requirements, not an author
self-approval checklist. Leave review, merge or demo items open until they happen.
For a non-UI story, record why browser/phone/keyboard items do not apply rather than
claiming to have tested them. See context/6_rules.md for the authoritative rules. -->

- [ ] Another team member reviewed and approved the change (link review).
- [ ] The change merged into `main` through a pull request (link merge).
- [ ] Tests passed in CI (link run).
- [ ] Chrome, Safari and Firefox evidence is recorded, or non-UI applicability explained.
- [ ] Phone-width evidence is recorded, or non-UI applicability explained.
- [ ] Keyboard-only evidence is recorded, or non-UI applicability explained.
- [ ] The story was shown in the sprint review demo (link/date).

## Risks and follow-up

<!-- Include unresolved gaps and affected deployment/data considerations, if any. -->
