import { useState, type FormEvent } from 'react'
import { SignIn, useSignIn } from '@clerk/clerk-react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { Wordmark } from '../components/ui/Wordmark'
import { isInstitutionalEmail } from '../lib/institutionalEmail'

/** Clerk errors arrive as a list; the long message is the one written for a human. */
function messageOf(error: unknown, fallback: string) {
  const first = (error as { errors?: { longMessage?: string; message?: string }[] })?.errors?.[0]
  return first?.longMessage ?? first?.message ?? fallback
}

/**
 * Sign-in is school email + password, with the second factor handled by Clerk.
 *
 * ⚠️ **This does not work until the Clerk dashboard enables password as a first factor.**
 * Checked against the live instance on 2026-09-18: `password.enabled` and
 * `password.required` are both true — a password is collected at sign-up — but
 * `password.used_for_first_factor` is **false**, so the password cannot be used to sign
 * in. Until someone flips that, `signIn.create` reports no password factor and this page
 * says so rather than showing a field that can never work. Do not merge ahead of the
 * dashboard change: it replaces the `email_code` flow that is currently the only way in.
 *
 * The second factor is not implemented here and does not need to be. When Clerk answers
 * `needs_second_factor`, the attempt is handed to Clerk's own `<SignIn />`, which prompts
 * for the authenticator code or a backup code (decision 015). Email is deliberately not
 * an option: Clerk does not offer it as a second factor
 * (`email_address.second_factors: []`), and a code sent to the mailbox that already
 * receives account mail would not be an independent factor anyway.
 */
export default function SignInPage() {
  const { isLoaded, signIn, setActive } = useSignIn()
  const navigate = useNavigate()
  const { pathname } = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [continueWithClerk, setContinueWithClerk] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!isLoaded || busy) return

    // Checked before Clerk is touched. The API would refuse the account anyway; this
    // says so while it is still useful.
    if (!isInstitutionalEmail(email)) {
      setError(
        'Use your school email address — one ending in .edu. Personal addresses such as Gmail or Outlook cannot be used.',
      )
      return
    }

    setBusy(true)
    setError(null)
    try {
      const attempt = await signIn.create({ identifier: email.trim() })

      if (!attempt.supportedFirstFactors?.some((f) => f.strategy === 'password')) {
        // Password is not enabled as a first factor on the Clerk instance. Say that,
        // rather than rejecting a password the instance was never going to check.
        setError('Password sign-in is not enabled for this app yet. Contact the CampusBridge team.')
        return
      }

      const result = await signIn.attemptFirstFactor({ strategy: 'password', password })

      if (result.status === 'complete') {
        await setActive({
          session: result.createdSessionId,
          // Clerk's taskUrls takes priority over this callback for pending tasks.
          navigate: async ({ session }) => {
            if (!session?.currentTask) navigate('/marketplace', { replace: true })
          },
        })
      } else {
        // needs_second_factor, account recovery, or a required password reset. Keep the
        // same Clerk attempt and let Clerk's UI finish it.
        setContinueWithClerk(true)
      }
    } catch (err) {
      setError(messageOf(err, 'That email and password did not match an account.'))
    } finally {
      setBusy(false)
    }
  }

  // Resume an unfinished attempt after a reload as well as after the first factor.
  const step = isLoaded && signIn.status === 'needs_second_factor' ? 'factor-two'
    : isLoaded && signIn.status === 'needs_new_password' ? 'reset-password' : null
  if (continueWithClerk || step !== null || pathname.startsWith('/sign-in/')) {
    if (step && (pathname === '/sign-in' || pathname === '/sign-in/')) {
      return <Navigate to={`/sign-in/${step}`} replace />
    }
    return (
      <div className="flex min-h-full flex-col items-center justify-center gap-6 px-4 py-12">
        <Wordmark />
        <SignIn routing="path" path="/sign-in" forceRedirectUrl="/marketplace" signUpUrl="/sign-up" />
      </div>
    )
  }

  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-6 px-4 py-12">
      <Wordmark />

      <div className="card w-full max-w-md p-6">
        <form onSubmit={submit} className="space-y-4">
          <div className="text-center">
            <h1 className="text-xl font-extrabold tracking-tight">Sign in to CampusBridge</h1>
            <p className="mt-1 text-sm text-[var(--color-ink-muted)]">
              Use your school email and password. If your account has two-step
              verification, we ask for your code next.
            </p>
          </div>

          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold">School email</span>
            <input
              className="field"
              type="email"
              autoComplete="email"
              placeholder="you@yourschool.edu"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </label>

          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold">Password</span>
            <input
              className="field"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>

          {error && <p className="text-sm font-medium text-[var(--color-danger)]">{error}</p>}

          <button className="btn-primary w-full" disabled={!isLoaded || busy || !password}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" />} Sign in
          </button>

          {/* The emailed-code sign-in is gone, so this is the only way back in after a
              forgotten password. Clerk's own UI owns the reset. */}
          <button
            type="button"
            className="btn-ghost btn-sm w-full"
            onClick={() => setContinueWithClerk(true)}
          >
            Forgot your password?
          </button>

          <p className="text-center text-sm text-[var(--color-ink-muted)]">
            New here?{' '}
            <Link to="/sign-up" className="font-semibold text-primary-600 hover:underline dark:text-primary-400">
              Create an account
            </Link>
          </p>
        </form>
      </div>
    </div>
  )
}
