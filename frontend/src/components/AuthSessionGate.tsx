import type { ReactNode } from 'react'
import { useSession } from '@clerk/clerk-react'
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
  return <>{children}</>
}
