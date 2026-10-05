import type { ReactNode } from 'react'
import { SignOutButton, useSession } from '@clerk/clerk-react'
import { Navigate } from 'react-router-dom'
import { Spinner } from './ui/Feedback'

/** Pending sessions still exist in Clerk and must not start another sign-in. */
export function AuthSessionGate({ children }: { children: ReactNode }) {
  const { isLoaded, session } = useSession()
  if (!isLoaded) return <Spinner />
  if (session?.currentTask?.key === 'setup-mfa') {
    return <Navigate to="/session-tasks/setup-mfa" replace />
  }
  if (session?.status === 'active') return <Navigate to="/marketplace" replace />
  // Any other pending task (e.g. choose-organization when Clerk's "membership required"
  // is on) has no page here. Clerk counts pending as signed out, so RequireAuth lands
  // here, and the form below would only fail with "already signed in". Offer the way out.
  if (session?.status === 'pending') {
    return (
      <div className="flex min-h-full flex-col items-center justify-center px-4 py-12">
        <div className="card flex w-full max-w-md flex-col items-center gap-4 p-6 text-center">
          <h1 className="text-xl font-extrabold tracking-tight">Account setup is incomplete</h1>
          <p className="text-sm text-[var(--color-ink-muted)]">
            Clerk is asking for a step this app cannot complete
            ({session.currentTask?.key ?? 'unknown'}). Every sign-in will stop here until the CampusBridge
            team turns that step off in Clerk. Sign out for now.
          </p>
          <SignOutButton>
            <button className="btn-primary">Sign out</button>
          </SignOutButton>
        </div>
      </div>
    )
  }
  return <>{children}</>
}
