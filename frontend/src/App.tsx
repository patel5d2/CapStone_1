import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { SignOutButton, useAuth, useClerk, useReverification, useSession, useUser } from '@clerk/clerk-react'
import { isReverificationCancelledError } from '@clerk/clerk-react/errors'
import { GraduationCap, ShieldCheck } from 'lucide-react'
import { AppShell } from './components/layout/AppShell'
import { setTokenGetter } from './lib/authToken'
import { isInstitutionalEmail } from './lib/institutionalEmail'
import { useProfileCheck } from './lib/profileGate'
import { applySchoolTheme, slugForSchool } from './lib/schoolTheme'
import { api, ApiError } from './lib/api'
import type { StudentAccountDetails } from './types'
import { Spinner } from './components/ui/Feedback'
import { SignUpSteps } from './components/ui/SignUpSteps'
import { Wordmark } from './components/ui/Wordmark'
import Landing from './pages/Landing'
import SignInPage from './pages/SignInPage'
import SignUpPage from './pages/SignUpPage'
import Marketplace from './pages/Marketplace'
import Messages from './pages/Messages'
import Community from './pages/Community'
import Support from './pages/Support'
import Profile from './pages/Profile'
import SetupMfaPage from './pages/SetupMfaPage'
import { AuthSessionGate } from './components/AuthSessionGate'

/** Keeps `lib/api.ts` supplied with a fresh Clerk session token. */
function AuthTokenBridge() {
  const { getToken } = useAuth()
  useEffect(() => {
    setTokenGetter(() => getToken())
    return () => setTokenGetter(null)
  }, [getToken])
  return null
}

/**
 * Objective 8: once a student is signed in, their profile's school picks the palette;
 * signing out (or having no profile yet) returns to the default. Rendered after
 * AuthTokenBridge so the token getter is in place when the profile is fetched.
 */
function SchoolThemeBridge() {
  const { isLoaded, isSignedIn, userId } = useAuth()
  useEffect(() => {
    if (!isLoaded) return
    if (!isSignedIn) {
      applySchoolTheme(null)
      return
    }
    let cancelled = false
    api.get<StudentAccountDetails>('/student/profile')
      .then((profile) => { if (!cancelled) applySchoolTheme(slugForSchool(profile.universityName)) })
      .catch((error: unknown) => {
        // No profile yet: no school to theme by. Any other failure keeps what is showing.
        if (!cancelled && error instanceof ApiError && error.status === 404) applySchoolTheme(null)
      })
    return () => { cancelled = true }
  }, [isLoaded, isSignedIn, userId])
  return null
}

/**
 * Sign-up refuses personal addresses before the account exists, so reaching this means
 * an account that predates the rule or one created outside that form. Either way every
 * API call it makes comes back 403, and saying so once is kinder than letting the four
 * tabs fill with failed requests.
 */
function NotInstitutional({ email }: { email?: string }) {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <div className="card flex flex-col items-center gap-4 px-6 py-12">
        <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-50 text-primary-600 dark:bg-primary-900/40 dark:text-primary-200">
          <GraduationCap className="h-6 w-6" />
        </span>
        <h1 className="text-xl font-extrabold tracking-tight">Use a valid email</h1>
        <p className="text-sm text-[var(--color-ink-muted)]">
          Your account needs a valid email address to use CampusBridge.
        </p>
        {email && (
          <p className="text-sm text-[var(--color-ink-muted)]">
            You are signed in as <span className="font-semibold break-all">{email}</span>, which is not one.
          </p>
        )}
        <SignOutButton>
          <button className="btn-primary">Sign out and use a valid email</button>
        </SignOutButton>
      </div>
    </div>
  )
}

/**
 * Mirrors `campusbridge.auth.require-two-factor` on the server. The server is what
 * refuses the request; this only decides whether the student is told why before the
 * four tabs fill with 403s. **Both must be flipped together** — the frontend alone
 * blocks nothing, and the backend alone is an unexplained wall.
 */
const REQUIRE_TWO_FACTOR = import.meta.env.VITE_REQUIRE_TWO_FACTOR !== 'false'

/**
 * A student with a school address who has not enrolled a second factor. Unlike the
 * institutional-email case there is a way out that does not involve signing out:
 * Clerk's own account UI can enrol the factor here and now.
 */
function TwoFactorRequired() {
  const { openUserProfile } = useClerk()
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <div className="card flex flex-col items-center gap-4 px-6 py-12">
        <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-50 text-primary-600 dark:bg-primary-900/40 dark:text-primary-200">
          <ShieldCheck className="h-6 w-6" />
        </span>
        <h1 className="text-xl font-extrabold tracking-tight">Add two-step verification</h1>
        <p className="text-sm text-[var(--color-ink-muted)]">
          CampusBridge requires a second factor on every student account. Add one to your account and
          you will be straight back in — you do not need to sign out.
        </p>
        <button className="btn-primary" onClick={() => openUserProfile()}>
          Set up two-step verification
        </button>
        <p className="text-xs text-[var(--color-ink-faint)]">
          Opens your account settings, under Security.
        </p>
      </div>
    </div>
  )
}

/** Clerk's reverification hint (the shape `reverificationError()` builds), for its modal. */
const SECOND_FACTOR_HINT = {
  clerk_error: {
    type: 'forbidden',
    reason: 'reverification-error',
    metadata: { reverification: { level: 'second_factor', afterMinutes: 10 } },
  },
}

/** The session has never used its second factor: `fva` second element is -1. */
function secondFactorUnverified(age: [number, number] | null | undefined) {
  return Array.isArray(age) && age[1] < 0
}

/**
 * Sign-up enrols the second factor but never asks for it, so the new session's token says
 * "second factor never verified" and the API refuses every request (the server requires a
 * verified one, `InstitutionalAccessPolicy`). Sign-in never lands here: Clerk asks for the
 * factor there. Ask once, in Clerk's own verification modal, then continue.
 */
function ConfirmSecondFactor({ signup = false }: { signup?: boolean }) {
  const clerk = useClerk()
  const { getToken } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const opened = useRef(false)
  const verify = useReverification(async () => {
    if (secondFactorUnverified(clerk.session?.factorVerificationAge)) return SECOND_FACTOR_HINT
    // A fresh token, so the very next API call carries the updated claim.
    await getToken({ skipCache: true })
    return true
  })
  const start = async () => {
    setError(null)
    try {
      await verify()
    } catch (e) {
      if (!isReverificationCancelledError(e)) setError('That did not work. Try again, or sign out and sign in.')
    }
  }
  useEffect(() => {
    if (opened.current) return
    opened.current = true
    void start()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return (
    <div className="mx-auto max-w-md px-4 py-16 text-center">
      {signup && <div className="mb-6"><SignUpSteps current={3} /></div>}
      <div className="card flex flex-col items-center gap-4 px-6 py-12">
        <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-50 text-primary-600 dark:bg-primary-900/40 dark:text-primary-200">
          <ShieldCheck className="h-6 w-6" />
        </span>
        <h1 className="text-xl font-extrabold tracking-tight">Confirm your two-step verification</h1>
        <p className="text-sm text-[var(--color-ink-muted)]">
          You just set it up. Enter one code from it to confirm it works, and you are in.
        </p>
        {error && <p role="alert" className="text-sm text-[var(--color-danger)]">{error}</p>}
        <button className="btn-primary" onClick={() => void start()}>Verify now</button>
        <SignOutButton>
          <button className="btn-ghost btn-sm">Sign out</button>
        </SignOutButton>
      </div>
    </div>
  )
}

/**
 * The check for a student profile did not come back with one. Not knowing is not the same
 * as registered, so the app stays closed. Usually the way forward is to ask again; without
 * `onRetry` the server gave a firm answer (the profile on this email belongs to another
 * Clerk account) and asking again would only repeat it.
 */
function AccountCheckFailed({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="mx-auto max-w-md px-4 py-16 text-center">
      <div className="card flex flex-col items-center gap-4 px-6 py-12">
        <h1 className="text-xl font-extrabold tracking-tight">
          {onRetry ? 'We could not check your account' : 'This email already has a profile'}
        </h1>
        <p role="alert" className="text-sm text-[var(--color-ink-muted)]">{message}</p>
        {onRetry && <button className="btn-primary" onClick={onRetry}>Try again</button>}
        <SignOutButton>
          <button className="btn-ghost btn-sm">Sign out</button>
        </SignOutButton>
      </div>
    </div>
  )
}

/**
 * Signed in, allowed, and registered: the student account/profile exists. Creating it is
 * the last step of sign-up (#114), so a student without one is sent to /sign-up/profile
 * from every page and never sees the app. `signupStep` is that page's own gate, the
 * inverse: it admits only a student with no profile yet and sends everyone else into the
 * app, so a registered student is never asked to set up again.
 */
export function RequireAuth({ children, signupStep = false }: { children: ReactNode; signupStep?: boolean }) {
  const { isLoaded, isSignedIn } = useAuth()
  const { isLoaded: sessionLoaded, session } = useSession()
  const { user } = useUser()
  const needsSecondFactor = REQUIRE_TWO_FACTOR && Boolean(user?.twoFactorEnabled)
    && secondFactorUnverified(session?.factorVerificationAge)
  // Held back until the second factor is verified: before then every API call is refused,
  // and the profile check would read that refusal as "profile exists".
  const profile = useProfileCheck(isSignedIn && !needsSecondFactor ? user?.id : undefined)
  if (!isLoaded || !sessionLoaded) return <Spinner />
  if (session?.currentTask?.key === 'setup-mfa') {
    return <Navigate to="/session-tasks/setup-mfa" replace />
  }
  if (!isSignedIn) return <Navigate to="/sign-in" replace />

  const email = user?.primaryEmailAddress?.emailAddress
  // While Clerk is still hydrating the user there is no email to judge; showing the
  // rejection then would flash it at students who are perfectly entitled to be here.
  if (user && !isInstitutionalEmail(email)) return <NotInstitutional email={email} />
  if (REQUIRE_TWO_FACTOR && user && !user.twoFactorEnabled) return <TwoFactorRequired />
  if (needsSecondFactor) return <ConfirmSecondFactor signup={signupStep} />
  if (profile.status === 'checking') return <Spinner />
  if (profile.status === 'failed') {
    return <AccountCheckFailed message={profile.message} onRetry={profile.retryable ? profile.retry : undefined} />
  }
  if (signupStep && profile.status === 'complete') return <Navigate to="/marketplace" replace />
  if (!signupStep && profile.status === 'missing') return <Navigate to="/sign-up/profile" replace />

  return <>{children}</>
}

/**
 * The last step of sign-up, outside AppShell on purpose: the app's tabs are not offered
 * to a student whose account is not finished. With no avatar menu here, signing out needs
 * its own button.
 */
function SignUpProfileStep() {
  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-8">
      <div className="mb-6 flex items-center justify-between gap-3">
        <Wordmark />
        <SignOutButton>
          <button className="btn-ghost btn-sm">Sign out</button>
        </SignOutButton>
      </div>
      <Profile signup />
    </div>
  )
}

export default function App() {
  return (
    <>
      <AuthTokenBridge />
      <SchoolThemeBridge />
      <Routes>
        {/* Outside AppShell and outside RequireAuth on purpose: a session pending on
            setup-mfa is signed in but not active, and RequireAuth's two-factor gate
            would redirect away from the one page that can clear it. */}
        <Route path="/session-tasks/setup-mfa" element={<SetupMfaPage />} />
        <Route path="/sign-in/*" element={<AuthSessionGate><SignInPage /></AuthSessionGate>} />
        <Route path="/sign-up/*" element={<AuthSessionGate><SignUpPage /></AuthSessionGate>} />
        <Route path="/sign-up/profile" element={<RequireAuth signupStep><SignUpProfileStep /></RequireAuth>} />
        <Route element={<AppShell />}>
          <Route path="/" element={<Landing />} />
          <Route
            path="/marketplace"
            element={
              <RequireAuth>
                <Marketplace />
              </RequireAuth>
            }
          />
          <Route
            path="/messages"
            element={
              <RequireAuth>
                <Messages />
              </RequireAuth>
            }
          />
          <Route
            path="/community"
            element={
              <RequireAuth>
                <Community />
              </RequireAuth>
            }
          />
          <Route
            path="/support"
            element={
              <RequireAuth>
                <Support />
              </RequireAuth>
            }
          />
          {/* The directory moved inside Community; keep old links working. */}
          <Route path="/directory" element={<Navigate to="/community?tab=directory" replace />} />
          <Route
            path="/profile"
            element={
              <RequireAuth>
                <Profile />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </>
  )
}
