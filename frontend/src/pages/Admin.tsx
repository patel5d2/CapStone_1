import { useState } from 'react'
import { ShieldCheck } from 'lucide-react'
import { PageHeader } from '../components/ui/Tabs'
import AccountsPanel from './admin/AccountsPanel'
import MarketplacePanel from './admin/MarketplacePanel'
import CommunityPanel from './admin/CommunityPanel'
import SupportPanel from './admin/SupportPanel'

type Area = 'accounts' | 'marketplace' | 'community' | 'support'

const AREAS: { value: Area; label: string }[] = [
  { value: 'accounts', label: 'Accounts' },
  { value: 'marketplace', label: 'Marketplace' },
  { value: 'community', label: 'Community' },
  { value: 'support', label: 'Support' },
]

/**
 * The administrator console (#113): student accounts, and everything students post in
 * Marketplace, Community and Support, each with the means to remove what should not be
 * there.
 *
 * The route is wrapped in `RequireAuth adminArea`, which keeps everyone else out of the
 * page. That is for their benefit, not for security: every request these tabs make is
 * checked again by the server (`AdminAccess`), so a student who forced the page open would
 * see an error where the lists should be.
 */
export default function Admin() {
  const [area, setArea] = useState<Area>('accounts')

  return (
    <div>
      <PageHeader
        title="Administration"
        subtitle="Manage student accounts and moderate what students post."
        action={<span className="badge-warning"><ShieldCheck className="h-3.5 w-3.5" /> Administrator</span>}
      />

      <p className="mb-6 flex items-start gap-3 rounded-2xl border border-[var(--color-border-strong)] bg-[var(--color-warning-bg)] px-4 py-3 text-sm text-[var(--color-ink)]">
        <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-[var(--color-warning)]" aria-hidden="true" />
        <span>
          <strong>Administrator area.</strong> Changes here apply to other people&rsquo;s accounts and posts, take
          effect immediately, and are recorded in the server log under your account.
        </span>
      </p>

      {/* Four areas do not fit one row on a phone, so below 640px they sit two by two;
          from there up they are the same pill row the rest of the app uses. */}
      <nav aria-label="Administration areas"
        className="mb-6 grid grid-cols-2 gap-1 rounded-3xl border border-[var(--color-border)] bg-[var(--color-surface)] p-1 sm:inline-flex sm:rounded-full">
        {AREAS.map((option) => (
          <button key={option.value} onClick={() => setArea(option.value)} aria-current={option.value === area ? 'page' : undefined}
            className={`rounded-full px-4 py-1.5 text-sm font-semibold transition-all ${
              option.value === area ? 'bg-primary-600 text-white shadow-sm' : 'text-[var(--color-ink-muted)] hover:text-[var(--color-ink)]'
            }`}>
            {option.label}
          </button>
        ))}
      </nav>

      {/* Only the open tab is mounted, so only its list is fetched. */}
      {area === 'accounts' && <AccountsPanel />}
      {area === 'marketplace' && <MarketplacePanel />}
      {area === 'community' && <CommunityPanel />}
      {area === 'support' && <SupportPanel />}
    </div>
  )
}
