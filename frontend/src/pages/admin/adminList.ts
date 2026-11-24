import { useCallback, useEffect, useState } from 'react'
import { api, toQueryString } from '../../lib/api'
import type { AdminPage, AdminStudent } from '../../types'

// Loading and formatting shared by every admin tab (#113). No components live here; those
// are in shared.tsx.

const PAGE_SIZE = 20

/** Account data can be incomplete; a missing name must not break the row it is on. */
export function nameOf(account: Pick<AdminStudent, 'firstName' | 'lastName'>) {
  return [account.firstName, account.lastName].filter(Boolean).join(' ').trim() || 'Unnamed account'
}

export function messageOf(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

/** A date an administrator can read; an unreadable or missing one is shown as nothing. */
export function formatDate(iso: string | null | undefined, withTime = false) {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString(undefined, withTime ? { dateStyle: 'medium', timeStyle: 'short' } : { dateStyle: 'medium' })
}

export const plural = (count: number, one: string, many = `${one}s`) => `${count} ${count === 1 ? one : many}`

export interface PagedList<T> {
  items: T[]
  totalItems: number
  totalPages: number
  page: number
  setPage: (page: number) => void
  loading: boolean
  error: string | null
  reload: () => void
  /** Swaps in what the server stored for one row, without refetching the page. */
  replace: (matches: (item: T) => boolean, next: T) => void
}

/**
 * One page of an admin list. `filters` are sent as query parameters; the caller resets the
 * page to 0 when it changes them.
 *
 * The answer is kept with the request it belongs to, so the list is "loading" exactly when
 * the newest answer is for an older request, and a slow earlier response can never
 * overwrite a later one.
 */
export function usePagedList<T>(path: string, filters: Record<string, string | number | boolean | null> = {}): PagedList<T> {
  const [page, setPage] = useState(0)
  const [reloads, setReloads] = useState(0)
  const [outcome, setOutcome] = useState<{ key: string; data?: AdminPage<T>; error?: string } | null>(null)

  const query = toQueryString({
    ...Object.fromEntries(Object.entries(filters).map(([key, value]) => [key, value === false || value === null ? null : String(value)])),
    page,
    size: PAGE_SIZE,
  })
  const requestKey = `${reloads}:${path}${query}`

  useEffect(() => {
    let cancelled = false
    api.get<AdminPage<T>>(`${path}${query}`)
      .then((data) => {
        if (cancelled) return
        // The last row on the last page was removed: step back instead of showing nothing.
        if (data.items.length === 0 && data.totalPages > 0 && page >= data.totalPages) {
          setPage(data.totalPages - 1)
          return
        }
        setOutcome({ key: requestKey, data })
      })
      .catch((e: unknown) => {
        if (!cancelled) setOutcome({ key: requestKey, error: messageOf(e, 'This list could not be loaded.') })
      })
    return () => { cancelled = true }
  }, [path, query, page, requestKey])

  const reload = useCallback(() => setReloads((count) => count + 1), [])
  const replace = useCallback((matches: (item: T) => boolean, next: T) => {
    setOutcome((current) => current?.data
      ? { ...current, data: { ...current.data, items: current.data.items.map((item) => (matches(item) ? next : item)) } }
      : current)
  }, [])

  return {
    items: outcome?.data?.items ?? [],
    totalItems: outcome?.data?.totalItems ?? 0,
    totalPages: outcome?.data?.totalPages ?? 0,
    page,
    setPage,
    loading: outcome?.key !== requestKey,
    error: outcome?.error ?? null,
    reload,
    replace,
  }
}
