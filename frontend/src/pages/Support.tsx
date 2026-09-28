import { useCallback, useEffect, useState } from 'react'
import { LifeBuoy, HeartHandshake, ShieldCheck, Phone, MapPin, Plus, Utensils, Brain, HandCoins } from 'lucide-react'
import { api, toQueryString } from '../lib/api'
import { useSchools } from '../hooks/useSchools'
import { useToast } from '../components/ui/Toast'
import { Modal } from '../components/ui/Modal'
import { PageHeader, Tabs } from '../components/ui/Tabs'
import { EmptyState, Spinner } from '../components/ui/Feedback'
import type { AnonymousRequest, SupportCategory, SupportResource } from '../types'

type Section = 'resources' | 'requests' | 'mine'

const CATEGORY_LABEL: Record<SupportCategory, string> = {
  FOOD_PANTRY: 'Food pantry',
  EMERGENCY_AID: 'Emergency aid',
  COUNSELING: 'Counseling',
  OTHER: 'Other',
}

const CATEGORY_ICON: Record<SupportCategory, typeof Utensils> = {
  FOOD_PANTRY: Utensils,
  EMERGENCY_AID: HandCoins,
  COUNSELING: Brain,
  OTHER: LifeBuoy,
}

export default function Support() {
  const [section, setSection] = useState<Section>('resources')

  return (
    <>
      <PageHeader
        title="Support"
        subtitle="Every school has help available. Find it here, or ask for what you need without your name attached."
      />

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <Tabs
          value={section}
          onChange={setSection}
          options={[
            { value: 'resources', label: 'Resources' },
            { value: 'requests', label: 'Open requests' },
            { value: 'mine', label: 'My requests' },
          ]}
        />
        <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-[var(--color-ink-muted)]">
          <ShieldCheck className="h-3.5 w-3.5 text-[var(--color-success)]" />
          Requests are posted anonymously
        </span>
      </div>

      {section === 'resources' && <Resources />}
      {section === 'requests' && <OpenRequests />}
      {section === 'mine' && <MyRequests />}
    </>
  )
}

function Resources() {
  const { schools, nameOf } = useSchools()
  const [schoolId, setSchoolId] = useState('')
  const [resources, setResources] = useState<SupportResource[]>([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setResources(await api.get<SupportResource[]>(`/api/support/resources${toQueryString({ schoolId })}`))
    } catch {
      setResources([])
    } finally {
      setLoading(false)
    }
  }, [schoolId])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <>
      <div className="mb-5 max-w-sm">
        <select className="field" value={schoolId} onChange={(e) => setSchoolId(e.target.value)}>
          <option value="">All schools</option>
          {schools.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </div>

      {loading ? (
        <Spinner />
      ) : resources.length === 0 ? (
        <EmptyState icon={<LifeBuoy className="h-6 w-6" />} title="No resources listed for that school yet" />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {resources.map((resource) => {
            const Icon = CATEGORY_ICON[resource.category]
            return (
              <article key={resource.id} className="card card-hover animate-fade-in-up p-5">
                <div className="flex items-start gap-3">
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary-50 text-primary-600 dark:bg-primary-900/40 dark:text-primary-200">
                    <Icon className="h-5 w-5" />
                  </span>
                  <div className="min-w-0">
                    <h3 className="text-sm leading-snug font-bold">{resource.name}</h3>
                    <p className="mt-0.5 text-xs text-[var(--color-ink-faint)]">{nameOf(resource.schoolId)}</p>
                  </div>
                </div>
                <span className="badge-neutral mt-3">{CATEGORY_LABEL[resource.category]}</span>
                {resource.description && (
                  <p className="mt-3 text-sm text-[var(--color-ink-muted)]">{resource.description}</p>
                )}
                {resource.contactInfo && (
                  <p className="mt-3 flex items-center gap-1.5 text-xs text-[var(--color-ink-muted)]">
                    <Phone className="h-3.5 w-3.5" /> {resource.contactInfo}
                  </p>
                )}
                {resource.address && (
                  <p className="mt-1 flex items-center gap-1.5 text-xs text-[var(--color-ink-muted)]">
                    <MapPin className="h-3.5 w-3.5" /> {resource.address}
                  </p>
                )}
              </article>
            )
          })}
        </div>
      )}
    </>
  )
}

function OpenRequests() {
  const { push } = useToast()
  const { schools, nameOf } = useSchools()
  const [requests, setRequests] = useState<AnonymousRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [form, setForm] = useState({ category: 'FOOD_PANTRY' as SupportCategory, description: '', schoolId: '' })

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setRequests(await api.get<AnonymousRequest[]>('/api/support/requests?status=OPEN'))
    } catch {
      push('Could not load requests', 'error')
    } finally {
      setLoading(false)
    }
  }, [push])

  useEffect(() => {
    void load()
  }, [load])

  const submit = async () => {
    if (!form.description.trim()) {
      push('Describe what you need', 'error')
      return
    }
    try {
      await api.post<AnonymousRequest>('/api/support/requests', {
        ...form,
        schoolId: form.schoolId ? Number(form.schoolId) : null,
      })
      push('Posted anonymously', 'success')
      setCreateOpen(false)
      setForm({ category: 'FOOD_PANTRY', description: '', schoolId: '' })
      void load()
    } catch {
      push('Could not submit your request', 'error')
    }
  }

  const fulfill = async (request: AnonymousRequest) => {
    if (!confirm('Mark this request as fulfilled by you?')) return
    try {
      await api.post(`/api/support/requests/${request.id}/fulfill`)
      push('Thank you for helping out', 'success')
      void load()
    } catch {
      push('Could not update that request', 'error')
    }
  }

  return (
    <>
      <div className="mb-4 flex justify-end">
        <button className="btn-primary" onClick={() => setCreateOpen(true)}>
          <Plus className="h-4 w-4" /> Ask for help
        </button>
      </div>

      {loading ? (
        <Spinner />
      ) : requests.length === 0 ? (
        <EmptyState
          icon={<HeartHandshake className="h-6 w-6" />}
          title="No open requests"
          description="When someone asks for help anonymously, it shows up here for students who can give."
          action={
            <button className="btn-primary" onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4" /> Ask for help
            </button>
          }
        />
      ) : (
        <div className="space-y-4">
          {requests.map((request) => (
            <article key={request.id} className="card animate-fade-in-up flex flex-wrap items-center gap-4 p-5">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="badge-primary">{CATEGORY_LABEL[request.category]}</span>
                  <span className="badge-success">{request.status.toLowerCase()}</span>
                  {nameOf(request.schoolId) && (
                    <span className="text-xs text-[var(--color-ink-faint)]">{nameOf(request.schoolId)}</span>
                  )}
                </div>
                <p className="mt-2.5 text-sm">{request.description}</p>
                <p className="mt-1 text-xs text-[var(--color-ink-faint)]">
                  Posted {new Date(request.createdAt).toLocaleDateString()} · anonymous
                </p>
              </div>
              <button className="btn-primary" onClick={() => fulfill(request)}>
                <HeartHandshake className="h-4 w-4" /> I can help
              </button>
            </article>
          ))}
        </div>
      )}

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Ask for help anonymously"
        description="Your name and email are never attached to this request — only you can see it in My requests."
        footer={
          <>
            <button className="btn-ghost" onClick={() => setCreateOpen(false)}>
              Cancel
            </button>
            <button className="btn-primary" onClick={submit}>
              Post anonymously
            </button>
          </>
        }
      >
        <div className="space-y-3">
          <select
            className="field"
            value={form.category}
            onChange={(e) => setForm({ ...form, category: e.target.value as SupportCategory })}
          >
            {(Object.keys(CATEGORY_LABEL) as SupportCategory[]).map((c) => (
              <option key={c} value={c}>
                {CATEGORY_LABEL[c]}
              </option>
            ))}
          </select>
          <select
            className="field"
            value={form.schoolId}
            onChange={(e) => setForm({ ...form, schoolId: e.target.value })}
          >
            <option value="">No specific school</option>
            {schools.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          <textarea
            className="field min-h-28"
            placeholder="Describe what you need."
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        </div>
      </Modal>
    </>
  )
}

function MyRequests() {
  const { nameOf } = useSchools()
  const [requests, setRequests] = useState<AnonymousRequest[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api
      .get<AnonymousRequest[]>('/api/support/requests/mine')
      .then(setRequests)
      .catch(() => setRequests([]))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <Spinner />
  if (requests.length === 0)
    return (
      <EmptyState
        icon={<HeartHandshake className="h-6 w-6" />}
        title="You haven't asked for anything yet"
        description="Requests you post stay anonymous to everyone else, but you can track their status here."
      />
    )

  return (
    <div className="space-y-4">
      {requests.map((request) => (
        <article key={request.id} className="card animate-fade-in-up p-5">
          <div className="flex flex-wrap items-center gap-2">
            <span className="badge-primary">{CATEGORY_LABEL[request.category]}</span>
            <span className={request.status === 'OPEN' ? 'badge-success' : 'badge-neutral'}>
              {request.status.toLowerCase()}
            </span>
            {nameOf(request.schoolId) && (
              <span className="text-xs text-[var(--color-ink-faint)]">{nameOf(request.schoolId)}</span>
            )}
          </div>
          <p className="mt-2.5 text-sm">{request.description}</p>
          <p className="mt-1 text-xs text-[var(--color-ink-faint)]">
            Submitted {new Date(request.createdAt).toLocaleString()}
          </p>
        </article>
      ))}
    </div>
  )
}
