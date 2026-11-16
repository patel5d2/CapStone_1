import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useSignUp } from '@clerk/clerk-react'
import { Link, useNavigate } from 'react-router-dom'
import { Loader2, MailCheck } from 'lucide-react'
import { PasswordField } from '../components/ui/PasswordField'
import { SignUpSteps } from '../components/ui/SignUpSteps'
import { Wordmark } from '../components/ui/Wordmark'
import { isInstitutionalEmail } from '../lib/institutionalEmail'
import { SCHOOL_NAMES } from '../lib/schoolTheme'
import { GRADES, SIGNUP_PROFILE_KEY, signupProfileProblem, US_STATES, type SignupProfile } from '../lib/signupProfile'

/** Clerk's configured minimum for this instance. */
const MIN_PASSWORD_LENGTH = 9
/** Clerk's emailed codes are six digits. */
const CODE_LENGTH = 6

const EMPTY = {
  firstName: '', lastName: '', email: '', password: '',
  universityName: '', grade: GRADES[0], major: '', residentCity: '', residentState: '',
}
type FieldName = keyof typeof EMPTY

interface ClerkError { code?: string; longMessage?: string; message?: string; meta?: { paramName?: string } }

/** Clerk errors arrive as a list; the first is the one to show. */
function firstError(error: unknown) {
  return (error as { errors?: ClerkError[] })?.errors?.[0]
}

/** The long message is the one written for a human. */
function messageOf(error: unknown, fallback: string) {
  const first = firstError(error)
  return first?.longMessage ?? first?.message ?? fallback
}

/** Clerk names the parameter it refused; this is the field on the form it came from. */
const FIELD_OF_PARAM: Record<string, FieldName> = {
  first_name: 'firstName', last_name: 'lastName', email_address: 'email', password: 'password',
}

/**
 * A labelled control. The label points at the control instead of wrapping it: wrapped
 * around a select, a label takes in every option's text, so the School picker would also
 * answer to "State" (Cincinnati State is one of its options).
 */
function Field({ id, label, children }: { id: string; label: string; children: ReactNode }) {
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-xs font-semibold">{label}</label>
      {children}
    </div>
  )
}

export default function SignUpPage() {
  const { isLoaded, signUp, setActive } = useSignUp()
  const navigate = useNavigate()

  const [form, setForm] = useState(EMPTY)
  const [code, setCode] = useState('')
  const [awaitingCode, setAwaitingCode] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [resendIn, setResendIn] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [startedOver, setStartedOver] = useState(false)
  // The field `error` is about, so it shows beside that field; null when it is about the whole form.
  const [errorField, setErrorField] = useState<FieldName | null>(null)

  // A reload in the middle of verifying loses this page's state but not Clerk's sign-up,
  // which still holds the details and is still waiting for the code. Carry on from it
  // instead of asking for everything again.
  const unverifiedEmail = isLoaded && !startedOver && signUp.status === 'missing_requirements'
    && signUp.unverifiedFields?.includes('email_address') ? signUp.emailAddress : null
  const verifying = awaitingCode || Boolean(unverifiedEmail)
  const emailShown = form.email.trim() || unverifiedEmail || ''

  useEffect(() => {
    if (resendIn <= 0) return
    const timer = window.setTimeout(() => setResendIn((remaining) => Math.max(0, remaining - 1)), 1000)
    return () => window.clearTimeout(timer)
  }, [resendIn])

  /**
   * Shows a problem, beside its field when it has one, and takes the student there: the
   * form is long enough that on a phone the field can be a screen away from the button.
   */
  const report = (message: string | null, field: FieldName | null = null) => {
    setError(message)
    setErrorField(field)
    if (field && typeof document !== 'undefined') document.getElementById(`signup-${field}`)?.focus()
  }

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

  const set = (key: FieldName) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setForm({ ...form, [key]: e.target.value })
    if (errorField === key) report(null)
  }

  /** The profile details as they will be saved: trimmed, like the name. */
  const profile = (): SignupProfile => ({
    universityName: form.universityName,
    grade: form.grade,
    major: form.major.trim(),
    residentCity: form.residentCity.trim(),
    residentState: form.residentState,
  })

  /**
   * Everything is checked before `signUp.create`, so details the server would refuse
   * never become a Clerk account at all. That is the whole point: being told at the end,
   * with an account already made, is worse than being told now.
   */
  const validate = (): { field: FieldName; message: string } | null => {
    if (!form.firstName.trim()) return { field: 'firstName', message: 'Enter your first name.' }
    if (!form.lastName.trim()) return { field: 'lastName', message: 'Enter your last name.' }
    if (!isInstitutionalEmail(form.email)) return { field: 'email', message: 'Enter a valid email address.' }
    if (form.password.length < MIN_PASSWORD_LENGTH) {
      return { field: 'password', message: `Passwords must be at least ${MIN_PASSWORD_LENGTH} characters.` }
    }
    return signupProfileProblem(profile())
  }

  const submitDetails = async (e: FormEvent) => {
    e.preventDefault()
    if (!isLoaded || busy) return

    const problem = validate()
    if (problem) {
      report(problem.message, problem.field)
      return
    }

    setBusy(true)
    report(null)
    const email = form.email.trim()

    try {
      const attempt = {
        emailAddress: email,
        password: form.password,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        // Carried on the new user until the last step turns them into the student profile.
        unsafeMetadata: { [SIGNUP_PROFILE_KEY]: profile() },
      }

      // Usernames are off on the Clerk instance; sending one is rejected as an unknown param.
      const created = await signUp.create(attempt)

      // Never activate an unverified address if the hosted signup settings change.
      if (created.status === 'complete') {
        if (created.verifications.emailAddress.status !== 'verified') {
          setError('Email verification is required. Contact the CampusBridge team to enable signup verification.')
          return
        }
        await setActive({
          session: created.createdSessionId,
          navigate: async ({ session }) => {
            if (!session?.currentTask) navigate('/sign-up/profile', { replace: true })
          },
        })
        return
      }

      await signUp.prepareEmailAddressVerification({ strategy: 'email_code' })
      setAwaitingCode(true)
      setStartedOver(false)
      setResendIn(30)
      setNotice(null)
    } catch (err) {
      const first = firstError(err)
      report(
        // Clerk's own wording stops at "taken"; the way forward is on this page.
        first?.code === 'form_identifier_exists'
          ? 'An account already uses that email. Sign in instead, or use a different email.'
          : messageOf(err, 'Could not start sign-up. Check your details and try again.'),
        FIELD_OF_PARAM[first?.meta?.paramName ?? ''] ?? null,
      )
    } finally {
      setBusy(false)
    }
  }

  const verifyCode = async (entered: string) => {
    if (!isLoaded || busy) return

    setBusy(true)
    setError(null)
    try {
      // A second click after the code already went through fails with
      // verification_already_verified; carry on from the sign-up as it stands.
      const result = await signUp.attemptEmailAddressVerification({ code: entered.trim() }).catch((err) => {
        if (firstError(err)?.code === 'verification_already_verified') return signUp
        throw err
      })
      if (result.status === 'complete') {
        await setActive({
          session: result.createdSessionId,
          // Complete any Clerk task before the last step, creating the student profile.
          navigate: async ({ session }) => {
            if (!session?.currentTask) navigate('/sign-up/profile', { replace: true })
          },
        })
      } else {
        setError(result.verifications.emailAddress.status === 'verified'
          ? `Your email is verified, but Clerk still needs: ${result.missingFields.join(', ') || 'unknown fields'}. Contact the CampusBridge team to check the required signup fields.`
          : 'That code was not accepted. Check the email and try again.')
      }
    } catch (err) {
      setError(messageOf(err, 'That code was not accepted.'))
    } finally {
      setBusy(false)
    }
  }

  const submitCode = (e: FormEvent) => {
    e.preventDefault()
    return verifyCode(code)
  }

  const errorFor = (key: FieldName) => errorField === key && error
    ? <span role="alert" className="mt-1 block text-xs font-medium text-[var(--color-danger)]">{error}</span>
    : null
  const control = (key: FieldName) => ({
    id: `signup-${key}`, className: 'field', value: form[key], onChange: set(key),
    'aria-invalid': errorField === key || undefined, required: true,
  })

  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-6 px-4 py-12">
      <Wordmark />
      <SignUpSteps current={verifying ? 2 : 1} />

      <div className="card w-full max-w-md p-6">
        {verifying ? (
          <form onSubmit={submitCode} className="space-y-4">
            <div className="flex flex-col items-center gap-2 text-center">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary-50 text-primary-600 dark:bg-primary-900/40 dark:text-primary-200">
                <MailCheck className="h-5 w-5" />
              </span>
              <h1 className="text-xl font-extrabold tracking-tight">Check your email</h1>
              <p className="text-sm text-[var(--color-ink-muted)]">
                We requested a {CODE_LENGTH}-digit verification code for{' '}
                <span className="font-semibold break-all">{emailShown}</span>.
              </p>
            </div>

            <input
              className="field text-center text-lg tracking-[0.3em]"
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder="000000"
              autoFocus
              value={code}
              onChange={(e) => {
                // Digits only, so a code pasted as "424 242" still fits.
                const entered = e.target.value.replace(/\D/g, '').slice(0, CODE_LENGTH)
                setCode(entered)
                // A complete code is the whole answer; do not make them find the button too.
                if (entered.length === CODE_LENGTH) void verifyCode(entered)
              }}
              aria-label="Verification code"
            />

            {error && <p role="alert" className="text-sm font-medium text-[var(--color-danger)]">{error}</p>}

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
                setStartedOver(true)
                setError(null)
                setCode('')
                setNotice(null)
              }}
            >
              Use a different email
            </button>
          </form>
        ) : (
          // noValidate: one set of messages, each beside its field, instead of the browser's
          // bubbles for empty fields and ours for everything else.
          <form onSubmit={submitDetails} className="space-y-6" noValidate>
            <div className="text-center">
              <h1 className="text-xl font-extrabold tracking-tight">Create your account</h1>
              <p className="mt-1 text-sm text-[var(--color-ink-muted)]">
                A few details now, and your profile is ready the moment you are in.
              </p>
            </div>

            <div className="space-y-4">
              <h2 className="text-sm font-bold">About you</h2>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field id="signup-firstName" label="First name">
                  <input {...control('firstName')} autoComplete="given-name" />
                  {errorFor('firstName')}
                </Field>
                <Field id="signup-lastName" label="Last name">
                  <input {...control('lastName')} autoComplete="family-name" />
                  {errorFor('lastName')}
                </Field>
              </div>

              <Field id="signup-email" label="Email">
                <input {...control('email')} type="email" autoComplete="email" placeholder="you@school.edu" />
                {errorFor('email')}
              </Field>

              <PasswordField
                id="signup-password"
                label="Password"
                autoComplete="new-password"
                minLength={MIN_PASSWORD_LENGTH}
                value={form.password}
                onChange={set('password')}
                invalid={errorField === 'password'}
              >
                {errorFor('password') ?? (
                  <span className="mt-1 block text-xs text-[var(--color-ink-faint)]">
                    At least {MIN_PASSWORD_LENGTH} characters.
                  </span>
                )}
              </PasswordField>
            </div>

            <div className="space-y-4 border-t border-[var(--color-border)] pt-5">
              <h2 className="text-sm font-bold">Your school</h2>
              <Field id="signup-universityName" label="School">
                <select {...control('universityName')}>
                  <option value="">Select your school</option>
                  {SCHOOL_NAMES.map((name) => <option key={name}>{name}</option>)}
                </select>
                {errorFor('universityName')}
              </Field>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field id="signup-grade" label="Year of study">
                  <select {...control('grade')}>
                    {GRADES.map((grade) => <option key={grade}>{grade}</option>)}
                  </select>
                  {errorFor('grade')}
                </Field>
                <Field id="signup-major" label="Major">
                  <input {...control('major')} maxLength={255} placeholder="e.g. Nursing" />
                  {errorFor('major')}
                </Field>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field id="signup-residentCity" label="City">
                  <input {...control('residentCity')} autoComplete="address-level2" maxLength={100} placeholder="e.g. Cincinnati" />
                  {errorFor('residentCity')}
                </Field>
                <Field id="signup-residentState" label="State">
                  <select {...control('residentState')} autoComplete="address-level1">
                    <option value="">Select your state</option>
                    {US_STATES.map(([abbreviation, name]) => <option key={abbreviation} value={abbreviation}>{name}</option>)}
                  </select>
                  {errorFor('residentState')}
                </Field>
              </div>
            </div>

            {error && !errorField && <p role="alert" className="text-sm font-medium text-[var(--color-danger)]">{error}</p>}

            {/* Clerk's bot protection is on for this instance and renders itself here.
                Without this element sign-up fails with a captcha error. */}
            <div id="clerk-captcha" />

            <div className="space-y-3">
              <button className="btn-primary w-full" disabled={!isLoaded || busy}>
                {busy && <Loader2 className="h-4 w-4 animate-spin" />} Create account
              </button>
              <p className="text-center text-xs text-[var(--color-ink-muted)]">
                Next: a code to your email, then two-step verification.
              </p>
            </div>

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
