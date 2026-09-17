import { useState, type FormEvent } from 'react'
import { useSignUp } from '@clerk/clerk-react'
import { Link, useNavigate } from 'react-router-dom'
import { Loader2, MailCheck } from 'lucide-react'
import { Wordmark } from '../components/ui/Wordmark'
import { isInstitutionalEmail, suggestUsername } from '../lib/institutionalEmail'

/** Clerk's configured minimum for this instance. */
const MIN_PASSWORD_LENGTH = 15

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

      try {
        await signUp.create({ ...attempt, username: suggestUsername(email) })
      } catch (err) {
        // Two students called j.smith at different schools collide on the derived
        // username. They never see it, so a suffix costs them nothing.
        if (paramOf(err) !== 'username') throw err
        await signUp.create({ ...attempt, username: `${suggestUsername(email)}${Math.floor(Math.random() * 10000)}` })
      }

      await signUp.prepareEmailAddressVerification({ strategy: 'email_code' })
      setAwaitingCode(true)
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
        setError('That code was not accepted. Check the email and try again.')
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
                We sent a code to <span className="font-semibold break-all">{form.email.trim()}</span>.
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
            <button
              type="button"
              className="btn-ghost btn-sm w-full"
              onClick={() => {
                setAwaitingCode(false)
                setError(null)
              }}
            >
              Use a different email
            </button>
          </form>
        ) : (
          <form onSubmit={submitDetails} className="space-y-4">
            <div className="text-center">
              <h1 className="text-xl font-extrabold tracking-tight">Join CampusBridge</h1>
              <p className="mt-1 text-sm text-[var(--color-ink-muted)]">
                For verified students at Cincinnati-area schools.
              </p>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block">
                <span className="mb-1.5 block text-xs font-semibold">First name</span>
                <input className="field" value={form.firstName} onChange={set('firstName')} required />
              </label>
              <label className="block">
                <span className="mb-1.5 block text-xs font-semibold">Last name</span>
                <input className="field" value={form.lastName} onChange={set('lastName')} required />
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
                Must end in .edu — this is how we verify you are a student.
              </span>
            </label>

            <label className="block">
              <span className="mb-1.5 block text-xs font-semibold">Password</span>
              <input
                className="field"
                type="password"
                autoComplete="new-password"
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
