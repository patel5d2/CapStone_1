import { useCallback, useRef, useState } from 'react'
import { api, ApiError } from '../../lib/api'
import { GRADES, US_STATES } from '../../lib/signupProfile'
import { Modal } from '../../components/ui/Modal'
import type { AdminStudent, AdminStudentUpdateRequest, School } from '../../types'
import { messageOf, nameOf } from './adminList'

interface EditForm {
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
type FieldErrors = Partial<Record<keyof EditForm, string>>

function toForm(account: AdminStudent): EditForm {
  return {
    firstName: account.firstName ?? '',
    lastName: account.lastName ?? '',
    residentCity: account.residentCity ?? '',
    residentState: account.residentState ?? '',
    universityId: account.universityId == null ? '' : String(account.universityId),
    grade: account.grade ?? '',
    major: account.major ?? '',
    socialMediaLink: account.socialMediaLink ?? '',
    graduationYear: account.graduationYear == null ? '' : String(account.graduationYear),
    bio: account.bio ?? '',
  }
}

/** UX checks that mirror `AdminStudentUpdateRequest`. The server remains authoritative. */
function validate(form: EditForm, schools: School[]): FieldErrors {
  const errors: FieldErrors = {}
  for (const key of ['firstName', 'lastName', 'residentCity', 'major', 'grade'] as const) {
    if (!form[key].trim()) errors[key] = 'This field is required.'
  }
  for (const key of ['firstName', 'lastName', 'residentCity'] as const) {
    if (form[key].length > 100) errors[key] = 'Use 100 characters or fewer.'
  }
  if (form.major.length > 255) errors.major = 'Use 255 characters or fewer.'
  if (!/^[A-Z]{2}$/.test(form.residentState)) errors.residentState = 'Choose a state.'
  if (!schools.some((school) => String(school.id) === form.universityId)) errors.universityId = 'Choose a school.'
  if (form.socialMediaLink.length > 255) errors.socialMediaLink = 'Use 255 characters or fewer.'
  if (form.graduationYear.trim()) {
    const year = Number(form.graduationYear)
    if (!Number.isInteger(year) || year < 1900 || year > 2100) errors.graduationYear = 'Enter a four-digit year.'
  }
  if (form.bio.length > 1000) errors.bio = 'Use 1000 characters or fewer.'
  return errors
}

function toRequest(form: EditForm): AdminStudentUpdateRequest {
  return {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    residentCity: form.residentCity.trim(),
    residentState: form.residentState,
    universityId: Number(form.universityId),
    grade: form.grade,
    major: form.major.trim(),
    socialMediaLink: form.socialMediaLink.trim() || null,
    graduationYear: form.graduationYear.trim() ? Number(form.graduationYear) : null,
    bio: form.bio.trim() || null,
  }
}

/** Corrects one student's account details. The server decides what may change (`AdminStudentUpdateRequest`). */
export function EditAccountDialog({ account, schools, onClose, onSaved, onMissing }: {
  account: AdminStudent
  schools: School[]
  onClose: () => void
  onSaved: (saved: AdminStudent) => void
  onMissing: () => void
}) {
  const [form, setForm] = useState<EditForm>(() => toForm(account))
  const [errors, setErrors] = useState<FieldErrors>({})
  const [failure, setFailure] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  // A save in flight must finish in front of the administrator, so its outcome is seen.
  const busy = useRef(false)
  const close = useCallback(() => { if (!busy.current) onClose() }, [onClose])

  const save = async (event: React.FormEvent) => {
    event.preventDefault()
    const problems = validate(form, schools)
    setErrors(problems)
    setFailure(null)
    if (Object.keys(problems).length > 0) return
    busy.current = true
    setSaving(true)
    try {
      onSaved(await api.put<AdminStudent>(`/api/admin/students/${account.id}`, toRequest(form)))
    } catch (e) {
      busy.current = false
      setSaving(false)
      if (e instanceof ApiError && e.status === 404) {
        onMissing()
        return
      }
      if (e instanceof ApiError && e.fieldErrors) setErrors(e.fieldErrors as FieldErrors)
      setFailure(`Nothing was saved. ${messageOf(e, 'The changes could not be saved.')}`)
    }
  }

  const field = (key: keyof EditForm) => ({
    id: `admin-edit-${key}`,
    name: key,
    className: 'field',
    value: form[key],
    'aria-invalid': Boolean(errors[key]),
    'aria-describedby': errors[key] ? `admin-edit-${key}-error` : undefined,
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
      setForm((previous) => ({ ...previous, [key]: event.target.value }))
      setErrors((previous) => ({ ...previous, [key]: undefined }))
    },
  })

  return (
    <Modal
      open
      onClose={close}
      title={`Edit ${nameOf(account)}`}
      description={account.email}
      footer={
        <>
          <button type="button" className="btn-ghost" onClick={close} disabled={saving}>Cancel</button>
          <button type="submit" form="admin-edit-form" className="btn-primary" disabled={saving}>
            {saving ? 'Saving…' : 'Save changes'}
          </button>
        </>
      }
    >
      <form id="admin-edit-form" onSubmit={save} noValidate className="space-y-4">
        <p className="text-sm text-[var(--color-ink-muted)]">
          You are editing this student&rsquo;s account as an administrator. Their email and photo are
          theirs and cannot be changed here.
        </p>
        {failure && <p role="alert" className="rounded-xl bg-[var(--color-danger-bg)] px-3 py-2 text-sm text-[var(--color-danger)]">{failure}</p>}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field name="firstName" label="First name" required errors={errors}><input {...field('firstName')} /></Field>
          <Field name="lastName" label="Last name" required errors={errors}><input {...field('lastName')} /></Field>
        </div>
        <Field name="universityId" label="School" required errors={errors}>
          <select {...field('universityId')}>
            <option value="">Choose a school</option>
            {schools.map((school) => <option key={school.id} value={school.id}>{school.name}</option>)}
          </select>
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field name="grade" label="Year of study" required errors={errors}>
            <select {...field('grade')}>
              {!GRADES.includes(form.grade) && <option value={form.grade}>{form.grade || 'Choose a year'}</option>}
              {GRADES.map((grade) => <option key={grade}>{grade}</option>)}
            </select>
          </Field>
          <Field name="major" label="Major" required errors={errors}><input {...field('major')} /></Field>
          <Field name="residentCity" label="City" required errors={errors}><input {...field('residentCity')} /></Field>
          <Field name="residentState" label="State" required errors={errors}>
            <select {...field('residentState')}>
              {!US_STATES.some(([code]) => code === form.residentState)
                && <option value={form.residentState}>{form.residentState || 'Choose a state'}</option>}
              {US_STATES.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
            </select>
          </Field>
          <Field name="graduationYear" label="Graduation year" errors={errors}><input {...field('graduationYear')} inputMode="numeric" /></Field>
          <Field name="socialMediaLink" label="Social link" errors={errors}><input {...field('socialMediaLink')} /></Field>
        </div>
        <Field name="bio" label="About" errors={errors}><textarea {...field('bio')} rows={3} /></Field>
      </form>
    </Modal>
  )
}

function Field({ name, label, required, errors, children }: {
  name: keyof EditForm; label: string; required?: boolean; errors: FieldErrors; children: React.ReactNode
}) {
  return (
    <div>
      <label htmlFor={`admin-edit-${name}`} className="mb-1.5 block text-sm font-semibold">
        {label} <span className="font-normal text-[var(--color-ink-muted)]">({required ? 'required' : 'optional'})</span>
      </label>
      {children}
      {errors[name] && <p id={`admin-edit-${name}-error`} className="mt-1 text-sm text-[var(--color-danger)]">{errors[name]}</p>}
    </div>
  )
}
