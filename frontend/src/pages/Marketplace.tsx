import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Store, Plus, Heart, Flag, Trash2, CheckCircle2, MessageCircle, Search, BookOpen, Pencil } from 'lucide-react'
import { api, toQueryString } from '../lib/api'
import { useSchools } from '../hooks/useSchools'
import { useToast } from '../components/ui/Toast'
import { Modal } from '../components/ui/Modal'
import { PageHeader, Tabs } from '../components/ui/Tabs'
import { EmptyState, ErrorState, Spinner } from '../components/ui/Feedback'
import type { Listing, ListingCategory, ListingRequest, ListingType } from '../types'

type View = 'all' | 'mine' | 'favorites'

const CATEGORIES: { value: ListingCategory; label: string }[] = [
  { value: 'BOOKS', label: 'Books' },
  { value: 'CLOTHES', label: 'Clothes' },
  { value: 'EVENT_TICKETS', label: 'Event Tickets' },
  { value: 'FURNITURE', label: 'Furniture' },
  { value: 'ELECTRONICS', label: 'Electronics' },
  { value: 'OTHER', label: 'Other' },
]

const LISTING_TYPES: { value: ListingType; label: string }[] = [
  { value: 'SELL', label: 'For sale' },
  { value: 'RENT', label: 'For rent' },
  { value: 'FREE', label: 'Free / donate' },
  { value: 'LOOKING_FOR', label: 'Looking for' },
]

const EMPTY_FORM: ListingRequest = {
  title: '',
  description: '',
  category: 'BOOKS',
  listingType: 'SELL',
  price: null,
  courseCode: '',
  schoolId: null,
  photoUrls: [],
}

export default function Marketplace() {
  const navigate = useNavigate()
  const { push } = useToast()
  const { schools, nameOf } = useSchools()

  const [view, setView] = useState<View>('all')
  const [listings, setListings] = useState<Listing[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState<string>('')
  const [listingType, setListingType] = useState<string>('')
  const [courseCode, setCourseCode] = useState('')
  const [schoolId, setSchoolId] = useState<string>('')

  const [createOpen, setCreateOpen] = useState(false)
  const [form, setForm] = useState<ListingRequest>(EMPTY_FORM)
  const [photoUrl, setPhotoUrl] = useState('')
  const [saving, setSaving] = useState(false)
  // Set while editing an existing listing; null means the form creates a new one.
  const [editingId, setEditingId] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      let path = '/api/marketplace/listings'
      if (view === 'mine') path = '/api/marketplace/my-listings'
      else if (view === 'favorites') path = '/api/marketplace/favorites'
      else path += toQueryString({ q: keyword, category, listingType, courseCode, schoolId })

      setListings(await api.get<Listing[]>(path))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load listings')
    } finally {
      setLoading(false)
    }
  }, [view, keyword, category, listingType, courseCode, schoolId])

  useEffect(() => {
    void load()
    // Filters are applied explicitly via the search button, so only the tab
    // itself re-runs the query automatically.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view])

  const closeForm = () => {
    setCreateOpen(false)
    setEditingId(null)
    setForm(EMPTY_FORM)
    setPhotoUrl('')
  }

  const openCreate = () => {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setPhotoUrl('')
    setCreateOpen(true)
  }

  const openEdit = async (listing: Listing) => {
    try {
      // Re-read the listing so the form starts from the stored copy rather than
      // whatever the search results happened to contain.
      const fresh = await api.get<Listing>(`/api/marketplace/listings/${listing.id}`)
      setForm({
        title: fresh.title,
        description: fresh.description ?? '',
        category: fresh.category,
        listingType: fresh.listingType,
        price: fresh.price,
        courseCode: fresh.courseCode ?? '',
        schoolId: fresh.schoolId,
        photoUrls: fresh.photoUrls ?? [],
      })
      setPhotoUrl(fresh.photoUrls?.[0] ?? '')
      setEditingId(fresh.id)
      setCreateOpen(true)
    } catch (e) {
      push(e instanceof Error ? e.message : 'Could not open that listing', 'error')
    }
  }

  const submitListing = async () => {
    if (!form.title.trim()) {
      push('Give your listing a title', 'error')
      return
    }
    setSaving(true)
    try {
      const priceless = form.listingType === 'FREE' || form.listingType === 'LOOKING_FOR'
      const body = {
        ...form,
        price: priceless ? null : form.price,
        courseCode: form.courseCode || null,
        schoolId: form.schoolId ? Number(form.schoolId) : null,
        photoUrls: photoUrl.trim() ? [photoUrl.trim()] : [],
      }

      if (editingId != null) {
        await api.put<Listing>(`/api/marketplace/listings/${editingId}`, body)
        push('Listing updated', 'success')
      } else {
        await api.post<Listing>('/api/marketplace/listings', body)
        push('Listing posted', 'success')
        setView('all')
      }
      closeForm()
      void load()
    } catch (e) {
      push(e instanceof Error ? e.message : 'Could not save listing', 'error')
    } finally {
      setSaving(false)
    }
  }

  const toggleFavorite = async (listing: Listing) => {
    try {
      const path = `/api/marketplace/listings/${listing.id}/favorite`
      if (listing.favorited) await api.del(path)
      else await api.post(path)
      setListings((prev) =>
        prev
          .map((l) => (l.id === listing.id ? { ...l, favorited: !l.favorited } : l))
          .filter((l) => view !== 'favorites' || l.favorited),
      )
    } catch {
      push('Could not update favorites', 'error')
    }
  }

  const markSold = async (listing: Listing) => {
    try {
      await api.post(`/api/marketplace/listings/${listing.id}/sold`)
      push('Marked as sold', 'success')
      void load()
    } catch {
      push('Could not mark as sold', 'error')
    }
  }

  const remove = async (listing: Listing) => {
    if (!confirm(`Delete "${listing.title}"?`)) return
    try {
      await api.del(`/api/marketplace/listings/${listing.id}`)
      push('Listing deleted', 'success')
      void load()
    } catch {
      push('Could not delete listing', 'error')
    }
  }

  const report = async (listing: Listing) => {
    const reason = prompt('Why are you reporting this listing?')
    if (!reason) return
    try {
      await api.post(`/api/marketplace/listings/${listing.id}/report`, { reason })
      push('Reported to moderators — thank you', 'success')
    } catch {
      push('Could not send report', 'error')
    }
  }

  const messageSeller = async (listing: Listing) => {
    try {
      await api.post('/api/messages/conversations', { listingId: listing.id })
      push('Conversation started', 'success')
      navigate('/messages')
    } catch (e) {
      push(e instanceof Error ? e.message : 'Could not start a conversation', 'error')
    }
  }

  return (
    <>
      <PageHeader
        title="Marketplace"
        subtitle="Textbooks, dorm gear, tickets and giveaways from verified students nearby."
        action={
          <button className="btn-primary" onClick={openCreate}>
            <Plus className="h-4 w-4" /> New listing
          </button>
        }
      />

      <div className="card mb-6 p-4">
        <div className="grid gap-3 md:grid-cols-[2fr_1fr_1fr_auto]">
          <div className="relative">
            <Search className="absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-[var(--color-ink-faint)]" />
            <input
              className="field pl-9"
              placeholder="Search listings…"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && load()}
            />
          </div>
          <select className="field" value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">All categories</option>
            {CATEGORIES.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </select>
          <select className="field" value={listingType} onChange={(e) => setListingType(e.target.value)}>
            <option value="">Any type</option>
            {LISTING_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
          <button className="btn-secondary" onClick={() => load()}>
            Search
          </button>
        </div>
        <div className="mt-3 grid gap-3 md:grid-cols-2">
          <div className="relative">
            <BookOpen className="absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-[var(--color-ink-faint)]" />
            <input
              className="field pl-9"
              placeholder="Course code (e.g. CS 2021)"
              value={courseCode}
              onChange={(e) => setCourseCode(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && load()}
            />
          </div>
          <select className="field" value={schoolId} onChange={(e) => setSchoolId(e.target.value)}>
            <option value="">All schools</option>
            {schools.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="mb-5">
        <Tabs
          value={view}
          onChange={setView}
          options={[
            { value: 'all', label: 'Browse' },
            { value: 'mine', label: 'My listings' },
            { value: 'favorites', label: 'Favorites' },
          ]}
        />
      </div>

      {loading ? (
        <Spinner label="Loading listings…" />
      ) : error ? (
        <ErrorState message={error} onRetry={() => void load()} />
      ) : listings.length === 0 ? (
        <EmptyState
          icon={<Store className="h-6 w-6" />}
          title={view === 'favorites' ? 'No favorites yet' : view === 'mine' ? 'You have no listings' : 'Nothing matches those filters'}
          description={
            view === 'all'
              ? 'Try widening your search, or post the first listing for your campus.'
              : 'Browse the marketplace and tap the heart on anything you want to keep an eye on.'
          }
          action={
            <button className="btn-primary" onClick={openCreate}>
              <Plus className="h-4 w-4" /> New listing
            </button>
          }
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {listings.map((listing) => (
            <ListingCard
              key={listing.id}
              listing={listing}
              schoolName={nameOf(listing.schoolId)}
              owned={view === 'mine'}
              onFavorite={() => toggleFavorite(listing)}
              onMessage={() => messageSeller(listing)}
              onMarkSold={() => markSold(listing)}
              onDelete={() => remove(listing)}
              onReport={() => report(listing)}
              onEdit={() => openEdit(listing)}
            />
          ))}
        </div>
      )}

      <Modal
        open={createOpen}
        onClose={closeForm}
        title={editingId != null ? 'Edit listing' : 'New listing'}
        description="Only verified students at Cincinnati-area schools will see this."
        footer={
          <>
            <button className="btn-ghost" onClick={closeForm}>
              Cancel
            </button>
            <button className="btn-primary" onClick={submitListing} disabled={saving}>
              {saving ? 'Saving…' : editingId != null ? 'Save changes' : 'Post listing'}
            </button>
          </>
        }
      >
        <div className="space-y-3">
          <input
            className="field"
            placeholder="Title"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
          />
          <textarea
            className="field min-h-24"
            placeholder="Describe the condition, pickup spot, and anything else worth knowing."
            value={form.description ?? ''}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
          <div className="grid gap-3 sm:grid-cols-2">
            <select
              className="field"
              value={form.category}
              onChange={(e) => setForm({ ...form, category: e.target.value as ListingCategory })}
            >
              {CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
            <select
              className="field"
              value={form.listingType}
              onChange={(e) => setForm({ ...form, listingType: e.target.value as ListingType })}
            >
              {LISTING_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <input
              className="field"
              type="number"
              min="0"
              step="0.01"
              placeholder="Price ($)"
              disabled={form.listingType === 'FREE' || form.listingType === 'LOOKING_FOR'}
              value={form.price ?? ''}
              onChange={(e) => setForm({ ...form, price: e.target.value ? Number(e.target.value) : null })}
            />
            <input
              className="field"
              placeholder="Course code (optional)"
              value={form.courseCode ?? ''}
              onChange={(e) => setForm({ ...form, courseCode: e.target.value })}
            />
          </div>
          <select
            className="field"
            value={form.schoolId ?? ''}
            onChange={(e) => setForm({ ...form, schoolId: e.target.value ? Number(e.target.value) : null })}
          >
            <option value="">No specific school</option>
            {schools.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          <input
            className="field"
            placeholder="Photo URL (optional)"
            value={photoUrl}
            onChange={(e) => setPhotoUrl(e.target.value)}
          />
        </div>
      </Modal>
    </>
  )
}

function ListingCard({
  listing,
  schoolName,
  owned,
  onFavorite,
  onMessage,
  onMarkSold,
  onDelete,
  onReport,
  onEdit,
}: {
  listing: Listing
  schoolName: string | null
  owned: boolean
  onFavorite: () => void
  onMessage: () => void
  onMarkSold: () => void
  onDelete: () => void
  onReport: () => void
  onEdit: () => void
}) {
  const priceLabel =
    listing.price != null
      ? `$${Number(listing.price).toFixed(2)}`
      : listing.listingType === 'FREE'
        ? 'Free'
        : listing.listingType === 'LOOKING_FOR'
          ? 'Wanted'
          : '—'

  const statusClass =
    listing.status === 'AVAILABLE' ? 'badge-success' : listing.status === 'PENDING' ? 'badge-warning' : 'badge-danger'

  return (
    <article className="card card-hover animate-fade-in-up flex flex-col overflow-hidden">
      {listing.photoUrls?.[0] && (
        <img
          src={listing.photoUrls[0]}
          alt=""
          className="h-40 w-full object-cover"
          onError={(e) => {
            e.currentTarget.style.display = 'none'
          }}
        />
      )}
      <div className="flex flex-1 flex-col p-5">
        <div className="flex items-start justify-between gap-3">
          <h3 className="text-base leading-snug font-bold">{listing.title}</h3>
          <span className={statusClass}>{listing.status.toLowerCase()}</span>
        </div>

        <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-[var(--color-ink-muted)]">
          <span className="font-bold text-primary-600 dark:text-primary-400">{priceLabel}</span>
          <span>·</span>
          <span>{listing.category.replace('_', ' ').toLowerCase()}</span>
          {listing.courseCode && (
            <>
              <span>·</span>
              <span>{listing.courseCode}</span>
            </>
          )}
        </div>

        {listing.description && (
          <p className="mt-3 line-clamp-3 text-sm text-[var(--color-ink-muted)]">{listing.description}</p>
        )}

        <p className="mt-3 text-xs text-[var(--color-ink-faint)]">
          {listing.sellerEmail}
          {schoolName && ` · ${schoolName}`}
        </p>

        <div className="mt-4 flex flex-wrap gap-2 border-t border-[var(--color-border)] pt-4">
          {!owned && (
            <button className="btn-secondary btn-sm" onClick={onMessage}>
              <MessageCircle className="h-3.5 w-3.5" /> Message
            </button>
          )}
          <button
            className={`btn-sm ${listing.favorited ? 'btn-secondary' : 'btn-ghost'}`}
            onClick={onFavorite}
            aria-label={listing.favorited ? 'Remove from favorites' : 'Add to favorites'}
          >
            <Heart className={`h-3.5 w-3.5 ${listing.favorited ? 'fill-current' : ''}`} />
            {listing.favorited ? 'Saved' : 'Save'}
          </button>
          {owned ? (
            <>
              <button className="btn-ghost btn-sm" onClick={onEdit}>
                <Pencil className="h-3.5 w-3.5" /> Edit
              </button>
              {listing.status !== 'SOLD' && (
                <button className="btn-ghost btn-sm" onClick={onMarkSold}>
                  <CheckCircle2 className="h-3.5 w-3.5" /> Sold
                </button>
              )}
              <button className="btn-danger btn-sm" onClick={onDelete}>
                <Trash2 className="h-3.5 w-3.5" /> Delete
              </button>
            </>
          ) : (
            <button className="btn-ghost btn-sm" onClick={onReport}>
              <Flag className="h-3.5 w-3.5" /> Report
            </button>
          )}
        </div>
      </div>
    </article>
  )
}
