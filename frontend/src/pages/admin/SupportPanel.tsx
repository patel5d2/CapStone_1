import { useCallback, useEffect, useRef, useState } from 'react'
import { HeartHandshake, LifeBuoy, Pencil, Plus, Trash2 } from 'lucide-react'
import { api, ApiError } from '../../lib/api'
import { useSchools } from '../../hooks/useSchools'
import { useToast } from '../../components/ui/Toast'
import { Modal } from '../../components/ui/Modal'
import { Tabs } from '../../components/ui/Tabs'
import { EmptyState, ErrorState, Spinner } from '../../components/ui/Feedback'
import type { AdminSupportRequest, AdminSupportResourceRequest, RequestStatus, School, SupportCategory, SupportResource } from '../../types'
import { formatDate, messageOf, plural, usePagedList } from './adminList'
import { ConfirmDialog, ListFrame, Row, Strong } from './shared'

type View = 'requests' | 'resources'

const CATEGORIES: { value: SupportCategory; label: string }[] = [
  { value: 'FOOD_PANTRY', label: 'Food pantry' },
  { value: 'EMERGENCY_AID', label: 'Emergency aid' },
  { value: 'COUNSELING', label: 'Counseling' },
  { value: 'OTHER', label: 'Other' },
]
const categoryLabel = (value: SupportCategory) => CATEGORIES.find((category) => category.value === value)?.label ?? value
const STATUS_LABEL: Record<RequestStatus, string> = { OPEN: 'Open', FULFILLED: 'Fulfilled', CLOSED: 'Closed' }
const STATUS_BADGE: Record<RequestStatus, string> = { OPEN: 'badge-warning', FULFILLED: 'badge-success', CLOSED: 'badge-neutral' }

/** Requests students post for help, and the campus resources listed on the Support tab. */
export default function SupportPanel() {
  const [view, setView] = useState<View>('requests')
  return (
    <div>
      <div className="mb-4">
        <Tabs<View> value={view} onChange={setView}
          options={[{ value: 'requests', label: 'Help requests' }, { value: 'resources', label: 'Campus resources' }]} />
      </div>
      {view === 'requests' ? <Requests /> : <Resources />}
    </div>
  )
}

function Requests() {
  const { push } = useToast()
  const [status, setStatus] = useState<RequestStatus | ''>('')
  const requests = usePagedList<AdminSupportRequest>('/api/admin/support/requests', { status: status || null })
  const [deleting, setDeleting] = useState<AdminSupportRequest | null>(null)
  const [changing, setChanging] = useState<number | null>(null)

  const mark = async (request: AdminSupportRequest, next: RequestStatus) => {
    setChanging(request.id)
    try {
      await api.put<AdminSupportRequest>(`/api/admin/support/requests/${request.id}/status`, { status: next })
      push(`Request marked ${STATUS_LABEL[next].toLowerCase()}.`, 'success')
    } catch (e) {
      push(e instanceof ApiError && e.status === 404
        ? 'That request no longer exists. The list has been refreshed.'
        : `The request was not changed. ${messageOf(e, 'Try again.')}`, 'error')
    } finally {
      setChanging(null)
      // Refetched rather than patched in place: with a status filter on, the row may no longer belong here.
      requests.reload()
    }
  }

  return (
    <>
      <div className="card mb-4 flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
        <p className="flex-1 text-sm text-[var(--color-ink-muted)]">
          Requests are anonymous. You can close or remove one, but not see who posted it.
        </p>
        <select aria-label="Filter requests by status" className="field sm:w-48" value={status}
          onChange={(event) => { requests.setPage(0); setStatus(event.target.value as RequestStatus | '') }}>
          <option value="">Every status</option>
          {(Object.keys(STATUS_LABEL) as RequestStatus[]).map((value) => <option key={value} value={value}>{STATUS_LABEL[value]}</option>)}
        </select>
      </div>

      <ListFrame list={requests} noun="request" icon={<HeartHandshake className="h-6 w-6" />}
        emptyTitle={status ? `No ${STATUS_LABEL[status].toLowerCase()} requests` : 'No help requests yet'}
        emptyDescription="Requests appear here when students ask for help on the Support tab.">
        {(request) => (
          <Row key={request.id}
            actions={
              <>
                {request.status !== 'OPEN' && <button className="btn-ghost btn-sm" disabled={changing != null} onClick={() => void mark(request, 'OPEN')}>Reopen</button>}
                {request.status === 'OPEN' && <button className="btn-secondary btn-sm" disabled={changing != null} onClick={() => void mark(request, 'FULFILLED')}>Mark fulfilled</button>}
                {request.status === 'OPEN' && <button className="btn-ghost btn-sm" disabled={changing != null} onClick={() => void mark(request, 'CLOSED')}>Close</button>}
                <button className="btn-danger btn-sm" disabled={changing != null} onClick={() => setDeleting(request)} aria-label="Delete this request">
                  <Trash2 className="h-3.5 w-3.5" /> Delete
                </button>
              </>
            }>
            <p className="flex flex-wrap items-center gap-2">
              <span className="badge-primary">{categoryLabel(request.category)}</span>
              <span className={STATUS_BADGE[request.status]}>{STATUS_LABEL[request.status]}</span>
            </p>
            <p className="mt-1 text-sm break-words whitespace-pre-wrap">{request.description}</p>
            <p className="mt-0.5 text-xs text-[var(--color-ink-faint)]">{[request.schoolName ?? 'Any school', formatDate(request.createdAt)].filter(Boolean).join(' · ')}</p>
          </Row>
        )}
      </ListFrame>

      {deleting && (
        <ConfirmDialog key={deleting.id}
          title="Delete this help request?"
          target={<><p className="font-bold">{categoryLabel(deleting.category)}</p><p className="mt-1 line-clamp-6 break-words whitespace-pre-wrap">{deleting.description}</p></>}
          consequences={[
            'The request is removed from the Support tab for everyone, including the student who posted it.',
            'If it is a genuine request that has been answered, mark it fulfilled or close it instead.',
            <Strong key="undone">This cannot be undone.</Strong>,
          ]}
          confirmLabel="Delete request" busyLabel="Deleting…" failurePrefix="The request was not deleted."
          onClose={() => setDeleting(null)}
          onMissing={() => { setDeleting(null); push('That request no longer exists. The list has been refreshed.', 'error'); requests.reload() }}
          onConfirm={async () => {
            await api.del(`/api/admin/support/requests/${deleting.id}`)
            setDeleting(null)
            push('Request deleted.', 'success')
            requests.reload()
          }} />
      )}
    </>
  )
}

function Resources() {
  const { push } = useToast()
  const { schools, nameOf: schoolName } = useSchools()
  const [schoolId, setSchoolId] = useState('')
  const [state, setState] = useState<{ key: number; resources?: SupportResource[]; error?: string } | null>(null)
  const [reloads, setReloads] = useState(0)
  const [editing, setEditing] = useState<SupportResource | 'new' | null>(null)
  const [deleting, setDeleting] = useState<SupportResource | null>(null)
  const reload = useCallback(() => setReloads((count) => count + 1), [])

  // The student-facing list: what an administrator edits is exactly what students see.
  useEffect(() => {
    let cancelled = false
    api.get<SupportResource[]>('/api/support/resources')
      .then((resources) => { if (!cancelled) setState({ key: reloads, resources }) })
      .catch((e: unknown) => { if (!cancelled) setState({ key: reloads, error: messageOf(e, 'The resources could not be loaded.') }) })
    return () => { cancelled = true }
  }, [reloads])

  const all = state?.resources ?? []
  const shown = [...(schoolId ? all.filter((resource) => String(resource.schoolId) === schoolId) : all)]
    .sort((a, b) => (schoolName(a.schoolId) ?? '').localeCompare(schoolName(b.schoolId) ?? '') || a.name.localeCompare(b.name))

  return (
    <>
      <div className="card mb-4 flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
        <select aria-label="Filter resources by school" className="field" value={schoolId} onChange={(event) => setSchoolId(event.target.value)}>
          <option value="">Every school</option>
          {schools.map((school) => <option key={school.id} value={school.id}>{school.name}</option>)}
        </select>
        <button className="btn-primary shrink-0" onClick={() => setEditing('new')}><Plus className="h-4 w-4" /> Add resource</button>
      </div>

      {state?.key !== reloads ? (
        <div role="status"><Spinner label="Loading resources…" /></div>
      ) : state.error ? (
        <ErrorState message={state.error} onRetry={reload} />
      ) : shown.length === 0 ? (
        <EmptyState icon={<LifeBuoy className="h-6 w-6" />} title="No campus resources"
          description={schoolId ? 'This school has none listed yet. Add one so its students have somewhere to turn.' : 'Add the first one so students have somewhere to turn.'} />
      ) : (
        <>
          <p className="mb-3 text-sm text-[var(--color-ink-muted)]" aria-live="polite">{plural(shown.length, 'resource')}</p>
          <ul className="card divide-y divide-[var(--color-border)]">
            {shown.map((resource) => (
              <Row key={resource.id}
                actions={
                  <>
                    <button className="btn-secondary btn-sm" onClick={() => setEditing(resource)} aria-label={`Edit ${resource.name}`}>
                      <Pencil className="h-3.5 w-3.5" /> Edit
                    </button>
                    <button className="btn-danger btn-sm" onClick={() => setDeleting(resource)} aria-label={`Delete ${resource.name}`}>
                      <Trash2 className="h-3.5 w-3.5" /> Delete
                    </button>
                  </>
                }>
                <p className="flex flex-wrap items-center gap-2">
                  <span className="font-bold break-words">{resource.name}</span>
                  <span className="badge-primary">{categoryLabel(resource.category)}</span>
                </p>
                <p className="text-sm text-[var(--color-ink-muted)]">{schoolName(resource.schoolId) ?? 'School unavailable'}</p>
                {resource.description && <p className="mt-1 line-clamp-2 text-sm break-words">{resource.description}</p>}
                <p className="mt-0.5 text-xs break-words text-[var(--color-ink-faint)]">{[resource.contactInfo, resource.address].filter(Boolean).join(' · ')}</p>
              </Row>
            ))}
          </ul>
        </>
      )}

      {editing && (
        <ResourceDialog key={editing === 'new' ? 'new' : editing.id} resource={editing === 'new' ? null : editing} schools={schools}
          defaultSchoolId={schoolId}
          onClose={() => setEditing(null)}
          onMissing={() => { setEditing(null); push('That resource no longer exists. The list has been refreshed.', 'error'); reload() }}
          onSaved={(saved) => {
            setEditing(null)
            push(`Saved “${saved.name}”. Students see it on the Support tab now.`, 'success')
            reload()
          }} />
      )}

      {deleting && (
        <ConfirmDialog key={deleting.id}
          title="Delete this campus resource?"
          target={<><p className="text-base font-bold break-words">{deleting.name}</p><p className="text-[var(--color-ink-muted)]">{[categoryLabel(deleting.category), schoolName(deleting.schoolId)].filter(Boolean).join(' · ')}</p></>}
          consequences={[
            'Students at this school will no longer see it on the Support tab.',
            'If its details are only out of date, edit it instead.',
            <Strong key="undone">This cannot be undone.</Strong>,
          ]}
          confirmLabel="Delete resource" busyLabel="Deleting…" failurePrefix="The resource was not deleted."
          onClose={() => setDeleting(null)}
          onMissing={() => { setDeleting(null); push('That resource no longer exists. The list has been refreshed.', 'error'); reload() }}
          onConfirm={async () => {
            await api.del(`/api/admin/support/resources/${deleting.id}`)
            setDeleting(null)
            push(`Deleted “${deleting.name}”.`, 'success')
            reload()
          }} />
      )}
    </>
  )
}

interface ResourceForm {
  schoolId: string
  category: SupportCategory
  name: string
  description: string
  contactInfo: string
  address: string
}
type ResourceErrors = Partial<Record<keyof ResourceForm, string>>

/** Adds a campus resource, or corrects one. Checks mirror `AdminSupportResourceRequest`; the server decides. */
function ResourceDialog({ resource, schools, defaultSchoolId, onClose, onSaved, onMissing }: {
  resource: SupportResource | null
  schools: School[]
  defaultSchoolId: string
  onClose: () => void
  onSaved: (saved: SupportResource) => void
  onMissing: () => void
}) {
  const [form, setForm] = useState<ResourceForm>({
    schoolId: resource ? String(resource.schoolId) : defaultSchoolId,
    category: resource?.category ?? 'FOOD_PANTRY',
    name: resource?.name ?? '',
    description: resource?.description ?? '',
    contactInfo: resource?.contactInfo ?? '',
    address: resource?.address ?? '',
  })
  const [errors, setErrors] = useState<ResourceErrors>({})
  const [failure, setFailure] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const busy = useRef(false)
  const close = useCallback(() => { if (!busy.current) onClose() }, [onClose])

  const save = async (event: React.FormEvent) => {
    event.preventDefault()
    const problems: ResourceErrors = {}
    if (!schools.some((school) => String(school.id) === form.schoolId)) problems.schoolId = 'Choose a school.'
    if (!form.name.trim()) problems.name = 'This field is required.'
    if (form.name.length > 255) problems.name = 'Use 255 characters or fewer.'
    if (form.description.length > 2000) problems.description = 'Use 2000 characters or fewer.'
    if (form.contactInfo.length > 255) problems.contactInfo = 'Use 255 characters or fewer.'
    if (form.address.length > 255) problems.address = 'Use 255 characters or fewer.'
    setErrors(problems)
    setFailure(null)
    if (Object.keys(problems).length > 0) return

    const body: AdminSupportResourceRequest = {
      schoolId: Number(form.schoolId),
      category: form.category,
      name: form.name.trim(),
      description: form.description.trim() || null,
      contactInfo: form.contactInfo.trim() || null,
      address: form.address.trim() || null,
    }
    busy.current = true
    setSaving(true)
    try {
      onSaved(resource
        ? await api.put<SupportResource>(`/api/admin/support/resources/${resource.id}`, body)
        : await api.post<SupportResource>('/api/admin/support/resources', body))
    } catch (e) {
      busy.current = false
      setSaving(false)
      if (resource && e instanceof ApiError && e.status === 404) {
        onMissing()
        return
      }
      if (e instanceof ApiError && e.fieldErrors) setErrors(e.fieldErrors as ResourceErrors)
      setFailure(`Nothing was saved. ${messageOf(e, 'The resource could not be saved.')}`)
    }
  }

  const field = (key: keyof ResourceForm) => ({
    id: `admin-resource-${key}`,
    name: key,
    className: 'field',
    value: form[key],
    'aria-invalid': Boolean(errors[key]),
    'aria-describedby': errors[key] ? `admin-resource-${key}-error` : undefined,
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
      setForm((previous) => ({ ...previous, [key]: event.target.value }))
      setErrors((previous) => ({ ...previous, [key]: undefined }))
    },
  })
  const labelled = (key: keyof ResourceForm, label: string, required: boolean, control: React.ReactNode) => (
    <div>
      <label htmlFor={`admin-resource-${key}`} className="mb-1.5 block text-sm font-semibold">
        {label} <span className="font-normal text-[var(--color-ink-muted)]">({required ? 'required' : 'optional'})</span>
      </label>
      {control}
      {errors[key] && <p id={`admin-resource-${key}-error`} className="mt-1 text-sm text-[var(--color-danger)]">{errors[key]}</p>}
    </div>
  )

  return (
    <Modal open onClose={close} title={resource ? `Edit ${resource.name}` : 'Add a campus resource'}
      description="Shown to students of the school on the Support tab."
      footer={
        <>
          <button type="button" className="btn-ghost" onClick={close} disabled={saving}>Cancel</button>
          <button type="submit" form="admin-resource-form" className="btn-primary" disabled={saving}>
            {saving ? 'Saving…' : resource ? 'Save changes' : 'Add resource'}
          </button>
        </>
      }>
      <form id="admin-resource-form" onSubmit={save} noValidate className="space-y-4">
        {failure && <p role="alert" className="rounded-xl bg-[var(--color-danger-bg)] px-3 py-2 text-sm text-[var(--color-danger)]">{failure}</p>}
        {labelled('name', 'Name', true, <input {...field('name')} />)}
        <div className="grid gap-4 sm:grid-cols-2">
          {labelled('schoolId', 'School', true,
            <select {...field('schoolId')}>
              <option value="">Choose a school</option>
              {schools.map((school) => <option key={school.id} value={school.id}>{school.name}</option>)}
            </select>)}
          {labelled('category', 'Category', true,
            <select {...field('category')}>
              {CATEGORIES.map((category) => <option key={category.value} value={category.value}>{category.label}</option>)}
            </select>)}
        </div>
        {labelled('description', 'Description', false, <textarea {...field('description')} rows={3} />)}
        {labelled('contactInfo', 'Contact details', false, <input {...field('contactInfo')} placeholder="Phone, email or opening hours" />)}
        {labelled('address', 'Address', false, <input {...field('address')} />)}
      </form>
    </Modal>
  )
}
