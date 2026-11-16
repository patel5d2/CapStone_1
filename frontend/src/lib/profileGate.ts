import { useEffect, useState } from 'react'
import { api, ApiError } from './api'

// Clerk user whose profile is known to exist, so the check runs once per user per page load.
let completeFor: string | null = null

export function markProfileComplete(userId: string | undefined) {
  completeFor = userId ?? null
}

export type ProfileCheck =
  | { status: 'checking' | 'missing' | 'complete' }
  | { status: 'failed'; message: string; retryable: boolean }

/**
 * Whether this student is registered, meaning their profile exists. Only the server's 404
 * means "missing". Any other failure is `failed`, not "registered": letting it through
 * would put a student with no profile inside the app whenever the check hiccups, so the
 * caller offers a retry instead (#114).
 *
 * A 409 is the one failure asking again cannot change: the profile on this address is
 * bound to another Clerk account (`StudentIdentityService`), which happens when an account
 * is deleted and made again with the same email. Only a person can settle whose it is.
 */
export function useProfileCheck(userId: string | undefined): ProfileCheck & { retry: () => void } {
  // Kept with the user it is about, so one student's answer is never read for the next.
  const [result, setResult] = useState<{ userId: string; check: ProfileCheck } | null>(null)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (!userId || completeFor === userId) return
    let cancelled = false
    api.get('/student/profile')
      .then(() => {
        completeFor = userId
        if (!cancelled) setResult({ userId, check: { status: 'complete' } })
      })
      .catch((e: unknown) => {
        if (cancelled) return
        // Anything that is not the server's own answer is the network.
        const refusal = e instanceof ApiError ? e : null
        setResult({
          userId,
          check: refusal?.status === 404
            ? { status: 'missing' }
            : {
                status: 'failed',
                message: refusal?.message ?? 'CampusBridge could not be reached. Check your connection.',
                retryable: refusal?.status !== 409,
              },
        })
      })
    return () => { cancelled = true }
  }, [userId, attempt])
  const retry = () => {
    setResult(null)
    setAttempt((count) => count + 1)
  }
  if (userId && completeFor === userId) return { status: 'complete', retry }
  return { ...(result && result.userId === userId ? result.check : { status: 'checking' as const }), retry }
}

/**
 * Whether the server already holds this student's profile. Asked when creating one fails,
 * to tell a repeat of a save that did go through apart from a real failure.
 */
export function profileExists(): Promise<boolean> {
  return api.get('/student/profile').then(() => true, () => false)
}
