import { useEffect, useState } from 'react'
import { Flag, ImageOff, Store, Trash2 } from 'lucide-react'
import { api, ApiError } from '../../lib/api'
import { useToast } from '../../components/ui/Toast'
import { Modal } from '../../components/ui/Modal'
import { Spinner } from '../../components/ui/Feedback'
import type { AdminListing, AdminListingReport } from '../../types'
import { formatDate, messageOf, plural, usePagedList } from './adminList'
import { Byline, Check, ConfirmDialog, ListFrame, Row, SearchBar, Strong } from './shared'

const label = (value: string) => value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' ')

function priceOf(listing: AdminListing) {
  if (listing.price != null) return `$${Number(listing.price).toFixed(2)}`
  return listing.listingType === 'FREE' ? 'Free' : null
}

function ListingCard({ listing }: { listing: AdminListing }) {
  return (
    <>
      <p className="text-base font-bold break-words">{listing.title}</p>
      <p className="break-all text-[var(--color-ink-muted)]">Posted by {listing.sellerEmail}</p>
      <p className="mt-0.5 text-xs text-[var(--color-ink-faint)]">
        {[label(listing.category), label(listing.listingType), priceOf(listing), formatDate(listing.createdAt)].filter(Boolean).join(' · ')}
      </p>
    </>
  )
}

/**
 * Every listing, in every status, with how often each has been reported. "Reported only"
 * is the moderation queue: each reported listing can be reviewed, cleared or taken down
 * from this one view.
 */
export default function MarketplacePanel() {
  const { push } = useToast()
  const [query, setQuery] = useState('')
  const [reportedOnly, setReportedOnly] = useState(false)
  const listings = usePagedList<AdminListing>('/api/admin/listings', { query, reportedOnly })
  const [reviewing, setReviewing] = useState<AdminListing | null>(null)
  const [deleting, setDeleting] = useState<AdminListing | null>(null)

  const alreadyGone = () => {
    setReviewing(null)
    setDeleting(null)
    push('That listing no longer exists. The list has been refreshed.', 'error')
    listings.reload()
  }

  return (
    <div>
      <SearchBar label="Search listings by title, description or seller email" placeholder="Search by title, description or seller email"
        applied={query} onSearch={(next) => { listings.setPage(0); setQuery(next) }}>
        <div className="shrink-0 text-sm font-semibold">
          <Check checked={reportedOnly} onChange={(next) => { listings.setPage(0); setReportedOnly(next) }}>Reported only</Check>
        </div>
      </SearchBar>

      <ListFrame list={listings} noun="listing" icon={<Store className="h-6 w-6" />}
        emptyTitle={reportedOnly ? 'No reported listings' : query ? 'No listings matched' : 'No listings yet'}
        emptyDescription={reportedOnly ? 'Nothing is waiting for review.' : query ? `Nothing matches “${query}”.` : 'Listings appear here as students post them.'}>
        {(listing) => (
          <Row key={listing.id}
            lead={listing.photoUrl
              ? <img src={listing.photoUrl} alt="" className="h-14 w-14 shrink-0 rounded-xl object-cover" />
              : <span aria-hidden="true" className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-[var(--color-surface-muted)] text-[var(--color-ink-faint)]"><ImageOff className="h-5 w-5" /></span>}
            actions={
              <>
                {listing.reportCount > 0 && (
                  <button className="btn-secondary btn-sm" onClick={() => setReviewing(listing)} aria-label={`Review reports on ${listing.title}`}>
                    <Flag className="h-3.5 w-3.5" /> Review {plural(listing.reportCount, 'report')}
                  </button>
                )}
                <button className="btn-danger btn-sm" onClick={() => setDeleting(listing)} aria-label={`Delete ${listing.title}`}>
                  <Trash2 className="h-3.5 w-3.5" /> Delete
                </button>
              </>
            }>
            <p className="flex flex-wrap items-center gap-2">
              <span className="font-bold break-words">{listing.title}</span>
              <span className={listing.status === 'AVAILABLE' ? 'badge-success' : 'badge-neutral'}>{label(listing.status)}</span>
              {listing.reportCount > 0 && <span className="badge-danger">{plural(listing.reportCount, 'report')}</span>}
            </p>
            <p className="text-sm text-[var(--color-ink-muted)]">
              {[label(listing.category), label(listing.listingType), priceOf(listing), listing.schoolName].filter(Boolean).join(' · ')}
            </p>
            {listing.description && <p className="mt-1 line-clamp-2 text-sm break-words">{listing.description}</p>}
            <Byline email={listing.sellerEmail} date={listing.createdAt} />
          </Row>
        )}
      </ListFrame>

      {reviewing && (
        <ReportsDialog key={reviewing.id} listing={reviewing}
          onClose={() => setReviewing(null)}
          onMissing={alreadyGone}
          onDelete={() => { setDeleting(reviewing); setReviewing(null) }}
          onDismissed={() => {
            setReviewing(null)
            push(`Dismissed the reports on “${reviewing.title}”. The listing was kept.`, 'success')
            listings.reload()
          }} />
      )}

      {deleting && (
        <ConfirmDialog key={deleting.id}
          title="Delete this listing?"
          target={<ListingCard listing={deleting} />}
          consequences={[
            'The listing disappears from the Marketplace for everyone, along with its photos and any reports on it.',
            'Conversations that started from it stay in Messages. The seller is not notified.',
            <Strong key="undone">This cannot be undone.</Strong>,
          ]}
          confirmLabel="Delete listing" busyLabel="Deleting…" failurePrefix="The listing was not deleted."
          onClose={() => setDeleting(null)}
          onMissing={alreadyGone}
          onConfirm={async () => {
            await api.del(`/api/admin/listings/${deleting.id}`)
            setDeleting(null)
            push(`Deleted “${deleting.title}”.`, 'success')
            listings.reload()
          }} />
      )}
    </div>
  )
}

/** Why a listing was reported, with the two ways to settle it: clear the reports, or take it down. */
function ReportsDialog({ listing, onClose, onDismissed, onDelete, onMissing }: {
  listing: AdminListing
  onClose: () => void
  onDismissed: () => void
  onDelete: () => void
  onMissing: () => void
}) {
  const [reports, setReports] = useState<AdminListingReport[] | null>(null)
  const [failure, setFailure] = useState<string | null>(null)
  const [working, setWorking] = useState(false)

  useEffect(() => {
    let cancelled = false
    api.get<AdminListingReport[]>(`/api/admin/listings/${listing.id}/reports`)
      .then((found) => { if (!cancelled) setReports(found) })
      .catch((e: unknown) => {
        if (cancelled) return
        if (e instanceof ApiError && e.status === 404) onMissing()
        else setFailure(messageOf(e, 'The reports could not be loaded.'))
      })
    return () => { cancelled = true }
    // Loaded once per listing; the dialog is keyed by it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [listing.id])

  const dismiss = async () => {
    setWorking(true)
    setFailure(null)
    try {
      await api.del(`/api/admin/listings/${listing.id}/reports`)
      onDismissed()
    } catch (e) {
      setWorking(false)
      if (e instanceof ApiError && e.status === 404) onMissing()
      else setFailure(`The reports were not dismissed. ${messageOf(e, 'Try again.')}`)
    }
  }

  return (
    <Modal open onClose={() => { if (!working) onClose() }} title="Reports on this listing"
      footer={
        <>
          <button type="button" className="btn-ghost" onClick={onClose} disabled={working}>Close</button>
          <button type="button" className="btn-secondary" onClick={() => void dismiss()} disabled={working || !reports}>
            {working ? 'Dismissing…' : 'Keep listing, dismiss reports'}
          </button>
          <button type="button" className="btn-danger" onClick={onDelete} disabled={working}>
            <Trash2 className="h-4 w-4" /> Delete listing
          </button>
        </>
      }>
      <div className="space-y-4 text-sm">
        <div className="rounded-xl border border-[var(--color-border-strong)] bg-[var(--color-surface-muted)] px-4 py-3">
          <ListingCard listing={listing} />
          {listing.description && <p className="mt-2 break-words whitespace-pre-wrap">{listing.description}</p>}
        </div>
        {failure && <p role="alert" className="rounded-xl bg-[var(--color-danger-bg)] px-3 py-2 text-[var(--color-danger)]">{failure}</p>}
        {!reports && !failure && <div role="status"><Spinner label="Loading reports…" /></div>}
        {reports && reports.length === 0 && <p className="text-[var(--color-ink-muted)]">These reports were already dismissed.</p>}
        {reports && reports.length > 0 && (
          <ul className="space-y-3">
            {reports.map((report) => (
              <li key={report.id} className="rounded-xl border border-[var(--color-border)] px-4 py-3">
                <p className="break-words whitespace-pre-wrap">{report.reason}</p>
                <Byline email={`Reported by ${report.reporterEmail}`} date={report.createdAt} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  )
}
