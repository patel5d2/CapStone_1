# CampusBridge frontend

React 19 + TypeScript + Vite single-page app for CampusBridge. Talks to the
Spring Boot API in the repository root and authenticates with Clerk.

## Setup

```bash
cp .env.example .env     # Clerk publishable key
npm install
```

## Develop

Run the backend in one terminal from the repository root:

```bash
./mvnw spring-boot:run   # http://localhost:8080
```

and the frontend in another:

```bash
npm run dev              # http://localhost:5173
```

Vite proxies `/api` and `/student` to port 8080, so cookies, tokens, and
relative URLs behave the same as in production.

## Build

```bash
npm run build
```

The bundle is written to `../src/main/resources/static`, which Spring Boot
serves directly — so a production run is still a single JAR and a single
container. The Docker image runs this build in its own stage.

## Layout

| Path | Contents |
| --- | --- |
| `src/pages` | One file per route (Marketplace, Messages, Community, Support, Directory, Profile, Landing, auth) |
| `src/components/layout` | `AppShell` — header nav on desktop, bottom tab bar on mobile |
| `src/components/ui` | Shared primitives: `Modal`, `Toast`, `Tabs`, `PageHeader`, loading/empty/error states |
| `src/lib/api.ts` | Typed fetch wrapper that attaches the Clerk session token |
| `src/types` | TypeScript mirrors of the backend DTOs |

Design tokens (colors, shadows, dark mode) live in `src/index.css` as Tailwind
v4 `@theme` variables; shared `.btn`, `.card`, `.field`, and `.badge` classes
are defined there too.
