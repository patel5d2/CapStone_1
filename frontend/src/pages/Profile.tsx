import { useEffect, useState } from 'react'
import { useUser } from '@clerk/clerk-react'
import { UserRound, Save, Sparkles } from 'lucide-react'
import { api, ApiError } from '../lib/api'
import { useSchools } from '../hooks/useSchools'
import { useToast } from '../components/ui/Toast'
import { PageHeader } from '../components/ui/Tabs'
import { Spinner } from '../components/ui/Feedback'
import type { StudentAccountDetails } from '../types'

const GRADES = ['Freshman', 'Sophomore', 'Junior', 'Senior', 'Graduate']

interface ProfileForm {
  firstName: string
  lastName: string
  residentCity: string
  residentState: string
  universityId: string
  grade: string
  major: string
  socialMediaLink: string
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

  useEffect(() => {
    let cancelled = false
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
        })
        setSavedSchoolName(profile.universityName ?? null)
        setIsNew(false)
      })
      .catch((e) => {
        if (cancelled) return
        if (e instanceof ApiError && e.status === 404) setIsNew(true)
        else push('Could not load your profile', 'error')
      })
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [push])

  useEffect(() => {
    if (!savedSchoolName || schools.length === 0) return
    const match = schools.find((s) => s.name === savedSchoolName)
    if (match) setForm((prev) => (prev.universityId ? prev : { ...prev, universityId: String(match.id) }))
  }, [schools, savedSchoolName])

  const save = async () => {
    if (!form.firstName || !form.lastName || !form.residentCity || !form.residentState || !form.major) {
      push('Fill in every required field', 'error')
      return
    }
    if (!form.universityId) {
      push('Pick your school', 'error')
      return
    }
    if (!/^[A-Z]{2}$/.test(form.residentState)) {
      push('State must be two capital letters, like OH', 'error')
      return
    }

    setSaving(true)
    const body = {
      ...form,
      universityId: Number(form.universityId),
      email,
      socialMediaLink: form.socialMediaLink || null,
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
    } catch (e) {
      push(e instanceof Error ? e.message : 'Could not save your profile', 'error')
    } finally {
      setSaving(false)
    }
  }

  const set = (key: keyof ProfileForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm({ ...form, [key]: e.target.value })

  if (loading) return <Spinner label="Loading your profile…" />

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader
        title={isNew ? 'Create your profile' : 'My Profile'}
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
          <Field label="First name">
            <input className="field" value={form.firstName} onChange={set('firstName')} />
          </Field>
          <Field label="Last name">
            <input className="field" value={form.lastName} onChange={set('lastName')} />
          </Field>
          <Field label="School">
            <select className="field" value={form.universityId} onChange={set('universityId')}>
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
          <Field label="Major">
            <input className="field" value={form.major} onChange={set('major')} />
          </Field>
          <Field label="City">
            <input className="field" value={form.residentCity} onChange={set('residentCity')} />
          </Field>
          <Field label="State" hint="Two letters, e.g. OH">
            <input
              className="field"
              maxLength={2}
              value={form.residentState}
              onChange={(e) => setForm({ ...form, residentState: e.target.value.toUpperCase() })}
            />
          </Field>
          <Field label="Social link" hint="Optional">
            <input
              className="field"
              placeholder="https://linkedin.com/in/…"
              value={form.socialMediaLink}
              onChange={set('socialMediaLink')}
            />
          </Field>
        </div>

        <div className="mt-6 flex justify-end">
          <button className="btn-primary" onClick={save} disabled={saving}>
            <Save className="h-4 w-4" />
            {saving ? 'Saving…' : isNew ? 'Create profile' : 'Save changes'}
          </button>
        </div>
      </div>
    </div>
  )
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-bold tracking-wide text-[var(--color-ink-muted)] uppercase">
        {label}
        {hint && <span className="ml-1 font-medium normal-case opacity-70">({hint})</span>}
      </span>
      {children}
    </label>
  )
}
