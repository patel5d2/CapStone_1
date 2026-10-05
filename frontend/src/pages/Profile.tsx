import { useEffect, useRef, useState } from 'react'
import { useUser } from '@clerk/clerk-react'
import { useNavigate } from 'react-router-dom'
import { UserRound, Save, Check } from 'lucide-react'
import { api, ApiError } from '../lib/api'
import { markProfileComplete } from '../lib/profileGate'
import { useToast } from '../components/ui/Toast'
import { PageHeader } from '../components/ui/Tabs'
import { EmptyState, ErrorState, Spinner } from '../components/ui/Feedback'
import type { ProfileImageResponse, ProfileSaveRequest, ProfileVisibility, School, StudentAccountDetails } from '../types'

const GRADES = ['Freshman', 'Sophomore', 'Junior', 'Senior', 'Graduate']
const PRIVATE: ProfileVisibility = { showMajor: false, showGraduationYear: false, showBio: false, showPhoto: false }
const VISIBILITY: { key: keyof ProfileVisibility; label: string }[] = [
  { key: 'showMajor', label: 'Major' },
  { key: 'showGraduationYear', label: 'Graduation year' },
  { key: 'showBio', label: 'About you' },
  { key: 'showPhoto', label: 'Profile photo' },
]
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
type FieldErrors = Partial<Record<keyof ProfileForm, string>>
const EMPTY: ProfileForm = {
  firstName: '', lastName: '', residentCity: '', residentState: '', universityId: '',
  grade: 'Freshman', major: '', socialMediaLink: '', graduationYear: '', bio: '',
}

/** UX checks mirror the request DTOs. The server remains authoritative. */
function validate(form: ProfileForm): FieldErrors {
  const errors: FieldErrors = {}
  for (const key of ['firstName', 'lastName', 'residentCity', 'major', 'grade'] as const) {
    if (!form[key].trim()) errors[key] = 'This field is required.'
  }
  for (const key of ['firstName', 'lastName', 'residentCity'] as const) {
    if (form[key].length > 100) errors[key] = 'Use 100 characters or fewer.'
  }
  if (form.major.length > 255) errors.major = 'Use 255 characters or fewer.'
  if (form.socialMediaLink.length > 255) errors.socialMediaLink = 'Use 255 characters or fewer.'
  if (!/^[A-Z]{2}$/.test(form.residentState)) errors.residentState = 'Enter two capital letters, such as OH.'
  if (!form.universityId) errors.universityId = 'Select your school.'
  if (form.bio.length > 1000) errors.bio = 'Use 1000 characters or fewer.'
  if (form.graduationYear && (!/^\d{4}$/.test(form.graduationYear)
      || Number(form.graduationYear) < 1900 || Number(form.graduationYear) > 2100)) {
    errors.graduationYear = 'Enter a year from 1900 to 2100.'
  }
  return errors
}

export default function Profile() {
  const { user } = useUser()
  const { push } = useToast()
  const navigate = useNavigate()
  const email = user?.primaryEmailAddress?.emailAddress ?? ''
  const [form, setForm] = useState<ProfileForm>(EMPTY)
  const [visibility, setVisibility] = useState<ProfileVisibility>(PRIVATE)
  const [photoUrl, setPhotoUrl] = useState<string | null>(null)
  const [schools, setSchools] = useState<School[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [isNew, setIsNew] = useState(false)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const errorSummary = useRef<HTMLParagraphElement>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const busy = saving || uploading

  useEffect(() => {
    let cancelled = false
    const profile = api.get<StudentAccountDetails>('/student/profile').catch((error: unknown) => {
      // S1-04 stores identity separately. A missing student row is normal before
      // the first save, regardless of when Clerk's webhook arrives.
      if (error instanceof ApiError && error.status === 404) return null
      throw error
    })
    Promise.all([profile, api.get<School[]>('/api/schools')])
      .then(([value, availableSchools]) => {
        if (cancelled) return
        setSchools(availableSchools)
        setIsNew(value === null)
        setForm(value ? {
          firstName: value.firstName ?? '', lastName: value.lastName ?? '',
          residentCity: value.residentCity ?? '', residentState: value.residentState ?? '',
          universityId: String(value.universityId ?? ''), grade: value.grade ?? 'Freshman',
          major: value.major ?? '', socialMediaLink: value.socialMediaLink ?? '',
          graduationYear: value.graduationYear == null ? '' : String(value.graduationYear), bio: value.bio ?? '',
        } : EMPTY)
        setVisibility(value?.visibility ?? PRIVATE)
        setPhotoUrl(value?.photoUrl ?? null)
        setSaved(false)
        setSaveError(null)
        setFieldErrors({})
        setFile(null)
        setUploadError(null)
      })
      .catch((error: unknown) => {
        if (!cancelled) setLoadError(error instanceof Error ? error.message : 'Could not load your profile.')
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [reload, user?.id])

  const edit = (key: keyof ProfileForm, value: string) => {
    setForm((previous) => ({ ...previous, [key]: value }))
    setFieldErrors((previous) => ({ ...previous, [key]: undefined }))
    setSaved(false)
  }
  const field = (key: keyof ProfileForm) => ({
    id: `profile-${key}`, name: key, className: 'field', value: form[key],
    'aria-invalid': Boolean(fieldErrors[key]),
    'aria-describedby': fieldErrors[key] ? `profile-${key}-error` : undefined,
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
      edit(key, key === 'residentState' ? event.target.value.toUpperCase() : event.target.value),
  })

  const upload = async (selected: File) => {
    setFile(selected)
    setUploadError(null)
    setSaved(false)
    if (selected.size > 10_000_000) {
      setUploadError('Photo must be 10 MB or smaller. Choose a smaller file.')
      return
    }
    setUploading(true)
    try {
      const response = await api.upload<ProfileImageResponse>('/api/profile-images', selected)
      setPhotoUrl(response.photoUrl)
      setFile(null)
      push('Photo uploaded. Save your profile to keep it.', 'info')
    } catch (error) {
      setUploadError(error instanceof Error ? error.message : 'Could not upload your photo. Try again.')
    } finally {
      setUploading(false)
    }
  }

  const save = async () => {
    if (busy || file) return
    const errors = validate(form)
    setFieldErrors(errors)
    setSaveError(null)
    setSaved(false)
    if (Object.keys(errors).length) {
      setSaveError('Your profile was not saved. Check the marked fields below.')
      requestAnimationFrame(() => errorSummary.current?.focus())
      return
    }
    const body: ProfileSaveRequest = {
      ...form, email, universityId: Number(form.universityId),
      graduationYear: form.graduationYear ? Number(form.graduationYear) : null,
      socialMediaLink: form.socialMediaLink || null, bio: form.bio || null, photoUrl, visibility,
    }
    setSaving(true)
    try {
      if (isNew) await api.post<string>('/student', body)
      else await api.put<string>('/student/profile', body)
      setIsNew(false)
      setSaved(true)
      markProfileComplete(user?.id)
      push('Profile and visibility saved', 'success')
      // First save finishes sign-up: let them into the app.
      if (isNew) navigate('/marketplace')
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Could not save your profile.'
      const mapped: FieldErrors = {}
      if (error instanceof ApiError && error.fieldErrors) {
        for (const [key, value] of Object.entries(error.fieldErrors)) {
          if (key in EMPTY) mapped[key as keyof ProfileForm] = value
        }
      }
      setFieldErrors(mapped)
      // Always retain the banner, including errors for fields this page cannot map.
      setSaveError(`Your profile was not saved. ${message}`)
      requestAnimationFrame(() => errorSummary.current?.focus())
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div role="status"><Spinner label="Loading your profile…" /></div>
  if (loadError) return <ErrorState message={loadError} onRetry={() => { setLoading(true); setLoadError(null); setReload((value) => value + 1) }} />
  if (!schools.length) return <EmptyState icon={<UserRound className="h-6 w-6" />} title="Schools are unavailable"
    description="We could not find a school to complete your profile. Please try again."
    action={<button className="btn-primary" onClick={() => setReload((value) => value + 1)}>Try again</button>} />

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader title={isNew ? 'Complete your profile' : 'My profile'}
        subtitle="Add your details and choose what classmates can see." />
      {isNew && <p className="card mb-5 p-4 text-sm" role="status">
        Your directory profile is not ready yet. Complete the required fields below, then save.
      </p>}
      <form className="card p-4 sm:p-6" noValidate onSubmit={(event) => { event.preventDefault(); void save() }}>
        <p className="mb-5 break-words text-sm text-[var(--color-ink-muted)]">
          Signed in as {email}. Manage your account from the avatar menu.
        </p>
        {saveError && <div className="mb-5 rounded-xl border border-[var(--color-danger)] p-4">
          <p ref={errorSummary} tabIndex={-1} role="alert" className="text-sm text-[var(--color-danger)]">{saveError}</p>
          <button type="submit" className="btn-ghost mt-3" disabled={busy || Boolean(file)}>Try saving again</button>
        </div>}
        <fieldset disabled={busy} className="min-w-0">
          <legend className="mb-4 text-sm font-bold">Profile details</legend>
          <p className="mb-4 text-sm text-[var(--color-ink-muted)]">Fields marked “required” must be completed.</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field name="firstName" label="First name" required errors={fieldErrors}><input {...field('firstName')} autoComplete="given-name" required /></Field>
            <Field name="lastName" label="Last name" required errors={fieldErrors}><input {...field('lastName')} autoComplete="family-name" required /></Field>
            <Field name="universityId" label="School" required errors={fieldErrors}>
              <select {...field('universityId')} disabled={!isNew || busy} required>
                <option value="">Select your school</option>
                {schools.map((school) => <option key={school.id} value={school.id}>{school.name}</option>)}
              </select>
            </Field>
            <Field name="grade" label="Year of study" required errors={fieldErrors}>
              <select {...field('grade')} required>{GRADES.map((grade) => <option key={grade}>{grade}</option>)}</select>
            </Field>
            <Field name="major" label="Major" required errors={fieldErrors}><input {...field('major')} required /></Field>
            <Field name="residentCity" label="City" required errors={fieldErrors}><input {...field('residentCity')} autoComplete="address-level2" required /></Field>
            <Field name="residentState" label="State (two letters)" required errors={fieldErrors}>
              <input {...field('residentState')} autoComplete="address-level1" maxLength={2} required />
            </Field>
            <Field name="graduationYear" label="Graduation year" errors={fieldErrors}><input {...field('graduationYear')} inputMode="numeric" /></Field>
            <div className="sm:col-span-2">
              <Field name="bio" label="About you" errors={fieldErrors}><textarea {...field('bio')} rows={4} /></Field>
              <p className="mt-1 text-sm text-[var(--color-ink-muted)]">{form.bio.length} / 1000 characters</p>
            </div>
          </div>
          {!isNew && <p className="mt-3 text-sm text-[var(--color-ink-muted)]">Your school was set when your profile was created.</p>}
        </fieldset>

        <fieldset disabled={busy} className="mt-6 min-w-0 border-t border-[var(--color-border)] pt-5">
          <legend className="text-sm font-bold">Profile photo (optional)</legend>
          <div className="mb-4 flex items-center gap-3">
            {photoUrl ? <img src={photoUrl} alt="Your profile photo" className="h-16 w-16 rounded-full object-cover" />
              : <UserRound className="h-12 w-12 text-[var(--color-ink-muted)]" aria-hidden="true" />}
            <p className="text-sm text-[var(--color-ink-muted)]">JPEG, PNG or WebP. Up to 10 MB and 4000 × 4000 pixels.</p>
          </div>
          <label htmlFor="profile-photo" className="mb-2 block text-sm font-semibold">Choose a profile photo</label>
          <input ref={fileInput} id="profile-photo" type="file" accept="image/jpeg,image/png,image/webp"
            className="field min-w-0 max-w-full" aria-describedby={uploadError ? 'photo-error' : undefined}
            onChange={(event) => { const selected = event.target.files?.[0]; if (selected) void upload(selected) }} />
          {uploadError && <div className="mt-3">
            <p id="photo-error" role="alert" className="text-sm text-[var(--color-danger)]">{uploadError}</p>
            {file && <button type="button" className="btn-ghost mt-2" onClick={() => void upload(file)}>Retry photo upload</button>}
          </div>}
          {(photoUrl || file) && <button type="button" className="btn-ghost mt-3" onClick={() => {
            setPhotoUrl(null); setFile(null); setUploadError(null); setSaved(false)
            if (fileInput.current) fileInput.current.value = ''
          }}>Remove photo</button>}
          {photoUrl && !saved && <p className="mt-3 text-sm text-[var(--color-ink-muted)]">Save your profile to keep this photo.</p>}
        </fieldset>
        <div role="status" aria-live="polite">{uploading && <Spinner label="Uploading your photo…" />}</div>

        <fieldset disabled={busy} className="mt-6 min-w-0 border-t border-[var(--color-border)] pt-5">
          <legend className="text-sm font-bold">What other students can see</legend>
          <p className="mb-4 text-sm text-[var(--color-ink-muted)]">
            Name, school, year of study and city/state appear in the directory. Email and social links are not shown.
            The fields below stay private unless you choose to share them. Changes take effect when you save.
          </p>
          <div className="grid gap-4 sm:grid-cols-2">
            {VISIBILITY.map(({ key, label }) => <div key={key}>
              <label htmlFor={`visibility-${key}`} className="mb-1.5 block text-sm font-semibold">{label} visibility</label>
              <select id={`visibility-${key}`} className="field" value={visibility[key] ? 'students' : 'private'}
                onChange={(event) => { setVisibility({ ...visibility, [key]: event.target.value === 'students' }); setSaved(false) }}>
                <option value="private">Only me</option>
                <option value="students">Other students</option>
              </select>
            </div>)}
          </div>
        </fieldset>
        <div className="mt-6 flex flex-wrap items-center justify-end gap-3">
          <p role="status" aria-live="polite" className="text-sm text-[var(--color-ink-muted)]">
            {saving ? 'Saving profile and visibility…' : saved ? <span className="flex items-center gap-2"><Check className="h-4 w-4" aria-hidden="true" />Profile and visibility saved</span> : 'Changes are saved when you select Save profile.'}
          </p>
          <button className="btn-primary" type="submit" disabled={busy || Boolean(file)}>
            <Save className="h-4 w-4" aria-hidden="true" />{saving ? 'Saving…' : 'Save profile'}
          </button>
        </div>
      </form>
    </div>
  )
}

function Field({ name, label, required, errors, children }: {
  name: keyof ProfileForm; label: string; required?: boolean; errors: FieldErrors; children: React.ReactNode
}) {
  return <div>
    <label htmlFor={`profile-${name}`} className="mb-1.5 block text-sm font-semibold">
      {label} <span className="font-normal text-[var(--color-ink-muted)]">({required ? 'required' : 'optional'})</span>
    </label>
    {children}
    {errors[name] && <p id={`profile-${name}-error`} className="mt-1 text-sm text-[var(--color-danger)]">{errors[name]}</p>}
  </div>
}
