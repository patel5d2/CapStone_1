import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Users, Heart, MessageSquare, Trash2, CalendarDays, Plus, MapPin, Send, Pin } from 'lucide-react'
import { api } from '../lib/api'
import { useSchools } from '../hooks/useSchools'
import { useToast } from '../components/ui/Toast'
import { Modal } from '../components/ui/Modal'
import { PageHeader, Tabs } from '../components/ui/Tabs'
import { EmptyState, Spinner } from '../components/ui/Feedback'
import Directory from './Directory'
import type { CampusEvent, Comment, CommunityGroup, GroupType, Post } from '../types'

const SECTIONS = ['feed', 'directory', 'groups', 'events'] as const
type Section = (typeof SECTIONS)[number]

const GROUP_TYPES: { value: GroupType; label: string; hint: string }[] = [
  { value: 'MAJOR', label: 'Major', hint: 'e.g. Computer Science' },
  { value: 'GRADUATION_YEAR', label: 'Graduation year', hint: 'e.g. 2027' },
  { value: 'COURSE_STUDY', label: 'Course study group', hint: 'e.g. CS 2021' },
  { value: 'GENERAL', label: 'General', hint: 'Anything else' },
]

export default function Community() {
  // The section lives in the URL so /community?tab=directory is linkable and the
  // redirect from the retired /directory route lands on the right sub-surface.
  const [params, setParams] = useSearchParams()
  const requested = params.get('tab') as Section | null
  const section: Section = requested && SECTIONS.includes(requested) ? requested : 'feed'
  const setSection = (next: Section) =>
    setParams(next === 'feed' ? {} : { tab: next }, { replace: true })

  return (
    <>
      <PageHeader
        title="Community"
        subtitle="The student directory, groups, study partners, and events across every Cincinnati-area school."
      />
      <div className="mb-6">
        <Tabs
          value={section}
          onChange={setSection}
          options={[
            { value: 'feed', label: 'Feed' },
            { value: 'directory', label: 'Directory' },
            { value: 'groups', label: 'Groups' },
            { value: 'events', label: 'Events' },
          ]}
        />
      </div>

      {section === 'feed' && <Feed />}
      {section === 'directory' && <Directory />}
      {section === 'groups' && <Groups />}
      {section === 'events' && <Events />}
    </>
  )
}

function Feed() {
  const { push } = useToast()
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(true)
  const [draft, setDraft] = useState('')
  const [openComments, setOpenComments] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setPosts(await api.get<Post[]>('/api/community/posts'))
    } catch {
      push('Could not load the feed', 'error')
    } finally {
      setLoading(false)
    }
  }, [push])

  useEffect(() => {
    void load()
  }, [load])

  const submit = async () => {
    const content = draft.trim()
    if (!content) return
    setDraft('')
    try {
      await api.post<Post>('/api/community/posts', { content })
      void load()
    } catch {
      push('Could not post', 'error')
      setDraft(content)
    }
  }

  const toggleLike = async (post: Post) => {
    const path = `/api/community/posts/${post.id}/like`
    try {
      if (post.likedByMe) await api.del(path)
      else await api.post(path)
      setPosts((prev) =>
        prev.map((p) =>
          p.id === post.id
            ? { ...p, likedByMe: !p.likedByMe, likeCount: p.likeCount + (p.likedByMe ? -1 : 1) }
            : p,
        ),
      )
    } catch {
      push('Could not update your like', 'error')
    }
  }

  const remove = async (post: Post) => {
    if (!confirm('Delete this post?')) return
    try {
      await api.del(`/api/community/posts/${post.id}`)
      push('Post deleted', 'success')
      void load()
    } catch (e) {
      push(e instanceof Error ? e.message : 'Could not delete post', 'error')
    }
  }

  // Only the author may pin, so the backend answers 403 for everyone else.
  const togglePin = async (post: Post) => {
    try {
      await api.post(`/api/community/posts/${post.id}/pin`)
      push(post.pinned ? 'Post unpinned' : 'Post pinned', 'success')
      void load()
    } catch (e) {
      push(e instanceof Error ? e.message : 'Only the author can pin this post', 'error')
    }
  }

  return (
    <div className="space-y-4">
      <div className="card p-4">
        <textarea
          className="field min-h-20"
          placeholder="Share something with students across all six schools…"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
        />
        <div className="mt-3 flex justify-end">
          <button className="btn-primary" onClick={submit} disabled={!draft.trim()}>
            <Send className="h-4 w-4" /> Post
          </button>
        </div>
      </div>

      {loading ? (
        <Spinner />
      ) : posts.length === 0 ? (
        <EmptyState
          icon={<MessageSquare className="h-6 w-6" />}
          title="No posts yet"
          description="Be the first to start a conversation in the community feed."
        />
      ) : (
        posts.map((post) => (
          <article key={post.id} className="card animate-fade-in-up p-5">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2.5">
                <span className="flex h-9 w-9 items-center justify-center rounded-full bg-primary-100 text-sm font-bold text-primary-700 dark:bg-primary-900/50 dark:text-primary-200">
                  {post.authorEmail.charAt(0).toUpperCase()}
                </span>
                <div>
                  <p className="text-sm font-semibold">{post.authorEmail}</p>
                  <p className="text-xs text-[var(--color-ink-faint)]">
                    {new Date(post.createdAt).toLocaleString()}
                  </p>
                </div>
              </div>
              {post.pinned && <span className="badge-primary">Pinned</span>}
            </div>

            <p className="mt-3 text-sm whitespace-pre-wrap">{post.content}</p>

            <div className="mt-4 flex gap-2 border-t border-[var(--color-border)] pt-3">
              <button
                className={`btn-sm ${post.likedByMe ? 'btn-secondary' : 'btn-ghost'}`}
                onClick={() => toggleLike(post)}
              >
                <Heart className={`h-3.5 w-3.5 ${post.likedByMe ? 'fill-current' : ''}`} /> {post.likeCount}
              </button>
              <button
                className="btn-ghost btn-sm"
                onClick={() => setOpenComments(openComments === post.id ? null : post.id)}
              >
                <MessageSquare className="h-3.5 w-3.5" /> {post.commentCount}
              </button>
              <button
                className={`btn-sm ${post.pinned ? 'btn-secondary' : 'btn-ghost'}`}
                onClick={() => togglePin(post)}
                title={post.pinned ? 'Unpin this post' : 'Pin this post to the top'}
              >
                <Pin className="h-3.5 w-3.5" /> {post.pinned ? 'Unpin' : 'Pin'}
              </button>
              <button className="btn-danger btn-sm ml-auto" onClick={() => remove(post)}>
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </div>

            {openComments === post.id && <Comments postId={post.id} onChanged={load} />}
          </article>
        ))
      )}
    </div>
  )
}

function Comments({ postId, onChanged }: { postId: number; onChanged: () => void }) {
  const { push } = useToast()
  const [comments, setComments] = useState<Comment[]>([])
  const [draft, setDraft] = useState('')
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    try {
      setComments(await api.get<Comment[]>(`/api/community/posts/${postId}/comments`))
    } catch {
      /* leave the list empty */
    } finally {
      setLoading(false)
    }
  }, [postId])

  useEffect(() => {
    void load()
  }, [load])

  const submit = async () => {
    const content = draft.trim()
    if (!content) return
    setDraft('')
    try {
      await api.post<Comment>(`/api/community/posts/${postId}/comments`, { content })
      await load()
      onChanged()
    } catch {
      push('Could not add comment', 'error')
    }
  }

  return (
    <div className="mt-3 space-y-3 border-t border-[var(--color-border)] pt-3">
      {loading ? (
        <p className="text-xs text-[var(--color-ink-faint)]">Loading comments…</p>
      ) : (
        comments.map((comment) => (
          <div key={comment.id} className="rounded-xl bg-[var(--color-surface-muted)] px-3 py-2">
            <p className="text-xs font-semibold">{comment.authorEmail}</p>
            <p className="text-sm">{comment.content}</p>
          </div>
        ))
      )}
      <div className="flex gap-2">
        <input
          className="field"
          placeholder="Write a comment…"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && submit()}
        />
        <button className="btn-secondary" onClick={submit}>
          Reply
        </button>
      </div>
    </div>
  )
}

function Groups() {
  const { push } = useToast()
  const { schools } = useSchools()
  const [groups, setGroups] = useState<CommunityGroup[]>([])
  const [loading, setLoading] = useState(true)
  const [scope, setScope] = useState<'all' | 'mine'>('all')
  const [createOpen, setCreateOpen] = useState(false)
  const [form, setForm] = useState({
    name: '',
    description: '',
    type: 'MAJOR' as GroupType,
    relatedValue: '',
    schoolId: '',
  })

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const path = scope === 'mine' ? '/api/community/groups/mine' : '/api/community/groups'
      setGroups(await api.get<CommunityGroup[]>(path))
    } catch {
      push('Could not load groups', 'error')
    } finally {
      setLoading(false)
    }
  }, [push, scope])

  useEffect(() => {
    void load()
  }, [load])

  const submit = async () => {
    if (!form.name.trim()) {
      push('Give the group a name', 'error')
      return
    }
    try {
      await api.post<CommunityGroup>('/api/community/groups', {
        ...form,
        relatedValue: form.relatedValue || null,
        schoolId: form.schoolId ? Number(form.schoolId) : null,
      })
      push('Group created', 'success')
      setCreateOpen(false)
      setForm({ name: '', description: '', type: 'MAJOR', relatedValue: '', schoolId: '' })
      void load()
    } catch {
      push('Could not create group', 'error')
    }
  }

  const toggleMembership = async (group: CommunityGroup) => {
    try {
      await api.post(`/api/community/groups/${group.id}/${group.joined ? 'leave' : 'join'}`)
      void load()
    } catch {
      push('Could not update membership', 'error')
    }
  }

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <Tabs
          value={scope}
          onChange={setScope}
          options={[
            { value: 'all', label: 'All groups' },
            { value: 'mine', label: 'My groups' },
          ]}
        />
        <button className="btn-primary" onClick={() => setCreateOpen(true)}>
          <Plus className="h-4 w-4" /> New group
        </button>
      </div>

      {loading ? (
        <Spinner />
      ) : groups.length === 0 ? (
        <EmptyState
          icon={<Users className="h-6 w-6" />}
          title={scope === 'mine' ? "You haven't joined any groups" : 'No groups yet'}
          description={
            scope === 'mine'
              ? 'Join a group from the All groups tab, or start your own.'
              : "Start one for your major, graduation year, or a course you're taking."
          }
          action={
            <button className="btn-primary" onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4" /> New group
            </button>
          }
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {groups.map((group) => (
            <article key={group.id} className="card card-hover animate-fade-in-up flex flex-col p-5">
              <div className="flex items-start justify-between gap-2">
                <h3 className="text-base font-bold">{group.name}</h3>
                <span className="badge-primary">{group.type.replace('_', ' ').toLowerCase()}</span>
              </div>
              <p className="mt-1 text-xs text-[var(--color-ink-faint)]">
                {group.relatedValue && `${group.relatedValue} · `}
                {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'}
              </p>
              {group.description && (
                <p className="mt-3 line-clamp-3 flex-1 text-sm text-[var(--color-ink-muted)]">{group.description}</p>
              )}
              <button
                className={`mt-4 ${group.joined ? 'btn-ghost' : 'btn-primary'}`}
                onClick={() => toggleMembership(group)}
              >
                {group.joined ? 'Leave group' : 'Join group'}
              </button>
            </article>
          ))}
        </div>
      )}

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="New group"
        description="One engine powers major groups, graduation-year groups, and course study groups."
        footer={
          <>
            <button className="btn-ghost" onClick={() => setCreateOpen(false)}>
              Cancel
            </button>
            <button className="btn-primary" onClick={submit}>
              Create group
            </button>
          </>
        }
      >
        <div className="space-y-3">
          <input
            className="field"
            placeholder="Group name"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
          <select
            className="field"
            value={form.type}
            onChange={(e) => setForm({ ...form, type: e.target.value as GroupType })}
          >
            {GROUP_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
          <input
            className="field"
            placeholder={GROUP_TYPES.find((t) => t.value === form.type)?.hint}
            value={form.relatedValue}
            onChange={(e) => setForm({ ...form, relatedValue: e.target.value })}
          />
          <select
            className="field"
            value={form.schoolId}
            onChange={(e) => setForm({ ...form, schoolId: e.target.value })}
          >
            <option value="">Open to all schools</option>
            {schools.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          <textarea
            className="field min-h-24"
            placeholder="What is this group for?"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        </div>
      </Modal>
    </>
  )
}

function Events() {
  const { push } = useToast()
  const { schools, nameOf } = useSchools()
  const [events, setEvents] = useState<CampusEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [form, setForm] = useState({ title: '', description: '', startsAt: '', location: '', schoolId: '' })

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setEvents(await api.get<CampusEvent[]>('/api/community/events'))
    } catch {
      push('Could not load events', 'error')
    } finally {
      setLoading(false)
    }
  }, [push])

  useEffect(() => {
    void load()
  }, [load])

  const submit = async () => {
    if (!form.title.trim() || !form.startsAt) {
      push('Title and start time are required', 'error')
      return
    }
    try {
      await api.post<CampusEvent>('/api/community/events', {
        ...form,
        startsAt: new Date(form.startsAt).toISOString(),
        schoolId: form.schoolId ? Number(form.schoolId) : null,
      })
      push('Event posted', 'success')
      setCreateOpen(false)
      setForm({ title: '', description: '', startsAt: '', location: '', schoolId: '' })
      void load()
    } catch {
      push('Could not post event', 'error')
    }
  }

  return (
    <>
      <div className="mb-4 flex justify-end">
        <button className="btn-primary" onClick={() => setCreateOpen(true)}>
          <Plus className="h-4 w-4" /> New event
        </button>
      </div>

      {loading ? (
        <Spinner />
      ) : events.length === 0 ? (
        <EmptyState
          icon={<CalendarDays className="h-6 w-6" />}
          title="No upcoming events"
          description="Post a game, club meeting, or study session — tickets can link to a marketplace listing."
          action={
            <button className="btn-primary" onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4" /> New event
            </button>
          }
        />
      ) : (
        <div className="space-y-4">
          {events.map((event) => {
            const start = new Date(event.startsAt)
            return (
              <article key={event.id} className="card card-hover animate-fade-in-up flex gap-4 p-5">
                <div className="flex h-14 w-14 shrink-0 flex-col items-center justify-center rounded-xl bg-primary-50 text-primary-700 dark:bg-primary-900/40 dark:text-primary-200">
                  <span className="text-[11px] font-bold uppercase">
                    {start.toLocaleString(undefined, { month: 'short' })}
                  </span>
                  <span className="text-lg leading-none font-extrabold">{start.getDate()}</span>
                </div>
                <div className="min-w-0 flex-1">
                  <h3 className="text-base font-bold">{event.title}</h3>
                  <p className="mt-0.5 flex flex-wrap items-center gap-1.5 text-xs text-[var(--color-ink-muted)]">
                    {start.toLocaleString()}
                    {event.location && (
                      <>
                        <span>·</span>
                        <MapPin className="h-3 w-3" />
                        {event.location}
                      </>
                    )}
                    {nameOf(event.schoolId) && (
                      <>
                        <span>·</span>
                        {nameOf(event.schoolId)}
                      </>
                    )}
                  </p>
                  {event.description && (
                    <p className="mt-2 text-sm text-[var(--color-ink-muted)]">{event.description}</p>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      )}

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="New event"
        footer={
          <>
            <button className="btn-ghost" onClick={() => setCreateOpen(false)}>
              Cancel
            </button>
            <button className="btn-primary" onClick={submit}>
              Post event
            </button>
          </>
        }
      >
        <div className="space-y-3">
          <input
            className="field"
            placeholder="Event title"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
          />
          <input
            className="field"
            type="datetime-local"
            value={form.startsAt}
            onChange={(e) => setForm({ ...form, startsAt: e.target.value })}
          />
          <input
            className="field"
            placeholder="Location"
            value={form.location}
            onChange={(e) => setForm({ ...form, location: e.target.value })}
          />
          <select
            className="field"
            value={form.schoolId}
            onChange={(e) => setForm({ ...form, schoolId: e.target.value })}
          >
            <option value="">Open to all schools</option>
            {schools.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          <textarea
            className="field min-h-24"
            placeholder="Details"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        </div>
      </Modal>
    </>
  )
}
