import { useCallback, useEffect, useState } from 'react'
import { useUser } from '@clerk/clerk-react'
import { UserRound, Save, Sparkles, Check } from 'lucide-react'
import { api, ApiError } from '../lib/api'
import { useSchools } from '../hooks/useSchools'
import { useToast } from '../components/ui/Toast'
import { PageHeader } from '../components/ui/Tabs'
import { ErrorState, Spinner } from '../components/ui/Feedback'
import type { StudentAccountDetails } from '../types'

const GRADES = ['Freshman', 'Sophomore', 'Junior', 'Senior', 'Graduate']

/**
 * Mirrors ProfileFieldBounds on the server (V6). These are UX, never security — the
 * server rejects the same values with a field-level 400 whatever this file says, and a
 * check here only saves the student a round trip. Keep them in step: a bound that is
 * looser here shows a server error instead of a field message, and one that is tighter
 * refuses input the server would have accepted.
 */
const BIO_MAX = 1000
const GRADUATION_YEAR_MIN = 1900
const GRADUATION_YEAR_MAX = 2100

type FieldErrors = Partial<Record<keyof ProfileForm, string>>

/** Field-level checks, so a problem is shown on the field rather than as a toast. */
function validate(form: ProfileForm): FieldErrors {
  const errors: FieldErrors = {}
  if (!form.firstName.trim()) errors.firstName = 'Required'
  if (!form.lastName.trim()) errors.lastName = 'Required'
  if (!form.residentCity.trim()) errors.residentCity = 'Required'
  if (!/^[A-Z]{2}$/.test(form.residentState)) errors.residentState = 'Two capital letters, like OH'
  if (!form.major.trim()) errors.major = 'Required'
  if (!form.universityId) errors.universityId = 'Pick your school'
  if (form.bio.length > BIO_MAX) errors.bio = `${form.bio.length} of ${BIO_MAX} characters`
  if (form.graduationYear) {
    const year = Number(form.graduationYear)
    if (!Number.isInteger(year) || year < GRADUATION_YEAR_MIN || year > GRADUATION_YEAR_MAX) {
      errors.graduationYear = 'Enter a four-digit year'
    }
  }
  return errors
}

interface ProfileForm {
  firstName: string
  lastName: string
  residentCity: string
  residentState: string
  universityId: string
  grade: string
  major: string
  socialMediaLink: string
  graduationYear: string
  bio: string
}

const EMPTY: ProfileForm = {
  firstName: '',
  lastName: '',
  residentCity: '',
  residentState: '',
  universityId: '',
  grade: 'Freshman',
  major: '',
  socialMediaLink: '',
  graduationYear: '',
  bio: '',
}

export default function Profile() {
  const { user } = useUser()
  const { push } = useToast()
  const { schools } = useSchools()

  const email = user?.primaryEmailAddress?.emailAddress ?? ''
  const [form, setForm] = useState<ProfileForm>(EMPTY)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  // No directory row yet: the first save has to create one rather than update.
  const [isNew, setIsNew] = useState(false)
  // The profile endpoint identifies the school by name, but saving needs its
  // id — so the name is held until the school list arrives to match it against.
  const [savedSchoolName, setSavedSchoolName] = useState<string | null>(null)
  // Explicit states rather than implied ones: a failed load offers a retry instead of
  // leaving an empty form that looks like a new profile, and a failed save says so on
  // the page rather than only in a toast that disappears.
  const [loadError, setLoadError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const load = useCallback(() => {
    let cancelled = false
    setLoading(true)
    setLoadError(null)
    api
      .get<StudentAccountDetails>('/student/profile')
      .then((profile) => {
        if (cancelled) return
        setForm({
          firstName: profile.firstName ?? '',
          lastName: profile.lastName ?? '',
          residentCity: profile.residentCity ?? '',
          residentState: profile.residentState ?? '',
          universityId: '',
          grade: profile.grade ?? 'Freshman',
          major: profile.major ?? '',
          socialMediaLink: profile.socialMediaLink ?? '',
          graduationYear: profile.graduationYear ? String(profile.graduationYear) : '',
          bio: profile.bio ?? '',
        })
        setSavedSchoolName(profile.universityName ?? null)
        setIsNew(false)
      })
      .catch((e) => {
        if (cancelled) return
        // 404 is not a failure: the account is verified but has no directory row yet.
        // That is also the S1-04 pending-profile state — the Clerk webhook records the
        // identity, never the directory row, so this is the normal first visit whether
        // or not a delivery has landed.
        if (e instanceof ApiError && e.status === 404) setIsNew(true)
        else setLoadError(e instanceof Error ? e.message : 'Could not load your profile')
      })
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => load(), [load])

  useEffect(() => {
    if (!savedSchoolName || schools.length === 0) return
    const match = schools.find((s) => s.name === savedSchoolName)
    if (match) setForm((prev) => (prev.universityId ? prev : { ...prev, universityId: String(match.id) }))
  }, [schools, savedSchoolName])

  const save = async () => {
    const errors = validate(form)
    setFieldErrors(errors)
    setSaveError(null)
    setSaved(false)
    if (Object.keys(errors).length > 0) {
      // The messages are on the fields; the toast only says where to look.
      push('Check the highlighted fields', 'error')
      return
    }

    setSaving(true)
    const body = {
      ...form,
      universityId: Number(form.universityId),
      email,
      socialMediaLink: form.socialMediaLink || null,
      graduationYear: form.graduationYear ? Number(form.graduationYear) : null,
      bio: form.bio || null,
    }
    try {
      if (isNew) {
        await api.post<string>('/student', body)
        setIsNew(false)
        push('Directory profile created', 'success')
      } else {
        await api.put<string>('/student/profile', body)
        push('Profile updated', 'success')
      }
      setSaved(true)
    } catch (e) {
      // Kept on the page, not only in a toast: a save that failed while the student was
      // reading something else must still be visible, with the way to try again.
      setSaveError(e instanceof Error ? e.message : 'Could not save your profile')
    } finally {
      setSaving(false)
    }
  }

  const set = (key: keyof ProfileForm) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
      setForm({ ...form, [key]: e.target.value })
      // Clear this field's error as soon as it is edited, and drop the saved badge:
      // the page no longer reflects what is stored.
      setFieldErrors((prev) => ({ ...prev, [key]: undefined }))
      setSaved(false)
    }

  if (loading) return <Spinner label="Loading your profile…" />
  if (loadError) return <ErrorState message={loadError} onRetry={load} />

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader
        title={isNew ? 'Create your profile' : 'My profile'}
        subtitle={
          isNew
            ? 'One more step — this is what other students see in the directory.'
            : 'This is how you appear in the directory and next to your listings.'
        }
      />

      {isNew && (
        <div className="card mb-5 flex items-start gap-3 border-primary-200 bg-primary-50 p-4 dark:bg-primary-900/30">
          <Sparkles className="mt-0.5 h-5 w-5 shrink-0 text-primary-600 dark:text-primary-200" />
          <p className="text-sm text-primary-900 dark:text-primary-100">
            Your account is verified, but you do not have a directory profile yet. Fill this in so classmates can
            find you and trust your listings.
          </p>
        </div>
      )}

      <div className="card p-6">
        <div className="mb-5 flex items-center gap-3">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-primary-100 text-lg font-bold text-primary-700 dark:bg-primary-900/50 dark:text-primary-200">
            {form.firstName ? (
              `${form.firstName.charAt(0)}${form.lastName.charAt(0)}`
            ) : (
              <UserRound className="h-6 w-6" />
            )}
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">{email}</p>
            <p className="text-xs text-[var(--color-ink-faint)]">
              Managed by Clerk — change your email or password from the avatar menu.
            </p>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="First name" error={fieldErrors.firstName}>
            <input className="field" value={form.firstName} onChange={set('firstName')} />
          </Field>
          <Field label="Last name" error={fieldErrors.lastName}>
            <input className="field" value={form.lastName} onChange={set('lastName')} />
          </Field>
          <Field
            label="School"
            error={fieldErrors.universityId}
            hint={isNew ? undefined : 'Set when your profile was created'}
          >
            <select
              className="field"
              value={form.universityId}
              onChange={set('universityId')}
              // Editable only while creating. The server ignores a school sent on an
              // update — it decides which directory, theme and school surfaces a student
              // belongs to, so it is not a field the owner may reassign (S1-06). Leaving
              // the control enabled would offer a change that silently does not happen.
              disabled={!isNew}
            >
              <option value="">Select your school</option>
              {schools.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Year">
            <select className="field" value={form.grade} onChange={set('grade')}>
              {GRADES.map((g) => (
                <option key={g} value={g}>
                  {g}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Major" error={fieldErrors.major}>
            <input className="field" value={form.major} onChange={set('major')} />
          </Field>
          <Field label="City" error={fieldErrors.residentCity}>
            <input className="field" value={form.residentCity} onChange={set('residentCity')} />
          </Field>
          <Field label="State" hint="Two letters, e.g. OH" error={fieldErrors.residentState}>
            <input
              className="field"
              maxLength={2}
              value={form.residentState}
              onChange={(e) => setForm({ ...form, residentState: e.target.value.toUpperCase() })}
            />
          </Field>
          <Field label="Social link" hint="Optional" error={fieldErrors.socialMediaLink}>
            <input
              className="field"
              placeholder="https://linkedin.com/in/…"
              value={form.socialMediaLink}
              onChange={set('socialMediaLink')}
            />
          </Field>
          <Field label="Graduation year" hint="Optional" error={fieldErrors.graduationYear}>
            <input
              className="field"
              inputMode="numeric"
              placeholder="2027"
              value={form.graduationYear}
              onChange={set('graduationYear')}
            />
          </Field>
          <div className="sm:col-span-2">
            <Field
              label="About you"
              hint="Optional"
              error={fieldErrors.bio}
              // Counted rather than silently truncated, and announced politely so a
              // screen reader hears the remaining room without interrupting typing.
              footer={
                <span aria-live="polite" className="text-xs text-[var(--color-ink-faint)]">
                  {form.bio.length} / {BIO_MAX}
                </span>
              }
            >
              <textarea
                className="field min-h-24"
                rows={4}
                placeholder="What you study, what you are looking for, anything that helps classmates recognise you."
                value={form.bio}
                onChange={set('bio')}
              />
            </Field>
          </div>
        </div>

        {saveError && (
          <div
            role="alert"
            className="mt-6 flex flex-wrap items-center gap-3 rounded-xl border border-[var(--color-danger)] p-3"
          >
            <p className="text-sm font-medium text-[var(--color-danger)]">{saveError}</p>
            <button type="button" className="btn-ghost btn-sm" onClick={save} disabled={saving}>
              Try again
            </button>
          </div>
        )}

        <div className="mt-6 flex flex-wrap items-center justify-end gap-3">
          {/* Stated, not implied: the toast has usually gone by the time anyone looks. */}
          {saved && !saving && (
            <p role="status" className="flex items-center gap-1.5 text-sm font-medium text-[var(--color-ink-muted)]">
              <Check className="h-4 w-4" aria-hidden="true" />
              Saved
            </p>
          )}
          <button className="btn-primary" onClick={save} disabled={saving}>
            <Save className="h-4 w-4" />
            {saving ? 'Saving…' : isNew ? 'Create profile' : 'Save changes'}
          </button>
        </div>
      </div>
    </div>
  )
}

function Field({
  label,
  hint,
  error,
  footer,
  children,
}: {
  label: string
  hint?: string
  error?: string
  footer?: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-bold tracking-wide text-[var(--color-ink-muted)] uppercase">
        {label}
        {hint && <span className="ml-1 font-medium normal-case opacity-70">({hint})</span>}
      </span>
      {children}
      {/* The message sits inside the label, so it is announced with the field rather
          than as loose text somewhere after it. */}
      {(error || footer) && (
        <span className="mt-1 flex items-center justify-between gap-2">
          <span className="text-xs font-medium text-[var(--color-danger)]">{error}</span>
          {footer}
        </span>
      )}
    </label>
  )
}
