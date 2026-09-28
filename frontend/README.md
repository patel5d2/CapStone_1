# CampusBridge frontend

React 19 + TypeScript 6 + Vite 8 single-page app for CampusBridge, the Cincinnati-area
campus marketplace. It uses Tailwind CSS 4, React Router 7, Clerk React 5 and Lucide
icons. Spring Boot serves the compiled frontend and the API from one application.

Start with the [project README](../README.md), [agent entry point](../context/AGENT.md)
and [current progress](../context/5_progress.md). The [UI specification](../context/4_ui_design.md)
separates implemented behavior from the target design. This README describes the
source configuration as of 2026-09-16; it is not a browser acceptance-test report.

## Local setup

Use Node **22.12 or newer in the Node 22 line** to match CI and Docker. The locked
Vite and React plugin packages require `^20.19.0 || >=22.12.0`. Use `npm ci` to install
the committed lockfile without selecting new package versions.

From the repository root:

```bash
cd frontend
npm ci
```

Provide `VITE_CLERK_PUBLISHABLE_KEY` through your shell or local frontend environment
configuration. It must be the publishable key for the same Clerk instance configured
by the backend's `CLERK_ISSUER` and `CLERK_JWKS_URI`. The app throws during startup
if the key is absent. Never put a Clerk secret key in a `VITE_*` variable: these values
are bundled into browser assets.
See [Vite's environment variable documentation](https://vite.dev/guide/env-and-mode)
for mode-specific configuration and precedence.

Start the backend in a separate terminal from the repository root, following its
[setup instructions](../README.md):

```bash
./mvnw spring-boot:run
```

Then start Vite from `frontend/`:

```bash
npm run dev
```

Vite requests port **5173** and proxies `/api` and `/student` to
`http://localhost:8080`. Use the URL printed by Vite if that port is occupied.
API requests use relative paths and an explicit bearer token. No separate API base
URL or cookie-based backend session is configured. The documented PostgreSQL
startup blocker still applies; see [Sprint 0](../context/5_progress.md).

## Commands and production output

Run these in `frontend/`:

| Command | Effect |
|---|---|
| `npm run dev` | Vite development server with the API proxies above |
| `npm run lint` | Oxlint source checks |
| `npm run build` | TypeScript project checks (`tsc -b`), then Vite production build |
| `npm run preview` | Locally preview an existing production build; does not start Spring Boot |

The frontend verification sequence is:

```bash
npm run lint
npm run build
```

There is no `test` script or installed frontend test runner yet. Linting and building
do not demonstrate route behavior, keyboard access, responsiveness or authentication.
Use the [team verification requirements](../context/6_rules.md) for acceptance checks.

Vite writes to **`../src/main/resources/static`** with `emptyOutDir: true`, replacing
its contents on every build. Edit `frontend/src/` and `frontend/public/`, never that
generated directory. For a local JAR, build the frontend before Maven packages the
backend. Maven does not run the frontend build itself. The Dockerfile builds the
frontend in a Node stage and copies the output into the Spring Boot JAR.
The output cleanup is explicitly configured; see [Vite's `emptyOutDir` option](https://vite.dev/config/build-options#build-emptyoutdir).

`VITE_CLERK_PUBLISHABLE_KEY` is fixed at build time. Docker accepts it as a build
argument; changing a running container's environment does not change its compiled
frontend. `npm run preview` is a local inspection tool, not the deployment server;
Spring Boot serves production assets and forwards the known client routes to
`index.html`.

## Routes and authentication

| Route | Current behavior |
|---|---|
| `/` | Public landing page |
| `/sign-in/*` | Clerk sign-in; completion redirects to `/marketplace` |
| `/sign-up/*` | Clerk sign-up; completion redirects to `/profile` |
| `/marketplace` | Listing browsing and management |
| `/messages` | Conversations and direct messages |
| `/community` | Posts, groups and events |
| `/support` | Resources and anonymous requests |
| `/directory` | Separate student-directory route in the current shell |
| `/profile` | Directory profile creation and editing |

All feature routes use `RequireAuth`, which waits for Clerk and sends signed-out
visitors to `/sign-in`. The backend independently protects API data. A client route
guard does not prove institutional verification, 2FA, ownership or profile privacy.
Those requirements and outstanding gaps are tracked in [architecture](../context/2_architecture.md)
and [progress](../context/5_progress.md).

`ClerkProvider` is mounted in `src/main.tsx`. `AuthTokenBridge` in `src/App.tsx`
registers Clerk's token getter with `src/lib/authToken.ts`; `src/lib/api.ts` obtains
a token for each request and sets `Authorization: Bearer …`. Use `api.get`,
`api.post`, `api.put` and `api.del` in pages, and `toQueryString` for filters.
Non-success responses throw `ApiError` with a status and message. The current backend
still depends on a custom `email` claim; do not remove it while that identity model
remains in use.

Creating a Clerk account does not yet create a directory row automatically. The
profile page reads `/student/profile`; a `404` switches its first save to
`POST /student`, while later saves use `PUT /student/profile`. Automatic webhook sync
is planned.

## Layout

| Path | Contents |
| --- | --- |
| `src/main.tsx` | Clerk, browser router and toast providers |
| `src/App.tsx` | Routes, `RequireAuth`, `AuthTokenBridge` |
| `src/pages/` | Route screens |
| `src/components/layout/` | `AppShell` — header navigation and signed-in mobile bottom bar |
| `src/components/ui` | Shared primitives: `Modal`, `Toast`, `Tabs`, `PageHeader`, loading/empty/error states |
| `src/hooks/useSchools.ts` | Loads the backend school list |
| `src/lib/api.ts` | Typed fetch wrapper that attaches the Clerk session token |
| `src/types/index.ts` | TypeScript mirrors of the backend DTOs |
| `src/index.css` | Tailwind theme variables, dark-mode overrides and shared classes |
| `public/` | Static assets copied by Vite |
| `vite.config.ts` | Plugins, development proxy, Vite alias and build destination |

Current imports are relative. Vite defines `@` for `src`, but the TypeScript configs
do not define matching `paths`; do not assume an `@/…` import will type-check.

## Current UI limitations

- The shell has five destinations. The four-tab design moves Directory inside
  Community and remains a Sprint 2 target.
- One indigo palette and system-preference dark mode exist. Automatic school themes,
  a theme override and text-size controls are planned.
- Messages poll the inbox every 8 seconds and the active thread every 5 seconds;
  this does not meet the under-2-second real-time messaging target.
- The listing editor accepts one photo URL; image upload and the five-photo flow
  are not implemented.
- Accessibility primitives need further work, including modal focus management,
  tab semantics and announced toast feedback. WCAG conformance has not been established.
