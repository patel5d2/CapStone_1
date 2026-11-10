import { Check } from 'lucide-react'

const STEPS = ['Your details', 'Verify email', 'Two-step verification']

/**
 * Where a new student is in sign-up. The three steps live on three different screens (the
 * form, the emailed code, Clerk's two-step setup), and without this nothing tells them how
 * much is left. Below 640px only the current step is named, so the row always fits.
 */
export function SignUpSteps({ current }: { current: 1 | 2 | 3 }) {
  return (
    <ol className="flex items-center justify-center gap-2 text-xs" aria-label="Sign-up steps">
      {STEPS.map((label, index) => {
        const step = index + 1
        const done = step < current
        const active = step === current
        return (
          <li key={label} className="flex items-center gap-2" aria-current={active ? 'step' : undefined}>
            {index > 0 && <span aria-hidden="true" className="h-px w-4 bg-[var(--color-border-strong)]" />}
            <span
              className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[11px] font-bold ${
                done || active
                  ? 'bg-primary-600 text-white'
                  : 'border border-[var(--color-border-strong)] text-[var(--color-ink-muted)]'
              }`}
            >
              {done ? <Check className="h-3.5 w-3.5" aria-hidden="true" /> : step}
            </span>
            <span className={active ? 'font-semibold' : 'hidden text-[var(--color-ink-muted)] sm:inline'}>
              {label}{done && <span className="sr-only"> (done)</span>}
            </span>
          </li>
        )
      })}
    </ol>
  )
}
