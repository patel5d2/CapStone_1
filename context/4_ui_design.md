# 4. UI Design — Design System, Navigation & Theming

> **Implementation audit: 2026-09-16.** Token source of truth:
> [`frontend/src/index.css`](../frontend/src/index.css). Define new colours in the
> theme before using them in components; do not scatter hex values through pages.
> Objective 8 (automatic school theming) remains a requirement, not a claim that the
> current UI meets it. The colour tokens **are** contrast-checked and the ratios are
> recorded below; that is one WCAG criterion of many, and the keyboard, focus-order and
> screen-reader gaps listed under Accessibility are still open. Target designs below are
> pending where the implementation status says so; scope decisions follow Team Rule 8.

## Implementation status

| Area | Present in source | Still required |
|---|---|---|
| Navigation | Sidebar ≥1024px, icon rail 640–1023px, bottom bar <640px; Directory is a sub-tab of Community | Unread/pending count badges |
| Palette | Pine-green primary, warm stone neutrals, contrast-checked in both modes | School lookup and per-school palettes |
| Preferences | Automatic `prefers-color-scheme` dark mode | User theme override and text-size setting |
| Feedback | Spinner, empty/error states, toasts, modal and button tabs | Consistent error states and accessibility fixes below |
| Shared features | Profile route and Clerk user button | Notification centre, campus map and home-feed work in the sprint plan |

The current navigation order is **Marketplace, Messages, Community, Support**, identical
in all three containers — one `NAV_ITEMS` array drives them, so they cannot drift.
Profile is pinned at the bottom of the sidebar beside the Clerk user button.

Rendered and checked at 1440px, 820px and 390px in headless Chromium on 2026-09-16.
That covers layout only: **no keyboard, screen-reader or cross-browser check has been
done**, and the signed-in shell was not exercised because it needs a Clerk session.
Record those in [progress](5_progress.md) when someone does them.

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
the retired `/directory` route redirects there. The responsive shell below is present
in `AppShell`; full acceptance remains Sprint 2 work.

**S0-4 review artifacts (2026-09-17):** [wireframe board](../docs/phase-0/wireframes/index.html)
and [annotated specification](../docs/phase-0/wireframes.md), with all four tabs at
375px and 1440px plus directory/chat detail frames. These target designs distinguish
current UI from implementation work and do not establish approval or app acceptance.

| Tab | Icon | Route |
|---|---|---|
| Marketplace | `Store` | `/marketplace` |
| Messages | `MessageCircle` | `/messages` |
| Community | `Users` | `/community` |
| Support | `LifeBuoy` | `/support` |

**Responsive shell — implemented:**

| Width | Pattern | Class |
|---|---|---|
| **≥ 1024px** | Persistent left sidebar, icon + label, profile pinned at the bottom | `lg:w-60` → 240px |
| **640–1023px** | Collapsed icon-only rail, labels as native `title` tooltips | `w-18` → 72px |
| **< 640px** | **Bottom tab bar** — four items, icon above an 11px label, safe-area inset padding, 52px targets. A slim top bar carries the wordmark and user button. | `min-h-13` |

The sidebar and rail are one `<aside>` whose label collapses, not two components, so the
destinations cannot diverge. Widths come from Tailwind v4's dynamic spacing
(`--spacing: .25rem`), so `w-18` is exactly 72px and `lg:w-60` exactly 240px — verified
in the compiled CSS, because a typo there fails silently as a zero-width rail.

Signed-out visitors get no sidebar and no offset; the main column's `sm:pl-18 lg:pl-60`
is applied only when `useAuth().isSignedIn`. Gating with `<SignedIn>` alone cannot do
this — it hides children, it cannot drop a class from a wrapper, which left a 240px
indent against empty space.

*Not yet built:* the notification centre that shares the sidebar's bottom slot, and the
unread/pending badges below.

Rules that keep navigation obvious:

- **The active tab is unmistakable** — accent fill plus an indicator bar, never colour
  alone. Implemented: a left bar in the sidebar and rail, a top bar in the bottom tab bar.
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

These values match `index.css`. Unlike the previous revision, the ratios below were
computed, not assumed — see **Contrast ledger**.

### Why this palette

The palette it replaced was `indigo-600` (`#4f46e5`) with a cyan accent over cool slate
(`#f8fafc` / `#0f172a`). That is the default a framework hands you and the combination
every generated starter app wears, so it says nothing about this product. Three
deliberate moves:

- **Pine green, not indigo or violet.** Green reads as campus rather than as dashboard,
  and it is the one saturated family the generated-app look never uses.
- **Warm stone neutrals, not cool slate.** A warm grey reads as paper; the green then
  sits on it like ink rather than like a UI kit on a screen.
- **Ochre accent, not cyan.** Decorative only — hero glow, highlights. It is never an
  action colour, so it never competes with the primary.

This is the **fallback** palette. Objective 8 overrides the `primary-*` scale per school,
so the green is what an unthemed or signed-out visitor sees.

### Base palette (fallback — used before sign-in and for any unthemed school)

| Token | Value |
|---|---|
| `primary-50` | `#eef4f0` |
| `primary-100` | `#d7e7de` |
| `primary-200` | `#b0cfc0` |
| `primary-400` | `#5b9a79` ← link on dark |
| `primary-500` | `#3d8060` ← focus border |
| `primary-600` | `#2c6a4d` ← default action |
| `primary-700` | `#21523c` ← hover |
| `primary-900` | `#122e22` |
| `accent-400` | `#e3b04b` ← decorative only |
| `accent-500` | `#cf982f` ← decorative only |

### Surface & ink (light and system dark mode)

| Token | Light | Dark |
|---|---|---|
| `surface` | `#ffffff` | `#1c1f1d` |
| `surface-muted` | `#f7f5f1` | `#131614` |
| `border` | `#e6e1d8` | `#2c302d` |
| `ink` | `#1b1a17` | `#f1efe9` |
| `ink-muted` | `#6a6459` | `#a4a096` |
| `ink-faint` | `#968f83` | `#767268` |
| `scrim` | `rgba(28,26,23,.45)` | `rgba(8,10,9,.62)` |
| `border-strong` | `#948d80` | `#767268` |

**`border` vs `border-strong`.** The hairline `border` is 1.30:1 — right for card and bar
edges, which are decorative and exempt. A field's edge is not decorative: it is what says
"this is an input", so WCAG 1.4.11 asks 3:1 for it. `.field` uses `border-strong`;
everything else keeps the hairline. Do not raise `border` itself, or every card outline
turns into a box.

**Green in dark mode.** `primary-600` is 2.59:1 on the dark surface. It is fine as a
*fill* (white on it is 6.41:1) and wrong as text or an icon. Every `text-primary-600`
needs a `dark:text-primary-400` beside it — nine places in the app did not have one.

`scrim` backs the modal overlay. `Modal` previously hardcoded `bg-slate-900/40`; a token
keeps it warm with the rest of the theme and honours the "no raw colour utilities" rule
below.

### Status

Status **foregrounds now change with the mode**, not only their backgrounds. The previous
theme kept light-mode ink on a dark tint, which put dark-mode warning at 2.98:1 and
danger at 3.34:1 — both under the 4.5:1 this project is graded on.

| Token | Light | Light bg | Dark | Dark bg |
|---|---|---|---|---|
| `success` | `#127e43` | `#eef7f0` | `#6fd69a` | `#10291b` |
| `warning` | `#9a6216` | `#fcf5e8` | `#e8b95f` | `#2e2109` |
| `danger` | `#b3261e` | `#fdf0ee` | `#f59e94` | `#2d100d` |

**Known trade-off:** a green primary sits near the green success colour. They differ in
lightness and saturation, and the "status is never colour alone" rule below already
requires a text label on every badge, which is what carries the meaning. Worth a second
look if a school theme is ever itself green.

### Contrast ledger

Computed against WCAG 2.1 relative luminance. Re-run these whenever a token moves, and
repeat the whole table for each school palette as it lands.

| Pairing | Ratio | Needs |
|---|---|---|
| `btn-primary` — white on `primary-600` | 6.41:1 | 4.5 |
| `btn-secondary` / `badge-primary` — `primary-700` on `primary-50` | 8.06:1 | 4.5 |
| Link `primary-600` on `surface` | 6.41:1 | 4.5 |
| Link `primary-600` on `surface-muted` | 5.89:1 | 4.5 |
| Landing hero — `primary-100` on `primary-700` | 7.01:1 | 4.5 |
| Body `ink` on `surface` (light / dark) | 17.40:1 / 14.46:1 | 4.5 |
| `ink-muted` on `surface-muted` (light / dark) | 5.39:1 / 6.98:1 | 4.5 |
| `ink-faint` on `surface` (light / dark) | 3.20:1 / 3.47:1 | 3.0 · meta text only |
| Focus border `primary-500` on `surface` | 4.71:1 | 3.0 |
| Field resting border `border-strong` on `surface` (light / dark) | 3.29:1 / 3.47:1 | 3.0 · control boundary |
| Dark-mode green text `primary-400` on dark `surface` | 5.02:1 | 4.5 |
| Dark active nav — `primary-200` on `primary-900/40` | 9.47:1 | 4.5 |
| `success` on its bg (light / dark) | 4.69:1 / 7.51:1 | 4.5 |
| `warning` on its bg (light / dark) | 4.69:1 / 8.62:1 | 4.5 |
| `danger` on its bg (light / dark) | 5.88:1 / 8.56:1 | 4.5 |

`ink-faint` is the one token held to 3:1 rather than 4.5:1: it is used for timestamps and
secondary meta, never for body copy. The value it replaced (`#94a3b8`) was **2.56:1** on
white and failed even that.

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
4. No school, or an unknown one → the base pine-green palette above.

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

## Cascade trap — read before adding a bare element rule

`index.css` had a bare `a { color: inherit }` **outside any layer**. Unlayered CSS
outranks every `@layer`, including Tailwind's utilities, so that one rule silently beat
every text-colour class on every link in the app: `text-white` inside `.btn-primary`
rendered as inherited dark ink, and the landing hero's call to action rendered white on
a white button — invisible, and invisible in exactly the way a build, a type-check and a
lint run all pass. It was caught by screenshotting the page.

It now lives in `@layer base`, where utilities outrank it. **Any element-level rule you
add goes in `@layer base`.** A rule outside a layer is a rule that quietly wins.

## Component classes

Defined in `@layer components` in `index.css`. Prefer these over re-styling from scratch —
this is what stops each page inventing its own look.

| Class | Use |
|---|---|
| `.btn` | base button layout when a component supplies its own colour treatment |
| `.btn-primary` | primary action, filled with `primary-600` |
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
| `AppShell` | `components/layout/AppShell` | route layout with the four-destination nav; renders an `Outlet` |
| `Spinner` | `components/ui/Feedback` | `{ label? }` |
| `EmptyState` | `components/ui/Feedback` | `{ icon, title, description?, action? }` |
| `ErrorState` | `components/ui/Feedback` | `{ message, onRetry? }` |
| `Modal` | `components/ui/Modal` | `{ open, onClose, title, description?, children, footer? }` |
| `Wordmark` | `components/ui/Wordmark` | `{ compact? }` — the tile plus "CampusBridge"; `compact` drops the text for the icon rail |
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
- **Contrast ≥ 4.5:1** body text, **3:1** large text and UI boundaries. The base palette
  is verified in both modes — see the contrast ledger. **No school theme is verified**,
  because none exists yet; each one repeats that table before it ships.
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
