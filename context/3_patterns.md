# 3. Patterns — Code Conventions & Standards

> Match the surrounding code. Where this file and existing code disagree, the **feature
> packages** (`marketplace`, `messages`, `community`, `support`) are the reference — not
> the legacy `controller/` + `repository/` + `service/` trio at the package root.

## Backend directory structure

Organise **by feature**, not by layer:

```
com.jonathansoriano.enterprisedevgroupproject/
├── <feature>/                  marketplace | messages | community | support | school
│   ├── <Entity>.java           @Entity, @Table(name = "snake_case")
│   ├── <Entity>Repository.java extends JpaRepository<T, Long>
│   ├── <Feature>Service.java   business logic + authorization
│   ├── <Feature>Controller.java thin: parse, delegate, return
│   ├── <Enum>.java             @Enumerated(EnumType.STRING)
│   └── dto/
│       ├── <X>Request.java     inbound, bean-validated
│       └── <X>Response.java    outbound, never an entity
├── config/                     SecurityConfig, SpaForwardingConfig
├── security/                   CurrentUser
├── exception/                  ExceptionTranslator, ExceptionWrapper, domain exceptions
├── webhook/                    (planned) Clerk webhook receiver
├── seed/                       ApplicationRunner seeders
└── util/                       SqlUtils  ← legacy, being deleted
```

**Legacy layout to migrate, not imitate:** `controller/`, `domain/`, `dto/`, `model/`,
`repository/`, `service/` at the root hold the student directory. Target: a `students/`
feature package.

## Authorization — the OWASP A01 rules

Broken access control is the #1 focus of the graded security review. These are not
style preferences; they are invariants 1, 2 and 4.

- **Read the caller from the JWT, never the request.** `@AuthenticationPrincipal Jwt`
  → `CurrentUser`. If a request body carries an owner field, **overwrite it
  server-side** before it reaches the service.
- **Check ownership in the service layer, explicitly:**
  ```java
  Listing listing = repo.findById(id)
      .orElseThrow(() -> new SearchNotFoundException("Listing not found"));
  if (!listing.getOwnerId().equals(caller)) {
      throw new ForbiddenException("Not your listing");   // 403, not 404
  }
  ```
  Never rely on `findByIdAndOwner(...)` alone — a missing row and a forbidden row must
  be distinguishable in the code even when the API response is deliberately vague.
- **Admin is a capability check, not a route.** Read the admin flag from Clerk metadata
  on the token and assert it in the service. Never infer it from a URL prefix.
- **Filter private fields server-side.** A profile field the user marked private must
  never appear in a response payload — hiding it in the client is a data leak.
- **Never return an email address** from a public-facing endpoint (invariant 4).

## Persistence rules

- **New persistence is JPA only.** Do not add `NamedParameterJdbcTemplate` code.
- **All SQL parameterised** — `:named` parameters or JPQL. No concatenation, ever
  (invariant 6, OWASP A03).
- **Schema changes only via Flyway.** New file `V<n>__snake_case.sql`. **Never edit an
  applied migration** — add a new one.
- **`@Transactional` on multi-write service methods.** Partial writes are bugs.
- **Enums persist as `EnumType.STRING`.** Never ordinal.
- **Timestamps are `java.time.Instant`,** UTC.
- **Paginate every list endpoint.** Accept `Pageable`, return `Page<T>`. Objective 4 is
  sub-second search at 10,000 listings; unbounded `List<T>` cannot meet it.
- **Index what you filter on** — declare in the migration, and in `@Table(indexes=...)`
  so `validate` stays honest.

## Error handling

One global handler: `exception/ExceptionTranslator` (`@ControllerAdvice`,
`HIGHEST_PRECEDENCE`). Every body is an `ExceptionWrapper` `{status, message, path}`.

| Throw | Becomes |
|---|---|
| `SearchNotFoundException` | `404` |
| `EmailAlreadyExistsException` | `409` |
| `MethodArgumentNotValidException` | `400`, per-field messages |
| `ResponseStatusException` | its own status |
| anything else | `500` |

- Prefer a **domain exception** over `ResponseStatusException` in services.
- A new status needs a new `@ExceptionHandler` — otherwise the catch-all makes it a `500`.
- Never swallow an exception to return an empty result.
- **Error messages must not leak** whether a resource exists to someone not entitled to
  know, or echo internal SQL, stack traces, or configuration.

## Logging

- `@Slf4j`. Never `System.out`.
- Parameterised: `log.warn("Rejected at {}: ", uri, ex)` — never concatenation.
- `warn` for expected client faults, `error` for unexpected server faults.
- **Never log a token, secret, password, or full email address.**

## Webhooks (planned, Sprint 1)

- **Verify the signature first**, before parsing the body or touching the database.
  Reject with `401` on failure.
- Webhook handlers are **idempotent** — Clerk retries; a replayed `user.created` must
  not create a second student row.
- The webhook route is the only unauthenticated `/api` path; it authenticates by
  signature instead.

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

- **All HTTP goes through `lib/api.ts`** (`api.get/post/put/del`). It attaches the Clerk
  token, sets JSON headers, and throws `ApiError {status, message}`. Never call `fetch`
  directly in a page.
- **Never touch `getToken` in a page.** `AuthTokenBridge` is the only writer to
  `lib/authToken.ts`.
- Build query strings with `toQueryString(...)`, which drops empty values.
- Pages own loading / empty / error state via `Spinner`, `EmptyState`, `ErrorState`.
- Feedback goes through `useToast().push(message, kind)` — never `alert()`.
- Protect a route by wrapping the element in `<RequireAuth>`, and add it to
  `SpaForwardingConfig` + `SecurityConfig` until the `/api` consolidation lands.
- Type every payload in `types/index.ts`. **No `any`.** Prefer `unknown` + narrowing.
- **Client-side checks are UX, never security.** Hiding an admin button does not protect
  the endpoint behind it.

## Functional / immutability rules

- **Never mutate props, state, or a parameter.** Derive: `[...items, next]`,
  `{...obj, field}`, `items.map(...)`.
- Prefer `map` / `filter` / `reduce` over index loops where it reads as clearly.
- Components stay pure: no side effects in render; effects only in `useEffect`, with
  complete dependency arrays and cleanup.
- Backend: build DTOs with Lombok `@Builder`; don't reshape a DTO with setters mid-flow.
- The boundary is always an HTTP endpoint — there are no server actions.

## Naming

| Thing | Convention | Example |
|---|---|---|
| Java class | PascalCase | `ListingService` |
| Java package | lowercase, singular feature | `marketplace` |
| DB table / column | snake_case | `listing_favorite`, `clerk_user_id` |
| REST path | lowercase, plural, kebab | `/api/marketplace/my-listings` |
| React component / page | PascalCase | `Marketplace.tsx` |
| Hook | `use` + PascalCase | `useSchools.ts` |
| TS type | PascalCase | `ListingResponse` |
| CSS component class | kebab, BEM-ish variant | `.btn-primary`, `.badge-success` |
| Flyway migration | `V<n>__snake_case.sql` | `V1__baseline.sql` |
| Branch | `feat/` `fix/` `chore/` + short slug | `feat/12-listing-photos` |

## Keep changes small

- One concern per pull request. Don't reformat or "tidy" unrelated files.
- No interface with one implementation, no factory for one product, no config for a
  value that never varies.
- Delete dead code you make redundant rather than leaving it beside the new path.
- If a change spans UI **and** schema **and** background work, it is too big — split it.
