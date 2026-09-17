import { useEffect, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { SignOutButton, useAuth, useClerk, useUser } from '@clerk/clerk-react'
import { GraduationCap, ShieldCheck } from 'lucide-react'
import { AppShell } from './components/layout/AppShell'
import { setTokenGetter } from './lib/authToken'
import { isInstitutionalEmail } from './lib/institutionalEmail'
import { Spinner } from './components/ui/Feedback'
import Landing from './pages/Landing'
import SignInPage from './pages/SignInPage'
import SignUpPage from './pages/SignUpPage'
import Marketplace from './pages/Marketplace'
import Messages from './pages/Messages'
import Community from './pages/Community'
import Support from './pages/Support'
import Profile from './pages/Profile'

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
        <h1 className="text-xl font-extrabold tracking-tight">Use your school email</h1>
        <p className="text-sm text-[var(--color-ink-muted)]">
          CampusBridge is for verified students, so accounts have to be on a school address ending in{' '}
          <span className="font-semibold">.edu</span>.
        </p>
        {email && (
          <p className="text-sm text-[var(--color-ink-muted)]">
            You are signed in as <span className="font-semibold break-all">{email}</span>, which is not one.
          </p>
        )}
        <SignOutButton>
          <button className="btn-primary">Sign out and use a .edu address</button>
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
const REQUIRE_TWO_FACTOR = import.meta.env.VITE_REQUIRE_TWO_FACTOR === 'true'

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

function RequireAuth({ children }: { children: ReactNode }) {
  const { isLoaded, isSignedIn } = useAuth()
  const { user } = useUser()
  if (!isLoaded) return <Spinner />
  if (!isSignedIn) return <Navigate to="/sign-in" replace />

  const email = user?.primaryEmailAddress?.emailAddress
  // While Clerk is still hydrating the user there is no email to judge; showing the
  // rejection then would flash it at students who are perfectly entitled to be here.
  if (user && !isInstitutionalEmail(email)) return <NotInstitutional email={email} />
  if (REQUIRE_TWO_FACTOR && user && !user.twoFactorEnabled) return <TwoFactorRequired />

  return <>{children}</>
}

export default function App() {
  return (
    <>
      <AuthTokenBridge />
      <Routes>
        <Route path="/sign-in/*" element={<SignInPage />} />
        <Route path="/sign-up/*" element={<SignUpPage />} />
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
