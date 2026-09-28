import type { ReactNode } from 'react'

export interface TabOption<T extends string> {
  value: T
  label: string
  count?: number
}

export function Tabs<T extends string>({
  options,
  value,
  onChange,
}: {
  options: TabOption<T>[]
  value: T
  onChange: (value: T) => void
}) {
  return (
    <div className="inline-flex gap-1 rounded-full border border-[var(--color-border)] bg-[var(--color-surface)] p-1">
      {options.map((option) => {
        const active = option.value === value
        return (
          <button
            key={option.value}
            onClick={() => onChange(option.value)}
            className={`rounded-full px-4 py-1.5 text-sm font-semibold transition-all ${
              active
                ? 'bg-primary-600 text-white shadow-sm'
                : 'text-[var(--color-ink-muted)] hover:text-[var(--color-ink)]'
            }`}
          >
            {option.label}
            {option.count !== undefined && (
              <span className={`ml-1.5 text-xs ${active ? 'text-primary-100' : 'text-[var(--color-ink-faint)]'}`}>
                {option.count}
              </span>
            )}
          </button>
        )
      })}
    </div>
  )
}

export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string
  subtitle?: string
  action?: ReactNode
}) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-[var(--color-ink-muted)]">{subtitle}</p>}
      </div>
      {action}
    </div>
  )
}
