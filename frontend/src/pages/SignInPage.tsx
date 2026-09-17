import { useState, type FormEvent } from 'react'
import { useSignIn } from '@clerk/clerk-react'
import { Link, useNavigate } from 'react-router-dom'
import { Loader2, MailCheck } from 'lucide-react'
import { Wordmark } from '../components/ui/Wordmark'
import { isInstitutionalEmail } from '../lib/institutionalEmail'

/** Clerk errors arrive as a list; the long message is the one written for a human. */
function messageOf(error: unknown, fallback: string) {
  const first = (error as { errors?: { longMessage?: string; message?: string }[] })?.errors?.[0]
  return first?.longMessage ?? first?.message ?? fallback
}

/**
 * Sign-in is an emailed one-time code, every time — no password step.
 *
 * Clerk treats `email_code` as a FIRST factor, so this replaces the password rather
 * than adding to it. It is not two-factor authentication: both the code and the account
 * live in the same mailbox, so anyone with the mailbox has everything. Objective 1 still
 * needs a genuine second factor (TOTP) layered on top of this.
 */
export default function SignInPage() {
  const { isLoaded, signIn, setActive } = useSignIn()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [awaitingCode, setAwaitingCode] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const sendCode = async (e: FormEvent) => {
    e.preventDefault()
    if (!isLoaded || busy) return

    // Checked before Clerk is touched, so a personal address never even triggers an
    // email. The API would refuse the account anyway; this says so while it still helps.
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

      const factor = attempt.supportedFirstFactors?.find((f) => f.strategy === 'email_code')
      if (!factor || !('emailAddressId' in factor)) {
        // Someone turned the email-code strategy off in the Clerk dashboard. Say that,
        // rather than showing a code box that can never be filled.
        setError('Email sign-in codes are not enabled for this app. Contact the CampusBridge team.')
        return
      }

      await signIn.prepareFirstFactor({ strategy: 'email_code', emailAddressId: factor.emailAddressId })
      setAwaitingCode(true)
    } catch (err) {
      setError(messageOf(err, 'Could not send a code. Check the address and try again.'))
    } finally {
      setBusy(false)
    }
  }

  const submitCode = async (e: FormEvent) => {
    e.preventDefault()
    if (!isLoaded || busy) return

    setBusy(true)
    setError(null)
    try {
      const result = await signIn.attemptFirstFactor({ strategy: 'email_code', code: code.trim() })
      if (result.status === 'complete') {
        await setActive({
          session: result.createdSessionId,
          // Clerk's taskUrls takes priority over this callback for pending tasks.
          navigate: async ({ session }) => {
            if (!session?.currentTask) navigate('/marketplace', { replace: true })
          },
        })
      } else {
        // status is 'needs_second_factor' once TOTP is enabled; Clerk's own UI handles
        // that today, so send them there rather than half-implementing it here.
        setError('This account needs another verification step. Contact the CampusBridge team.')
      }
    } catch (err) {
      setError(messageOf(err, 'That code was not accepted.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-6 px-4 py-12">
      <Wordmark />

      <div className="card w-full max-w-md p-6">
        {awaitingCode ? (
          <form onSubmit={submitCode} className="space-y-4">
            <div className="flex flex-col items-center gap-2 text-center">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary-50 text-primary-600 dark:bg-primary-900/40 dark:text-primary-200">
                <MailCheck className="h-5 w-5" />
              </span>
              <h1 className="text-xl font-extrabold tracking-tight">Enter your verification code</h1>
              <p className="text-sm text-[var(--color-ink-muted)]">
                We sent a code to <span className="font-semibold break-all">{email.trim()}</span>.
              </p>
            </div>

            <input
              className="field text-center text-lg tracking-[0.3em]"
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder="000000"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              aria-label="Verification code"
            />

            {error && <p className="text-sm font-medium text-[var(--color-danger)]">{error}</p>}

            <button className="btn-primary w-full" disabled={busy || !code.trim()}>
              {busy && <Loader2 className="h-4 w-4 animate-spin" />} Verify and sign in
            </button>
            <button
              type="button"
              className="btn-ghost btn-sm w-full"
              onClick={() => {
                setAwaitingCode(false)
                setCode('')
                setError(null)
              }}
            >
              Use a different email
            </button>
          </form>
        ) : (
          <form onSubmit={sendCode} className="space-y-4">
            <div className="text-center">
              <h1 className="text-xl font-extrabold tracking-tight">Sign in to CampusBridge</h1>
              <p className="mt-1 text-sm text-[var(--color-ink-muted)]">
                We email you a verification code each time — there is no password to remember.
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

            {error && <p className="text-sm font-medium text-[var(--color-danger)]">{error}</p>}

            <button className="btn-primary w-full" disabled={!isLoaded || busy}>
              {busy && <Loader2 className="h-4 w-4 animate-spin" />} Email me a code
            </button>

            <p className="text-center text-sm text-[var(--color-ink-muted)]">
              New here?{' '}
              <Link to="/sign-up" className="font-semibold text-primary-600 hover:underline dark:text-primary-400">
                Create an account
              </Link>
            </p>
          </form>
        )}
      </div>
    </div>
  )
}
