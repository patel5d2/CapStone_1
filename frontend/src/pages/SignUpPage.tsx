import { useEffect, useState, type FormEvent } from 'react'
import { useSignUp } from '@clerk/clerk-react'
import { Link, useNavigate } from 'react-router-dom'
import { Loader2, MailCheck } from 'lucide-react'
import { Wordmark } from '../components/ui/Wordmark'
import { isInstitutionalEmail, suggestUsername } from '../lib/institutionalEmail'

/** Clerk's configured minimum for this instance. */
const MIN_PASSWORD_LENGTH = 9

/** Clerk errors arrive as a list; the long message is the one written for a human. */
function messageOf(error: unknown, fallback: string) {
  const first = (error as { errors?: { longMessage?: string; message?: string }[] })?.errors?.[0]
  return first?.longMessage ?? first?.message ?? fallback
}

function paramOf(error: unknown) {
  return (error as { errors?: { meta?: { paramName?: string } }[] })?.errors?.[0]?.meta?.paramName
}

export default function SignUpPage() {
  const { isLoaded, signUp, setActive } = useSignUp()
  const navigate = useNavigate()

  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '' })
  const [code, setCode] = useState('')
  const [awaitingCode, setAwaitingCode] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [resendIn, setResendIn] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    if (resendIn <= 0) return
    const timer = window.setTimeout(() => setResendIn((remaining) => Math.max(0, remaining - 1)), 1000)
    return () => window.clearTimeout(timer)
  }, [resendIn])

  const resendCode = async () => {
    if (!isLoaded || busy || resendIn > 0) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await signUp.prepareEmailAddressVerification({ strategy: 'email_code' })
      setCode('')
      setNotice('Another code was requested. Check your university inbox and junk folder, and use the latest code.')
      setResendIn(30)
    } catch (err) {
      setError(messageOf(err, 'Could not resend the code. Please try again shortly.'))
      setResendIn(30)
    } finally {
      setBusy(false)
    }
  }

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [key]: e.target.value })

  /**
   * Everything is checked before `signUp.create`, so a personal address never becomes
   * a Clerk account at all. That is the whole point: the server would refuse the
   * account later, and being told at the end is worse than being told now.
   */
  const validate = () => {
    if (!form.firstName.trim()) return 'Enter your first name.'
    if (!form.lastName.trim()) return 'Enter your last name.'
    if (!isInstitutionalEmail(form.email)) {
      return 'Use your school email address — one ending in .edu. Personal addresses such as Gmail or Outlook cannot be used.'
    }
    if (form.password.length < MIN_PASSWORD_LENGTH) {
      return `Passwords must be at least ${MIN_PASSWORD_LENGTH} characters.`
    }
    return null
  }

  const submitDetails = async (e: FormEvent) => {
    e.preventDefault()
    if (!isLoaded || busy) return

    const problem = validate()
    if (problem) {
      setError(problem)
      return
    }

    setBusy(true)
    setError(null)
    const email = form.email.trim()

    try {
      const attempt = {
        emailAddress: email,
        password: form.password,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
      }

      const created = await signUp
        .create({ ...attempt, username: suggestUsername(email) })
        .catch((err) => {
          // Two students called j.smith at different schools collide on the derived
          // username. They never see it, so a suffix costs them nothing.
          if (paramOf(err) !== 'username') throw err
          return signUp.create({ ...attempt, username: `${suggestUsername(email)}${Math.floor(Math.random() * 10000)}` })
        })

      // Never activate an unverified address if the hosted signup settings change.
      if (created.status === 'complete') {
        if (created.verifications.emailAddress.status !== 'verified') {
          setError('Email verification is required. Contact the CampusBridge team to enable signup verification.')
          return
        }
        await setActive({
          session: created.createdSessionId,
          navigate: async ({ session }) => {
            if (!session?.currentTask) navigate('/profile', { replace: true })
          },
        })
        return
      }

      await signUp.prepareEmailAddressVerification({ strategy: 'email_code' })
      setAwaitingCode(true)
      setResendIn(30)
      setNotice(null)
    } catch (err) {
      setError(messageOf(err, 'Could not start sign-up. Check your details and try again.'))
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
      const result = await signUp.attemptEmailAddressVerification({ code: code.trim() })
      if (result.status === 'complete') {
        await setActive({
          session: result.createdSessionId,
          // Complete any Clerk task before creating the student profile.
          navigate: async ({ session }) => {
            if (!session?.currentTask) navigate('/profile', { replace: true })
          },
        })
      } else {
        setError(result.verifications.emailAddress.status === 'verified'
          ? 'Your email is verified, but account setup is incomplete. Contact the CampusBridge team to check the required signup fields.'
          : 'That code was not accepted. Check the email and try again.')
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
              <h1 className="text-xl font-extrabold tracking-tight">Check your school email</h1>
              <p className="text-sm text-[var(--color-ink-muted)]">
                We requested a verification code for <span className="font-semibold break-all">{form.email.trim()}</span>.
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
              {busy && <Loader2 className="h-4 w-4 animate-spin" />} Verify and continue
            </button>
            <p className="text-xs text-[var(--color-ink-muted)]">
              Allow a few minutes for delivery. Check Junk or Spam and your university email quarantine.
              If it is still missing, confirm your email address or request another code.
            </p>
            {notice && <p role="status" className="text-sm text-[var(--color-ink-muted)]">{notice}</p>}
            <button
              type="button"
              className="btn-secondary w-full"
              onClick={resendCode}
              disabled={!isLoaded || busy || resendIn > 0}
            >
              {resendIn > 0 ? `Resend code in ${resendIn}s` : 'Resend code'}
            </button>
            <button
              type="button"
              className="btn-ghost btn-sm w-full"
              disabled={busy}
              onClick={() => {
                setAwaitingCode(false)
                setError(null)
                setCode('')
                setNotice(null)
              }}
            >
              Use a different email
            </button>
          </form>
        ) : (
          <form onSubmit={submitDetails} className="space-y-4">
            <div className="text-center">
              {/* Hidden visually, not deleted: the wordmark above is a link, so removing
                  the heading outright would leave this page with none for a screen
                  reader to navigate by. */}
              <h1 className="sr-only">Join CampusBridge</h1>
              <p className="text-sm text-[var(--color-ink-muted)]">
                Create your account, verify your university email, then set up MFA.
              </p>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block">
                <span className="mb-1.5 block text-xs font-semibold">First name</span>
                <input className="field" autoComplete="given-name" value={form.firstName} onChange={set('firstName')} required />
              </label>
              <label className="block">
                <span className="mb-1.5 block text-xs font-semibold">Last name</span>
                <input className="field" autoComplete="family-name" value={form.lastName} onChange={set('lastName')} required />
              </label>
            </div>

            <label className="block">
              <span className="mb-1.5 block text-xs font-semibold">School email</span>
              <input
                className="field"
                type="email"
                autoComplete="email"
                placeholder="you@yourschool.edu"
                value={form.email}
                onChange={set('email')}
                required
              />
              <span className="mt-1 block text-xs text-[var(--color-ink-faint)]">
                University email ending in .edu only. No personal emails.
              </span>
            </label>

            <label className="block">
              <span className="mb-1.5 block text-xs font-semibold">Password</span>
              <input
                className="field"
                type="password"
                autoComplete="new-password"
                minLength={MIN_PASSWORD_LENGTH}
                value={form.password}
                onChange={set('password')}
                required
              />
              <span className="mt-1 block text-xs text-[var(--color-ink-faint)]">
                At least {MIN_PASSWORD_LENGTH} characters.
              </span>
            </label>

            {error && <p className="text-sm font-medium text-[var(--color-danger)]">{error}</p>}

            {/* Clerk's bot protection is on for this instance and renders itself here.
                Without this element sign-up fails with a captcha error. */}
            <div id="clerk-captcha" />

            <button className="btn-primary w-full" disabled={!isLoaded || busy}>
              {busy && <Loader2 className="h-4 w-4 animate-spin" />} Create account
            </button>

            <p className="text-center text-sm text-[var(--color-ink-muted)]">
              Already have an account?{' '}
              <Link to="/sign-in" className="font-semibold text-primary-600 hover:underline dark:text-primary-400">
                Sign in
              </Link>
            </p>
          </form>
        )}
      </div>
    </div>
  )
}
