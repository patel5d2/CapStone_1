import { useCallback, useRef, useState, type ReactNode } from 'react'
import { Search } from 'lucide-react'
import { ApiError } from '../../lib/api'
import { Modal } from '../../components/ui/Modal'
import { EmptyState, ErrorState, Spinner } from '../../components/ui/Feedback'
import { formatDate, messageOf, plural, type PagedList } from './adminList'

// The pieces every admin tab is built from (#113): one way to load a page of results, one
// way to show its loading/empty/failed states, and one confirmation dialog. Keeping them
// here is what makes a destructive action look and behave the same on every tab.

/** A search box that applies on submit, with whatever extra filters a tab passes as children. */
export function SearchBar({ label, placeholder, applied, onSearch, children }: {
  label: string
  placeholder: string
  applied: string
  onSearch: (query: string) => void
  children?: ReactNode
}) {
  const [text, setText] = useState(applied)
  // "Find account" and "Show all" change the applied search from outside the box.
  const [shown, setShown] = useState(applied)
  if (shown !== applied) {
    setShown(applied)
    setText(applied)
  }
  return (
    <form
      role="search"
      className="card mb-4 flex flex-col gap-3 p-4 sm:flex-row sm:items-center"
      onSubmit={(event) => { event.preventDefault(); onSearch(text.trim()) }}
    >
      <input type="search" aria-label={label} className="field" placeholder={placeholder} value={text}
        maxLength={100} onChange={(event) => setText(event.target.value)} />
      {children}
      <div className="flex gap-2">
        {applied && <button type="button" className="btn-ghost" onClick={() => onSearch('')}>Clear</button>}
        <button type="submit" className="btn-primary"><Search className="h-4 w-4" /> Search</button>
      </div>
    </form>
  )
}

/** The loading, failed and empty states of a list, then its rows, count and pager. */
export function ListFrame<T>({ list, noun, icon, emptyTitle, emptyDescription, children }: {
  list: PagedList<T>
  /** What one row is, e.g. "listing"; used for the count. */
  noun: string
  icon: ReactNode
  emptyTitle: string
  emptyDescription?: string
  children: (item: T) => ReactNode
}) {
  if (list.loading) return <div role="status"><Spinner label={`Loading ${noun}s…`} /></div>
  if (list.error) return <ErrorState message={list.error} onRetry={list.reload} />
  if (list.items.length === 0) return <EmptyState icon={icon} title={emptyTitle} description={emptyDescription} />
  return (
    <>
      <p className="mb-3 text-sm text-[var(--color-ink-muted)]" aria-live="polite">{plural(list.totalItems, noun)}</p>
      <ul className="card divide-y divide-[var(--color-border)]">{list.items.map(children)}</ul>
      {list.totalPages > 1 && (
        <nav aria-label={`${noun} pages`} className="mt-4 flex items-center justify-between gap-3">
          <button className="btn-ghost btn-sm" disabled={list.page === 0} onClick={() => list.setPage(list.page - 1)}>Previous</button>
          <span className="text-sm text-[var(--color-ink-muted)]">Page {list.page + 1} of {list.totalPages}</span>
          <button className="btn-ghost btn-sm" disabled={list.page + 1 >= list.totalPages} onClick={() => list.setPage(list.page + 1)}>Next</button>
        </nav>
      )}
    </>
  )
}

/** One row: what it is on the left, what can be done to it on the right (below it on a phone). */
export function Row({ lead, children, actions }: { lead?: ReactNode; children: ReactNode; actions: ReactNode }) {
  return (
    <li className="flex flex-wrap items-start gap-3 p-4">
      {lead}
      <div className="min-w-0 flex-1">{children}</div>
      <div className="flex w-full shrink-0 flex-wrap items-center gap-2 sm:w-auto">{actions}</div>
    </li>
  )
}

/** A checkbox with its label, for the options and the acknowledgement in a dialog. */
export function Check({ checked, onChange, disabled, children }: {
  checked: boolean; onChange: (checked: boolean) => void; disabled?: boolean; children: ReactNode
}) {
  return (
    <label className="flex items-start gap-2.5">
      <input type="checkbox" className="mt-0.5 h-4 w-4 shrink-0" checked={checked} disabled={disabled}
        onChange={(event) => onChange(event.target.checked)} />
      <span>{children}</span>
    </label>
  )
}

/**
 * The confirmation every privileged action goes through. It names the target, says what
 * will happen, and stays open with the reason if the action fails, so a failure is never
 * mistaken for success. While the request is in flight it cannot be closed, so its outcome
 * is always seen. A 404 means someone else already dealt with the target: `onMissing`.
 */
export function ConfirmDialog({ title, target, consequences, options, acknowledgement, confirmLabel, busyLabel, tone = 'danger', failurePrefix, onConfirm, onClose, onMissing }: {
  title: string
  /** What is about to be acted on, shown in a box so it cannot be mistaken. */
  target: ReactNode
  consequences: ReactNode[]
  options?: ReactNode
  /** When given, the action stays disabled until this is ticked. */
  acknowledgement?: string
  confirmLabel: string
  busyLabel: string
  tone?: 'danger' | 'primary'
  /** Starts the failure message, e.g. "The listing was not deleted." */
  failurePrefix: string
  onConfirm: () => Promise<void>
  onClose: () => void
  onMissing?: () => void
}) {
  const [acknowledged, setAcknowledged] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const [working, setWorking] = useState(false)
  const busy = useRef(false)
  const close = useCallback(() => { if (!busy.current) onClose() }, [onClose])

  const confirm = async () => {
    busy.current = true
    setWorking(true)
    setFailure(null)
    try {
      await onConfirm()
    } catch (e) {
      busy.current = false
      setWorking(false)
      if (onMissing && e instanceof ApiError && e.status === 404) {
        onMissing()
        return
      }
      setFailure(`${failurePrefix} ${messageOf(e, 'Try again.')}`)
    }
  }

  return (
    <Modal
      open
      onClose={close}
      title={title}
      footer={
        <>
          <button type="button" className="btn-ghost" onClick={close} disabled={working}>Cancel</button>
          <button type="button" className={tone === 'danger' ? 'btn-danger' : 'btn-primary'}
            onClick={() => void confirm()} disabled={working || (Boolean(acknowledgement) && !acknowledged)}>
            {working ? busyLabel : confirmLabel}
          </button>
        </>
      }
    >
      <div className="space-y-4 text-sm">
        <div className="rounded-xl border border-[var(--color-border-strong)] bg-[var(--color-surface-muted)] px-4 py-3">{target}</div>
        <ul className="list-disc space-y-1.5 pl-5 text-[var(--color-ink-muted)]">
          {consequences.map((consequence, index) => <li key={index}>{consequence}</li>)}
        </ul>
        {options && <div className="space-y-2">{options}</div>}
        {failure && <p role="alert" className="rounded-xl bg-[var(--color-danger-bg)] px-3 py-2 text-[var(--color-danger)]">{failure}</p>}
        {acknowledgement && (
          <div className="font-semibold">
            <Check checked={acknowledged} onChange={setAcknowledged} disabled={working}>{acknowledgement}</Check>
          </div>
        )}
      </div>
    </Modal>
  )
}

/** The identity line shown wherever content is listed: who posted it and when. */
export function Byline({ email, date }: { email: string | null; date: string | null }) {
  return (
    <p className="mt-0.5 text-xs break-all text-[var(--color-ink-faint)]">
      {[email || 'Unknown account', formatDate(date)].filter(Boolean).join(' · ')}
    </p>
  )
}

export function Strong({ children }: { children: ReactNode }) {
  return <strong className="text-[var(--color-ink)]">{children}</strong>
}
