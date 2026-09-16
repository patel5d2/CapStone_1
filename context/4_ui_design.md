# 4. UI Design — Design System & Tokens

> Single source of truth: `frontend/src/index.css`. The values below are copied from
> it. **Never hardcode a hex colour, and never invent a token.** If something is
> missing, add it to `@theme` in `index.css` first, then use it.

## Framework

- **Tailwind CSS 4**, loaded via `@tailwindcss/vite` (no `tailwind.config.js`; tokens
  live in the `@theme` block in `index.css`).
- **No component library.** No shadcn/ui, no MUI, no Radix. Shared primitives are
  hand-rolled in `components/ui/`. Do not install a UI kit.
- Icons: **`lucide-react`** only.

## Design tokens

### Colour — brand

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

### Colour — surface & ink (theme-aware)

| Token | Light | Dark |
|---|---|---|
| `surface` | `#ffffff` | `#111827` |
| `surface-muted` | `#f8fafc` | `#0b1120` |
| `border` | `#e2e8f0` | `#1f2937` |
| `ink` | `#0f172a` | `#f1f5f9` |
| `ink-muted` | `#64748b` | `#94a3b8` |
| `ink-faint` | `#94a3b8` | `#64748b` |

### Colour — status

| Token | Value | Background |
|---|---|---|
| `success` | `#16a34a` | `success-bg` `#f0fdf4` / dark `#052e16` |
| `warning` | `#b45309` | `warning-bg` `#fffbeb` / dark `#451a03` |
| `danger` | `#dc2626` | `danger-bg` `#fef2f2` / dark `#450a0a` |

### Typography

- One family: `--font-sans` → `"Inter", ui-sans-serif, system-ui, -apple-system, sans-serif`.
- Scale in use: `text-[11px]` (badges) · `text-xs` · `text-sm` (body/controls) ·
  `text-base` · `text-lg` · `text-xl`+ (page titles).
- Weights: `font-semibold` for controls, `font-bold` for badges and headings.
- Body is antialiased; do not override font smoothing per component.

### Shadow & radius

| Token | Use |
|---|---|
| `shadow-card` | resting card |
| `shadow-card-hover` | card under pointer (`.card-hover`) |
| `shadow-float` | modal, popover, toast |

Radius: `rounded-xl` for buttons and inputs, `rounded-2xl` for cards,
`rounded-full` for badges and avatars. Nothing else.

### Spacing

Stay on Tailwind's 4px scale. Conventions already in use: `gap-2` inside a control,
`gap-4` between cards, `p-4`/`p-6` for card padding, `px-4 py-2.5` for buttons,
`px-3.5 py-2.5` for fields. Do not use arbitrary values like `p-[13px]`.

## Component classes

Defined in `@layer components` in `index.css`. **Prefer these over re-styling from
scratch** — this is what stops each generated page inventing its own look.

| Class | Use |
|---|---|
| `.btn-primary` | primary action; filled `primary-600` |
| `.btn-secondary` | secondary action; tinted `primary-50` |
| `.btn-ghost` | tertiary; bordered, transparent |
| `.btn-danger` | destructive; `danger-bg` on `danger` |
| `.btn-sm` | size modifier, combine with a variant |
| `.card` | bordered surface panel |
| `.card-hover` | adds hover elevation, combine with `.card` |
| `.field` | text input, textarea, select |
| `.badge-neutral` / `-primary` / `-success` / `-warning` / `-danger` | status pills |
| `.animate-fade-in-up` | list/panel entrance, 0.25s |
| `.thin-scrollbar` | scroll containers |

Every button variant already carries base layout, radius, transition and a disabled
state. Write `className="btn-primary"`, not a re-derived stack of utilities.

Tailwind 4 **cannot `@apply` a custom class** — variants share base rules through a
selector list. Follow that pattern when adding a variant.

## Shared components

Use these rather than rebuilding the pattern:

| Component | Import | Props |
|---|---|---|
| `AppShell` | `components/layout/AppShell` | route layout with nav; wraps all in-app routes |
| `Spinner` | `components/ui/Feedback` | `{ label? }` |
| `EmptyState` | `components/ui/Feedback` | `{ icon, title, description?, action? }` |
| `ErrorState` | `components/ui/Feedback` | `{ message, onRetry? }` |
| `Modal` | `components/ui/Modal` | `{ open, onClose, title, description?, children, footer? }` |
| `Tabs` | `components/ui/Tabs` | `{ options: TabOption<T>[], value, onChange }` |
| `PageHeader` | `components/ui/Tabs` | `{ title, subtitle?, action? }` |
| `useToast` | `components/ui/Toast` | `push(message, kind?)` |

**Every page starts with `PageHeader`** and handles all three async states with
`Spinner` / `EmptyState` / `ErrorState`. That consistency is the anti-drift rule.

## Dark mode

Automatic, via `prefers-color-scheme`. It works because utilities compile to
`var(--color-…)` and the media query overrides the variables.

- Use **semantic** tokens (`surface`, `ink`, `border`) so dark mode is free.
- A raw colour like `bg-white` or `text-slate-900` breaks dark mode. Don't.
- Only add a `dark:` variant for a genuine exception (e.g. `dark:bg-primary-900/40`
  in `.btn-secondary`).

## Layout & accessibility

- Mobile-first; the app must work at ~375px. Let flex/grid wrap rather than
  introducing a horizontal scroll.
- Interactive elements are `<button>` / `<a>` — never a clickable `<div>`.
- Icon-only controls need `aria-label`. `Modal` needs a real `title`.
- Rely on the focus ring `.field` already provides (`focus:ring-2 focus:ring-primary-100`);
  never `outline-none` without a visible replacement.
- Status is never colour alone — pair a badge colour with its text.
