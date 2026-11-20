import { useEffect, useReducer } from 'react'
import { api } from '../lib/api'
import type { AdminAccess } from '../types'

export type AdminAccessState =
  | { status: 'checking' }
  | { status: 'failed' }
  | ({ status: 'ready' } & AdminAccess)

// One answer per Clerk user per page load, shared by the navigation and the route guard so
// both always agree and the server is asked once. Kept per user id, so signing in as
// someone else never reads the previous person's answer.
const answers = new Map<string, AdminAccess>()
const pending = new Set<string>()
const failed = new Set<string>()
const listeners = new Set<() => void>()

function load(userId: string) {
  if (answers.has(userId) || pending.has(userId)) return
  pending.add(userId)
  failed.delete(userId)
  api.get<AdminAccess>('/api/admin/access')
    .then((answer) => { answers.set(userId, answer) })
    // Not knowing is not the same as "not an administrator": it is reported as failed, and
    // the next caller (or retry) asks again.
    .catch(() => { failed.add(userId) })
    .finally(() => {
      pending.delete(userId)
      listeners.forEach((notify) => notify())
    })
}

/**
 * Whether the signed-in user is an administrator (#113).
 *
 * This decides what the UI shows and nothing else. Every administrator request is checked
 * again by the server, so changing this answer in the browser reveals an empty page whose
 * requests are all refused.
 *
 * @param userId the Clerk user to ask about; pass undefined until the session can call the API
 */
export function useAdminAccess(userId: string | null | undefined): AdminAccessState & { retry: () => void } {
  const [, rerender] = useReducer((count: number) => count + 1, 0)
  useEffect(() => {
    listeners.add(rerender)
    return () => { listeners.delete(rerender) }
  }, [])
  useEffect(() => {
    if (userId) load(userId)
  }, [userId])

  const retry = () => {
    if (!userId) return
    load(userId)
    rerender()
  }
  if (!userId) return { status: 'checking', retry }
  const answer = answers.get(userId)
  if (answer) return { status: 'ready', ...answer, retry }
  return { status: failed.has(userId) && !pending.has(userId) ? 'failed' : 'checking', retry }
}
