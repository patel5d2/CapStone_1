# 4. UI Design — Design System, Navigation & Theming

> Token source of truth: `frontend/src/index.css`. **Never hardcode a hex colour and
> never invent a token.** If something is missing, add it to `@theme` first, then use it.
> Objective 8 (automatic school theming) and WCAG 2.1 AA are graded requirements.

## Framework

- **Tailwind CSS 4** via `@tailwindcss/vite`. No `tailwind.config.js` — tokens live in
  the `@theme` block in `index.css`.
- **No component library.** No shadcn/ui, MUI or Radix. Shared primitives are hand-rolled
  in `components/ui/`. Do not install a UI kit.
- Icons: **`lucide-react`** only. `h-4 w-4` inline, `h-5 w-5` in buttons and nav.

## Navigation — four tabs

The app has **four tabs**, not five. The student directory is a surface **inside
Community**, not a peer of it. (`AppShell` currently lists five — folding Directory into
Community is a Sprint 2 task.)

| Tab | Icon | Route |
|---|---|---|
| Marketplace | `Store` | `/marketplace` |
| Messages | `MessageCircle` | `/messages` |
| Community | `Users` | `/community` |
| Support | `LifeBuoy` | `/support` |

**Responsive shell — the core navigation pattern:**

| Width | Pattern |
|---|---|
| **≥ 1024px** | Persistent left sidebar, 240px, icon + label, active item tinted with the school accent. Profile and notifications pinned at the bottom. |
| **640–1023px** | Collapsed icon-only rail, 72px, labels as tooltips. |
| **< 640px** | **Bottom tab bar** — thumb-reachable, four items, icon above a 11px label, safe-area inset padding. Top bar keeps only the school badge, search and the user button. |

Rules that keep navigation obvious:

- **The active tab is unmistakable** — accent fill plus an indicator bar, never colour alone.
- **Navigation never moves between breakpoints.** Same four items, same order, always.
- **One level of nesting maximum.** Sub-surfaces (Directory inside Community, Marketplace
  vs Groups vs Direct inside Messages) use the `Tabs` primitive, never a second nav bar.
- **Every page opens with `PageHeader`** (`title`, `subtitle?`, `action?`) so the user
  always knows where they are.
- **A tab never shows a blank screen.** `Spinner` while loading, `EmptyState` when empty
  with a clear next action, `ErrorState` with retry on failure.
- **Unread and pending counts** ride on the tab as a badge, capped at `99+`.

## Design tokens

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

### Surface & ink (theme-aware)

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
  and avatars. Nothing else.
- Shadows: `shadow-card` resting · `shadow-card-hover` hover · `shadow-float` modals,
  popovers, toasts, the bottom bar.
- Spacing stays on Tailwind's 4px scale: `gap-2` within a control, `gap-4` between cards,
  `p-4`/`p-6` card padding, `px-4 py-2.5` buttons, `px-3.5 py-2.5` fields. No arbitrary
  values like `p-[13px]`.

## School theming — objective 8

**The mechanism:** base semantic tokens (`surface`, `ink`, `border`) never change. Only
the **`primary-*` scale** is re-pointed per school. Because every utility compiles to
`var(--color-…)`, overriding those variables re-themes the entire app with no component
changes.

1. On login, resolve the signed-in student's school.
2. Stamp `data-school="<slug>"` on `<html>`.
3. A CSS block per school overrides the `primary-*` scale.
4. No school, or an unknown one → the base indigo palette above.

```css
/* index.css */
:root[data-school="uc"] {
  --color-primary-50:  /* tint  */;
  --color-primary-600: /* action — must hit 4.5:1 on white AND on surface-dark */;
  --color-primary-700: /* hover */;
}
:root[data-school="xavier"] { /* … */ }
```

Slugs: `uc`, `xavier`, `nku`, `miami`, `cincystate`, `msj`.

**Filling in the palettes is a design task, not a guess.** Each school's values come from
its official brand guide, and **every one must be contrast-checked before it ships** —
`primary-600` against white and against `surface` in dark mode, at 4.5:1 for body text
and 3:1 for large text and UI boundaries. A school colour that fails contrast gets a
darkened in-app variant; the brand hue is never allowed to break invariant 11.

Keep school colour to **accent** surfaces — active nav, primary buttons, links, focus
rings, badges. Page backgrounds and body text stay neutral, or six themes become six
different products.

## Component classes

Defined in `@layer components` in `index.css`. Prefer these over re-styling from scratch —
this is what stops each page inventing its own look.

| Class | Use |
|---|---|
| `.btn-primary` | primary action, filled with the school accent |
| `.btn-secondary` | secondary, tinted `primary-50` |
| `.btn-ghost` | tertiary, bordered and transparent |
| `.btn-danger` | destructive |
| `.btn-sm` | size modifier, combine with a variant |
| `.card` / `.card-hover` | bordered surface panel, optional hover elevation |
| `.field` | input, textarea, select |
| `.badge-neutral` `-primary` `-success` `-warning` `-danger` | status pills |
| `.animate-fade-in-up` | list and panel entrance, 0.25s |
| `.thin-scrollbar` | scroll containers |

Every button variant already carries layout, radius, transition and a disabled state.
Write `className="btn-primary"`, not a re-derived stack of utilities.

Tailwind 4 **cannot `@apply` a custom class** — variants share base rules through a
selector list. Follow that pattern when adding one.

## Shared components

| Component | Import | Props |
|---|---|---|
| `AppShell` | `components/layout/AppShell` | route layout with the four-tab nav |
| `Spinner` | `components/ui/Feedback` | `{ label? }` |
| `EmptyState` | `components/ui/Feedback` | `{ icon, title, description?, action? }` |
| `ErrorState` | `components/ui/Feedback` | `{ message, onRetry? }` |
| `Modal` | `components/ui/Modal` | `{ open, onClose, title, description?, children, footer? }` |
| `Tabs` | `components/ui/Tabs` | `{ options: TabOption<T>[], value, onChange }` |
| `PageHeader` | `components/ui/Tabs` | `{ title, subtitle?, action? }` |
| `useToast` | `components/ui/Toast` | `push(message, kind?)` |

## Dark mode & text size

- Dark mode is automatic via `prefers-color-scheme`, and works because utilities compile
  to `var(--color-…)`. A user override (Sprint 2) stamps `data-theme` on `<html>` and
  must win over the media query in both directions.
- **Use semantic tokens** (`surface`, `ink`, `border`) so dark mode is free. A raw
  `bg-white` or `text-slate-900` breaks it.
- Text-size preference (Sprint 2) scales the root font size. Never hardcode `px` for
  body text — use the `text-*` scale so it scales with the user's choice.

## Accessibility — WCAG 2.1 AA, graded

- **Keyboard operable end to end.** Every flow completable without a mouse; visible focus
  on every interactive element; logical tab order; no keyboard traps in `Modal`.
- **Text alternatives on all images** — listing photos use the listing title; decorative
  images get `alt=""`.
- **Visible focus indicators.** `.field` ships `focus:ring-2 focus:ring-primary-100`.
  Never `outline-none` without a visible replacement.
- **Contrast ≥ 4.5:1** body text, **3:1** large text and UI boundaries — verified in
  **every school theme** and in dark mode.
- **Semantic elements.** `<button>` and `<a>` for interaction, never a clickable `<div>`.
  Icon-only controls need `aria-label`; `Modal` needs a real `title`.
- **Status is never colour alone** — pair a badge colour with its text.
- **Touch targets ≥ 44×44px**, especially in the bottom tab bar.
- Mobile-first: everything works at ~375px. Flex and grid wrap rather than scrolling
  horizontally.
