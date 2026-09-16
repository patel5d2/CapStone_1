# 4. UI Design — Design System, Navigation & Theming

> **Implementation audit: 2026-09-16.** Token source of truth:
> [`frontend/src/index.css`](../frontend/src/index.css). Define new colours in the
> theme before using them in components; do not scatter hex values through pages.
> Objective 8 (automatic school theming) and WCAG 2.1 AA remain requirements, not
> claims that the current UI passes them. Target designs below are still pending
> where the implementation status says so; scope decisions follow Team Rule 8.

## Implementation status

| Area | Present in source | Still required |
|---|---|---|
| Navigation | Four header links at `md` (768px) and above; four signed-in bottom links below `md`; Directory is a sub-tab of Community | Sidebar and rail layout |
| Palette | Shared indigo tokens with dark surface overrides | School lookup and per-school palettes |
| Preferences | Automatic `prefers-color-scheme` dark mode | User theme override and text-size setting |
| Feedback | Spinner, empty/error states, toasts, modal and button tabs | Consistent error states and accessibility fixes below |
| Shared features | Profile route and Clerk user button | Notification centre, campus map and home-feed work in the sprint plan |

The current navigation order is **Marketplace, Messages, Community, Support**. The
profile link is separate and hidden below `sm` (640px). Source inspection does not
establish cross-browser behavior or WCAG conformance; record browser, viewport and
keyboard checks in [progress](5_progress.md).

## Framework

- **Tailwind CSS 4** via `@tailwindcss/vite`. No `tailwind.config.js` — tokens live in
  the `@theme` block in `index.css`.
- **No component library.** No shadcn/ui, MUI or Radix. Shared primitives are hand-rolled
  in `components/ui/`. Do not install a UI kit.
- Icons: **`lucide-react`** only. `h-4 w-4` inline, `h-5 w-5` in buttons and nav.

## Target navigation — four tabs

The app has **four tabs**, not five. The student directory is a surface **inside
Community**, not a peer of it. `AppShell` now lists these four in this order, and
Directory is a `Tabs` sub-surface of Community reached at `/community?tab=directory`;
the retired `/directory` route redirects there. The responsive shell below is still
Sprint 2 work and is not implemented.

| Tab | Icon | Route |
|---|---|---|
| Marketplace | `Store` | `/marketplace` |
| Messages | `MessageCircle` | `/messages` |
| Community | `Users` | `/community` |
| Support | `LifeBuoy` | `/support` |

**Target responsive shell:**

| Width | Pattern |
|---|---|
| **≥ 1024px** | Persistent left sidebar, 240px, icon + label, active item tinted with the school accent. Profile and notifications pinned at the bottom. |
| **640–1023px** | Collapsed icon-only rail, 72px, labels as tooltips. |
| **< 640px** | **Bottom tab bar** — thumb-reachable, four items, icon above a 11px label, safe-area inset padding. Top bar keeps only the school badge, search and the user button. |

Rules that keep navigation obvious:

- **The active tab is unmistakable** — accent fill plus an indicator bar, never colour alone.
- **Destinations and their order stay consistent between breakpoints.** The navigation
  container changes between sidebar, rail and bottom bar.
- **One level of nesting maximum.** Sub-surfaces (Directory inside Community, Marketplace
  vs Groups vs Direct inside Messages) use the `Tabs` primitive, never a second nav bar.
- **Every page opens with `PageHeader`** (`title`, `subtitle?`, `action?`) so the user
  always knows where they are.
- **A tab never shows a blank screen.** `Spinner` while loading, `EmptyState` when empty
  with a clear next action, `ErrorState` with retry on failure.
- **Unread and pending counts** ride on the tab as a badge, capped at `99+`.

## Implemented design tokens

The following values match `index.css`. They document the palette; they do not
certify contrast for every foreground/background pairing. School overrides are
absent today.

### Base palette (fallback — used before sign-in and for any unthemed school)

| Token | Value |
|---|---|
| `primary-50` | `#eef2ff` |
| `primary-100` | `#e0e7ff` |
| `primary-200` | `#c7d2fe` |
| `primary-400` | `#818cf8` |
| `primary-500` | `#6366f1` |
| `primary-600` | `#4f46e5` ← default action |
| `primary-700` | `#4338ca` ← hover |
| `primary-900` | `#312e81` |
| `accent-400` | `#22d3ee` |
| `accent-500` | `#06b6d4` |

### Surface & ink (light and system dark mode)

| Token | Light | Dark |
|---|---|---|
| `surface` | `#ffffff` | `#111827` |
| `surface-muted` | `#f8fafc` | `#0b1120` |
| `border` | `#e2e8f0` | `#1f2937` |
| `ink` | `#0f172a` | `#f1f5f9` |
| `ink-muted` | `#64748b` | `#94a3b8` |
| `ink-faint` | `#94a3b8` | `#64748b` |

### Status

| Token | Value | Background |
|---|---|---|
| `success` | `#16a34a` | `success-bg` `#f0fdf4` / dark `#052e16` |
| `warning` | `#b45309` | `warning-bg` `#fffbeb` / dark `#451a03` |
| `danger` | `#dc2626` | `danger-bg` `#fef2f2` / dark `#450a0a` |

### Typography, radius, shadow, spacing

- One family: `--font-sans` → `"Inter", ui-sans-serif, system-ui, -apple-system, sans-serif`.
- Scale: `text-[11px]` (badges, bottom-bar labels) · `text-xs` · `text-sm` (body and
  controls) · `text-base` · `text-lg` · `text-xl`+ (page titles).
- Weights: `font-semibold` for controls, `font-bold` for badges and headings.
- Radius: `rounded-xl` buttons and inputs · `rounded-2xl` cards · `rounded-full` badges
  and avatars. The current modal and landing hero use `rounded-3xl`; keep these larger
  containers consistent when extending them.
- Shadows: `--shadow-card` resting · `--shadow-card-hover` hover · `--shadow-float`
  floating content. Reuse the shared classes or these variables.
- Spacing stays on Tailwind's 4px scale: `gap-2` within a control, `gap-4` between cards,
  `p-4`/`p-6` card padding, `px-4 py-2.5` buttons, `px-3.5 py-2.5` fields. No arbitrary
  values like `p-[13px]`.

## Planned school theming — objective 8

**Target mechanism:** school selection changes the **`primary-*` scale**; neutral
semantic tokens (`surface`, `ink`, `border`) continue to follow light/dark mode. Use
the shared CSS variables so consumers inherit theme changes. The current CSS contains
no `data-school` rules and no frontend code sets that attribute.

1. On login, resolve the signed-in student's school.
2. Stamp `data-school="<slug>"` on `<html>`.
3. A CSS block per school overrides the `primary-*` scale.
4. No school, or an unknown one → the base indigo palette above.

Proposed selectors follow `:root[data-school="uc"]`. Planned slugs for the six named
schools: `uc`, `xavier`, `nku`, `miami`, `cincystate`, `msj`. The database currently
seeds eight schools; the final supported set remains an [open question](5_progress.md).
Neither the six named schools nor the two additional schools have theme palettes yet.

**Filling in the palettes is a design task, not a guess.** Each school's values come from
its official brand guide, and **every one must be contrast-checked before it ships** —
the actual foreground/background pairs in both modes, at 4.5:1 for ordinary text
and 3:1 for large text and required non-text control indicators. Check button labels,
links and focus indicators separately. A school colour that fails contrast needs an
accessible in-app variant, including a lighter dark-mode variant where appropriate;
the brand hue must not break invariant 11.

Keep school colour to **accent** surfaces — active nav, primary buttons, links, focus
rings, badges. Page backgrounds and body text stay neutral, or six themes become six
different products.

## Component classes

Defined in `@layer components` in `index.css`. Prefer these over re-styling from scratch —
this is what stops each page inventing its own look.

| Class | Use |
|---|---|
| `.btn` | base button layout when a component supplies its own colour treatment |
| `.btn-primary` | primary action, currently filled with the indigo accent |
| `.btn-secondary` | secondary, tinted `primary-50` |
| `.btn-ghost` | tertiary, bordered and transparent |
| `.btn-danger` | destructive |
| `.btn-sm` | size modifier, combine with a variant |
| `.card` / `.card-hover` | bordered surface panel, optional hover elevation |
| `.field` | input, textarea, select |
| `.badge` and `.badge-neutral`, `.badge-primary`, `.badge-success`, `.badge-warning`, `.badge-danger` | base and coloured status pills |
| `.animate-fade-in-up` | list and panel entrance, 0.25s |
| `.thin-scrollbar` | scroll containers |

Every button variant already carries layout, radius, transition and a disabled state.
Write `className="btn-primary"`, not a re-derived stack of utilities.

Current component variants share base rules through a selector list. Follow that
pattern; a selector declared in `@layer components` is not automatically a Tailwind
utility that can be composed with `@apply`. Tailwind's `@utility` directive is a
separate mechanism for defining custom utilities. See the
[Tailwind custom utilities documentation](https://tailwindcss.com/docs/adding-custom-styles#adding-custom-utilities).

## Shared components

| Component | Import | Props |
|---|---|---|
| `AppShell` | `components/layout/AppShell` | route layout with the current five-destination nav; renders an `Outlet` |
| `Spinner` | `components/ui/Feedback` | `{ label? }` |
| `EmptyState` | `components/ui/Feedback` | `{ icon, title, description?, action? }` |
| `ErrorState` | `components/ui/Feedback` | `{ message, onRetry? }` |
| `Modal` | `components/ui/Modal` | `{ open, onClose, title, description?, children, footer? }` |
| `Tabs` | `components/ui/Tabs` | `{ options: TabOption<T>[], value, onChange }` |
| `PageHeader` | `components/ui/Tabs` | `{ title, subtitle?, action? }` |
| `ToastProvider` / `useToast` | `components/ui/Toast` | provider plus `push(message, kind?)`; kind is `success`, `error` or `info` |

Import paths above are relative to `frontend/src/`. Current source uses relative
imports: Vite has an `@` alias, but TypeScript has no matching `paths` configuration.

## Dark mode & text size

- **Implemented:** dark surface, ink and status-background variables follow
  `prefers-color-scheme`; existing `dark:` utilities also follow the system preference.
- **Planned (Sprint 2):** a user override stamps `data-theme` on `<html>`. It must
  control both semantic variables and Tailwind's `dark:` variant, overriding the
  system preference in both directions. No such override is wired today.
- **Use semantic tokens** (`surface`, `ink`, `border`) so dark mode is free. A raw
  `bg-white` or `text-slate-900` breaks it.
- **Planned (Sprint 2):** a text-size preference scales the root font size. Use the
  `text-*` scale for body text so it scales with the user's choice. The existing
  `text-[11px]` badge and navigation labels will need review for this preference.

## Accessibility — WCAG 2.1 AA, graded

These are acceptance requirements. The current primitives have known gaps:

- `Modal` has dialog semantics, a title, Escape dismissal and scroll locking, but no
  initial-focus handling, focus containment or return to the triggering control.
- `Tabs` renders buttons with colour styling, but lacks tab/panel roles, selected
  state and the keyboard behavior expected of an ARIA tab interface.
- `Toast` has no live region and its icon-only dismiss button has no accessible name.
- `Spinner` and error feedback are visual/textual components without dedicated live
  status semantics; verify announcements within each flow.
- No reduced-motion handling or automated frontend accessibility tests are present.
  The muted text, status colours, focus indicators and control sizes still need audit.

- **Keyboard operable end to end.** Every flow completable without a mouse; visible focus
  on every interactive element; logical tab order. A modal keeps focus inside while
  open, supports dismissal and returns focus to its trigger when closed.
- **Text alternatives on all images** — listing photos use the listing title; decorative
  images get `alt=""`.
- **Visible focus indicators.** `.field` ships `focus:ring-2 focus:ring-primary-100`.
  Never `outline-none` without a visible replacement.
- **Contrast ≥ 4.5:1** body text, **3:1** large text and UI boundaries — verified in
  **every school theme** and in dark mode.
- **Semantic elements.** `<button>` and `<a>` for interaction, never a clickable `<div>`.
  Icon-only controls need `aria-label`; `Modal` needs a real `title`.
- **Status is never colour alone** — pair a badge colour with its text.
- **Team touch-target goal: ≥ 44×44 CSS pixels**, especially in the bottom tab bar.
  This is an additional project target; WCAG 2.1's Target Size criterion is level AAA,
  not a universal AA requirement. See [WCAG 2.1 Target Size](https://www.w3.org/WAI/WCAG21/Understanding/target-size.html).
- Mobile-first: everything works at ~375px. Flex and grid wrap rather than scrolling
  horizontally.

Check the actual user flows in Chrome, Safari and Firefox at phone and desktop
widths, in light and dark modes, and with keyboard-only navigation. As school
palettes land, repeat contrast checks for each one. A successful build is not an
accessibility test.
