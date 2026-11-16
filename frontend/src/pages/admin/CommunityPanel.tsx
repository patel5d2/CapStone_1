import { useEffect, useState } from 'react'
import { CalendarDays, MessageSquare, MessagesSquare, Trash2, Users } from 'lucide-react'
import { api, ApiError } from '../../lib/api'
import { useToast } from '../../components/ui/Toast'
import { Modal } from '../../components/ui/Modal'
import { Tabs } from '../../components/ui/Tabs'
import { Spinner } from '../../components/ui/Feedback'
import type { AdminComment, AdminEvent, AdminGroup, AdminPost } from '../../types'
import { formatDate, messageOf, plural, usePagedList } from './adminList'
import { Byline, ConfirmDialog, ListFrame, Row, SearchBar, Strong } from './shared'

type View = 'posts' | 'groups' | 'events'

const UNDONE = <Strong key="undone">This cannot be undone.</Strong>

/** Posts, groups and events from every school and every group, with the means to remove each. */
export default function CommunityPanel() {
  const [view, setView] = useState<View>('posts')
  return (
    <div>
      <div className="mb-4">
        <Tabs<View> value={view} onChange={setView}
          options={[{ value: 'posts', label: 'Posts' }, { value: 'groups', label: 'Groups' }, { value: 'events', label: 'Events' }]} />
      </div>
      {/* Each list loads only when its tab is opened. */}
      {view === 'posts' && <Posts />}
      {view === 'groups' && <Groups />}
      {view === 'events' && <Events />}
    </div>
  )
}

function Posts() {
  const { push } = useToast()
  const [query, setQuery] = useState('')
  const posts = usePagedList<AdminPost>('/api/admin/posts', { query })
  const [deleting, setDeleting] = useState<AdminPost | null>(null)
  const [reading, setReading] = useState<AdminPost | null>(null)

  const alreadyGone = () => {
    setDeleting(null)
    setReading(null)
    push('That post no longer exists. The list has been refreshed.', 'error')
    posts.reload()
  }

  return (
    <>
      <SearchBar label="Search posts by text or author email" placeholder="Search by text or author email"
        applied={query} onSearch={(next) => { posts.setPage(0); setQuery(next) }} />
      <ListFrame list={posts} noun="post" icon={<MessageSquare className="h-6 w-6" />}
        emptyTitle={query ? 'No posts matched' : 'No posts yet'}
        emptyDescription={query ? `Nothing matches “${query}”.` : 'Posts appear here as students write them.'}>
        {(post) => (
          <Row key={post.id}
            lead={post.imageUrl ? <img src={post.imageUrl} alt="" className="h-14 w-14 shrink-0 rounded-xl object-cover" /> : undefined}
            actions={
              <>
                {post.commentCount > 0 && (
                  <button className="btn-secondary btn-sm" onClick={() => setReading(post)}>
                    <MessagesSquare className="h-3.5 w-3.5" /> {plural(post.commentCount, 'comment')}
                  </button>
                )}
                <button className="btn-danger btn-sm" onClick={() => setDeleting(post)} aria-label="Delete this post">
                  <Trash2 className="h-3.5 w-3.5" /> Delete
                </button>
              </>
            }>
            <p className="line-clamp-4 text-sm break-words whitespace-pre-wrap">{post.content}</p>
            <p className="mt-1 flex flex-wrap items-center gap-2 text-xs text-[var(--color-ink-muted)]">
              <span className="badge-neutral normal-case">{post.groupId == null ? 'Main feed' : post.groupName ?? 'A group'}</span>
              {plural(post.likeCount, 'like')}
            </p>
            <Byline email={post.authorEmail} date={post.createdAt} />
          </Row>
        )}
      </ListFrame>

      {reading && (
        <CommentsDialog key={reading.id} post={reading} onClose={() => { setReading(null); posts.reload() }} onMissing={alreadyGone} />
      )}

      {deleting && (
        <ConfirmDialog key={deleting.id}
          title="Delete this post?"
          target={<><p className="line-clamp-6 break-words whitespace-pre-wrap">{deleting.content}</p><Byline email={deleting.authorEmail} date={deleting.createdAt} /></>}
          consequences={[
            deleting.commentCount > 0
              ? `The post is removed for everyone, along with the ${plural(deleting.commentCount, 'comment')} under it and its likes.`
              : 'The post is removed for everyone, along with its likes.',
            'The author is not notified.',
            UNDONE,
          ]}
          confirmLabel="Delete post" busyLabel="Deleting…" failurePrefix="The post was not deleted."
          onClose={() => setDeleting(null)}
          onMissing={alreadyGone}
          onConfirm={async () => {
            await api.del(`/api/admin/posts/${deleting.id}`)
            setDeleting(null)
            push('Post deleted.', 'success')
            posts.reload()
          }} />
      )}
    </>
  )
}

/** The comments under one post. Each can be removed on its own, leaving the post in place. */
function CommentsDialog({ post, onClose, onMissing }: { post: AdminPost; onClose: () => void; onMissing: () => void }) {
  const { push } = useToast()
  const [comments, setComments] = useState<AdminComment[] | null>(null)
  const [failure, setFailure] = useState<string | null>(null)
  const [removing, setRemoving] = useState<number | null>(null)

  useEffect(() => {
    let cancelled = false
    api.get<AdminComment[]>(`/api/admin/posts/${post.id}/comments`)
      .then((found) => { if (!cancelled) setComments(found) })
      .catch((e: unknown) => {
        if (cancelled) return
        if (e instanceof ApiError && e.status === 404) onMissing()
        else setFailure(messageOf(e, 'The comments could not be loaded.'))
      })
    return () => { cancelled = true }
    // Loaded once per post; the dialog is keyed by it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [post.id])

  const remove = async (comment: AdminComment) => {
    setRemoving(comment.id)
    setFailure(null)
    try {
      await api.del(`/api/admin/comments/${comment.id}`)
    } catch (e) {
      // Already gone is the outcome that was wanted; anything else is a real failure.
      if (!(e instanceof ApiError && e.status === 404)) {
        setRemoving(null)
        setFailure(`The comment was not deleted. ${messageOf(e, 'Try again.')}`)
        return
      }
    }
    setRemoving(null)
    setComments((current) => current?.filter((item) => item.id !== comment.id) ?? null)
    push('Comment deleted.', 'success')
  }

  return (
    <Modal open onClose={() => { if (removing == null) onClose() }} title="Comments on this post"
      description="Deleting a comment removes it for everyone and cannot be undone."
      footer={<button type="button" className="btn-ghost" onClick={onClose} disabled={removing != null}>Close</button>}>
      <div className="space-y-3 text-sm">
        <div className="rounded-xl border border-[var(--color-border-strong)] bg-[var(--color-surface-muted)] px-4 py-3">
          <p className="line-clamp-4 break-words whitespace-pre-wrap">{post.content}</p>
          <Byline email={post.authorEmail} date={post.createdAt} />
        </div>
        {failure && <p role="alert" className="rounded-xl bg-[var(--color-danger-bg)] px-3 py-2 text-[var(--color-danger)]">{failure}</p>}
        {!comments && !failure && <div role="status"><Spinner label="Loading comments…" /></div>}
        {comments && comments.length === 0 && <p className="text-[var(--color-ink-muted)]">No comments are left on this post.</p>}
        {comments?.map((comment) => (
          <div key={comment.id} className="flex items-start gap-3 rounded-xl border border-[var(--color-border)] px-4 py-3">
            <div className="min-w-0 flex-1">
              <p className="break-words whitespace-pre-wrap">{comment.content}</p>
              <Byline email={comment.authorEmail} date={comment.createdAt} />
            </div>
            <button className="btn-danger btn-sm shrink-0" disabled={removing != null} onClick={() => void remove(comment)}
              aria-label={`Delete the comment by ${comment.authorEmail}`}>
              <Trash2 className="h-3.5 w-3.5" /> {removing === comment.id ? 'Deleting…' : 'Delete'}
            </button>
          </div>
        ))}
      </div>
    </Modal>
  )
}

function Groups() {
  const { push } = useToast()
  const [query, setQuery] = useState('')
  const groups = usePagedList<AdminGroup>('/api/admin/groups', { query })
  const [deleting, setDeleting] = useState<AdminGroup | null>(null)

  return (
    <>
      <SearchBar label="Search groups by name, description or creator email" placeholder="Search by name, description or creator email"
        applied={query} onSearch={(next) => { groups.setPage(0); setQuery(next) }} />
      <ListFrame list={groups} noun="group" icon={<Users className="h-6 w-6" />}
        emptyTitle={query ? 'No groups matched' : 'No groups yet'}
        emptyDescription={query ? `Nothing matches “${query}”.` : 'Groups appear here as students create them.'}>
        {(group) => (
          <Row key={group.id}
            actions={
              <button className="btn-danger btn-sm" onClick={() => setDeleting(group)} aria-label={`Delete ${group.name}`}>
                <Trash2 className="h-3.5 w-3.5" /> Delete
              </button>
            }>
            <p className="font-bold break-words">{group.name}</p>
            {group.description && <p className="line-clamp-2 text-sm break-words">{group.description}</p>}
            <p className="mt-1 text-xs text-[var(--color-ink-muted)]">{plural(group.memberCount, 'member')} · {plural(group.postCount, 'post')}</p>
            <Byline email={`Created by ${group.createdByEmail}`} date={group.createdAt} />
          </Row>
        )}
      </ListFrame>

      {deleting && (
        <ConfirmDialog key={deleting.id}
          title="Delete this group?"
          target={<><p className="text-base font-bold break-words">{deleting.name}</p><Byline email={`Created by ${deleting.createdByEmail}`} date={deleting.createdAt} /></>}
          consequences={[
            <>The group is removed for all {plural(deleting.memberCount, 'member')}.</>,
            deleting.postCount > 0
              ? <><Strong>{`All ${plural(deleting.postCount, 'post')} in it are deleted too`}</Strong>, with their comments, whoever wrote them. To remove one post instead, use the Posts tab.</>
              : 'It has no posts.',
            UNDONE,
          ]}
          acknowledgement={deleting.postCount > 0 ? `I want to delete this group and the ${plural(deleting.postCount, 'post')} in it.` : undefined}
          confirmLabel="Delete group" busyLabel="Deleting…" failurePrefix="The group was not deleted."
          onClose={() => setDeleting(null)}
          onMissing={() => { setDeleting(null); push('That group no longer exists. The list has been refreshed.', 'error'); groups.reload() }}
          onConfirm={async () => {
            await api.del(`/api/admin/groups/${deleting.id}`)
            setDeleting(null)
            push(`Deleted the group “${deleting.name}”.`, 'success')
            groups.reload()
          }} />
      )}
    </>
  )
}

function Events() {
  const { push } = useToast()
  const [query, setQuery] = useState('')
  const events = usePagedList<AdminEvent>('/api/admin/events', { query })
  const [deleting, setDeleting] = useState<AdminEvent | null>(null)
  // Read once when the tab opens: "past" does not need to tick while the list is on screen.
  const [openedAt] = useState(() => Date.now())

  const when = (event: AdminEvent) => [formatDate(event.startsAt, true), event.location, event.schoolName].filter(Boolean).join(' · ')

  return (
    <>
      <SearchBar label="Search events by title, description or creator email" placeholder="Search by title, description or creator email"
        applied={query} onSearch={(next) => { events.setPage(0); setQuery(next) }} />
      <ListFrame list={events} noun="event" icon={<CalendarDays className="h-6 w-6" />}
        emptyTitle={query ? 'No events matched' : 'No events yet'}
        emptyDescription={query ? `Nothing matches “${query}”.` : 'Events appear here as students create them, past ones included.'}>
        {(event) => (
          <Row key={event.id}
            actions={
              <button className="btn-danger btn-sm" onClick={() => setDeleting(event)} aria-label={`Delete ${event.title}`}>
                <Trash2 className="h-3.5 w-3.5" /> Delete
              </button>
            }>
            <p className="flex flex-wrap items-center gap-2">
              <span className="font-bold break-words">{event.title}</span>
              {new Date(event.startsAt).getTime() < openedAt && <span className="badge-neutral">Past</span>}
            </p>
            <p className="text-sm text-[var(--color-ink-muted)]">{when(event)}</p>
            {event.description && <p className="mt-1 line-clamp-2 text-sm break-words">{event.description}</p>}
            <Byline email={`Created by ${event.createdByEmail}`} date={event.createdAt} />
          </Row>
        )}
      </ListFrame>

      {deleting && (
        <ConfirmDialog key={deleting.id}
          title="Delete this event?"
          target={<><p className="text-base font-bold break-words">{deleting.title}</p><p className="text-[var(--color-ink-muted)]">{when(deleting)}</p><Byline email={`Created by ${deleting.createdByEmail}`} date={deleting.createdAt} /></>}
          consequences={['The event is removed from Community for everyone. Its creator is not notified.', UNDONE]}
          confirmLabel="Delete event" busyLabel="Deleting…" failurePrefix="The event was not deleted."
          onClose={() => setDeleting(null)}
          onMissing={() => { setDeleting(null); push('That event no longer exists. The list has been refreshed.', 'error'); events.reload() }}
          onConfirm={async () => {
            await api.del(`/api/admin/events/${deleting.id}`)
            setDeleting(null)
            push(`Deleted the event “${deleting.title}”.`, 'success')
            events.reload()
          }} />
      )}
    </>
  )
}
