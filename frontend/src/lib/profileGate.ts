import { useEffect, useState } from 'react'
import { api, ApiError } from './api'

// Clerk user whose profile is known to exist, so the check runs once per user per page load.
let completeFor: string | null = null

export function markProfileComplete(userId: string | undefined) {
  completeFor = userId ?? null
}

/**
 * True when the student has not completed their profile yet, false when they have,
 * null while checking. Only the server's 404 counts as "missing": any other failure
 * lets the page load and report its own error rather than trapping the student here.
 */
export function useProfileMissing(userId: string | undefined): boolean | null {
  const known = !!userId && completeFor === userId
  const [missing, setMissing] = useState<boolean | null>(null)
  useEffect(() => {
    if (!userId || completeFor === userId) return
    let cancelled = false
    api.get('/student/profile')
      .then(() => {
        completeFor = userId
        if (!cancelled) setMissing(false)
      })
      .catch((e: unknown) => {
        if (!cancelled) setMissing(e instanceof ApiError && e.status === 404)
      })
    return () => { cancelled = true }
  }, [userId])
  return known ? false : missing
}
