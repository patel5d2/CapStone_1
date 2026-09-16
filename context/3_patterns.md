# 3. Patterns — Code Conventions & Standards

> Match the surrounding code. Where this file and existing code disagree, the
> **newer feature packages** (`marketplace`, `messages`, `community`, `support`) are
> the reference — not the legacy `controller/` + `repository/` + `service/` trio.

## Backend directory structure

Organise **by feature**, not by layer:

```
com.jonathansoriano.enterprisedevgroupproject/
├── <feature>/                  marketplace | messages | community | support | school
│   ├── <Entity>.java           @Entity, @Table(name = "snake_case")
│   ├── <Entity>Repository.java extends JpaRepository<T, Long>
│   ├── <Feature>Service.java   business logic + authorization
│   ├── <Feature>Controller.java thin: parse, delegate, return
│   ├── <Enum>.java             persisted with @Enumerated(EnumType.STRING)
│   └── dto/
│       ├── <X>Request.java     inbound, bean-validated
│       └── <X>Response.java    outbound, never an entity
├── config/                     SecurityConfig, SpaForwardingConfig
├── security/                   CurrentUser
├── exception/                  ExceptionTranslator, ExceptionWrapper, domain exceptions
├── seed/                       ApplicationRunner seeders
└── util/                       SqlUtils  ← legacy, being deleted
```

**Legacy layout to migrate, not imitate:** `controller/`, `domain/`, `dto/`, `model/`,
`repository/`, `service/` at the root hold the student directory. Target: one
`students/` feature package.

## Backend rules

- **Controllers are thin.** Read the principal, delegate, wrap in `ResponseEntity`.
  No business logic, no repository calls.
- **Never accept an identity from the request body.** Always
  `CurrentUser.emailOf(clerkSession)` from `@AuthenticationPrincipal Jwt`. If a
  request carries an owner field, overwrite it server-side.
- **Never return an entity from a controller.** Map to a `dto/*Response`.
- **Validate at the boundary** with `@Valid` + Jakarta annotations on `dto/*Request`.
- **Authorize in the service layer.** Ownership is the rule: load the row, compare its
  owner email to the caller's, throw `403` on mismatch. Do not rely on a `WHERE
  owner = ?` clause alone — a missing row and a forbidden row must not look the same.
- **`@Transactional` on multi-write service methods.** Partial writes are bugs.
- **Enums are `EnumType.STRING`.** Never ordinal.
- **Timestamps are `java.time.Instant`**, UTC.
- **New persistence is JPA only.** Do not add `NamedParameterJdbcTemplate` code.
- **Paginate every list endpoint.** Accept `Pageable`, return `Page<T>`. Unbounded
  `List<T>` returns are the project's main scale defect — do not add more.
- **Index what you filter on** via `@Table(indexes = ...)` or a Flyway migration.

## Error handling

One global handler: `exception/ExceptionTranslator` (`@ControllerAdvice`,
`HIGHEST_PRECEDENCE`). Every response body is an `ExceptionWrapper`
`{status, message, path}`.

| Throw | Becomes |
|---|---|
| `SearchNotFoundException` | `404` |
| `EmailAlreadyExistsException` | `409` |
| `MethodArgumentNotValidException` | `400` with per-field messages |
| `ResponseStatusException` | its own status (used for `401` on a missing claim) |
| anything else | `500` |

- Prefer a **domain exception** over `ResponseStatusException` in services.
- Adding a new status means adding an `@ExceptionHandler` — the catch-all would
  otherwise turn it into a `500`.
- Never swallow an exception to return an empty result.

## Logging

- `@Slf4j` (Lombok). Never `System.out`.
- Parameterised: `log.warn("Rejected at {}: ", uri, ex)` — never string concatenation.
- `warn` for expected client faults, `error` for unexpected server faults.
- **Never log a token, secret, or password.** Emails in logs should be rare.

## Frontend directory structure

```
frontend/src/
├── main.tsx              ClerkProvider + Router + ToastProvider
├── App.tsx               routes, RequireAuth, AuthTokenBridge
├── pages/                one file per route, PascalCase, default export
├── components/
│   ├── layout/AppShell.tsx
│   └── ui/               Tabs, Modal, Toast, Feedback — shared primitives
├── hooks/                use*.ts, named export
├── lib/                  api.ts, authToken.ts
├── types/index.ts        shared API types
└── index.css             Tailwind @theme tokens + component classes
```

`@/` is aliased to `frontend/src` — use it instead of `../../..`.

## Frontend rules

- **All HTTP goes through `lib/api.ts`** (`api.get/post/put/del`). It attaches the
  Clerk token, sets JSON headers, and throws `ApiError {status, message}`. Never call
  `fetch` directly in a page.
- **Never touch `getToken` in a page.** `AuthTokenBridge` is the only writer to
  `lib/authToken.ts`.
- Build query strings with `toQueryString(...)`, which drops empty values.
- Pages own their own loading / empty / error state using `Spinner`, `EmptyState`,
  `ErrorState` from `components/ui/Feedback`.
- User feedback goes through `useToast().push(message, kind)` — never `alert()`.
- Protect a route by wrapping the element in `<RequireAuth>`. Adding a route also
  means adding it to `SpaForwardingConfig` and `SecurityConfig` until the
  `/api` consolidation lands.
- Type every API payload in `types/index.ts`. **No `any`.** Prefer `unknown` + narrowing.

## Functional / immutability rules

- **Never mutate props, state, or a function parameter.** Derive a new value:
  `[...items, next]`, `{...obj, field}`, `items.map(...)`.
- Prefer `map` / `filter` / `reduce` over index loops where it reads as clearly.
- Keep components pure: no side effects in render, effects only in `useEffect`.
- Effects declare complete dependency arrays and clean up after themselves.
- Backend: build DTOs with Lombok `@Builder`; do not add setters to reshape a DTO
  mid-flow.
- Backend has no server actions — the boundary is always an HTTP REST endpoint.

## Naming

| Thing | Convention | Example |
|---|---|---|
| Java class | PascalCase | `ListingService` |
| Java package | lowercase, singular feature | `marketplace` |
| DB table / column | snake_case | `listing_favorite`, `seller_email` |
| REST path | lowercase, plural, kebab | `/api/marketplace/my-listings` |
| React component / page | PascalCase | `Marketplace.tsx` |
| Hook | `use` + PascalCase | `useSchools.ts` |
| TS type | PascalCase | `ListingResponse` |
| CSS component class | kebab, BEM-ish variant | `.btn-primary`, `.badge-success` |
| Flyway migration | `V<n>__snake_case.sql` | `V1__baseline.sql` |

## Keep changes small

- One concern per change. Do not reformat or "tidy" unrelated files.
- Do not introduce an interface with one implementation, a factory for one product,
  or config for a value that never varies.
- Delete dead code you make redundant rather than leaving it beside the new path.
