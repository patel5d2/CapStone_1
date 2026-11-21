import { NavLink, Outlet, Link, useNavigate } from 'react-router-dom'
import { SignedIn, SignedOut, UserButton, useAuth } from '@clerk/clerk-react'
import { Store, MessageCircle, Users, LifeBuoy, UserRound, ShieldCheck } from 'lucide-react'
import type { ComponentType } from 'react'
import { Wordmark } from '../ui/Wordmark'
import { useAdminAccess } from '../../hooks/useAdminAccess'

interface NavItem {
  to: string
  label: string
  icon: ComponentType<{ className?: string }>
}

// One list drives the sidebar, the rail and the bottom bar, so the destinations and
// their order cannot drift apart between breakpoints — only the container changes.
// The student directory is a surface inside Community, never a peer of it.
const NAV_ITEMS: NavItem[] = [
  { to: '/marketplace', label: 'Marketplace', icon: Store },
  { to: '/messages', label: 'Messages', icon: MessageCircle },
  { to: '/community', label: 'Community', icon: Users },
  { to: '/support', label: 'Support', icon: LifeBuoy },
]

// Account management (#113), offered only to administrators. Showing or hiding the link
// is a convenience: the page and every request behind it are checked again.
const ADMIN_ITEM: NavItem = { to: '/admin', label: 'Admin', icon: ShieldCheck }

/**
 * The destinations for whoever is signed in. A student sees the four tabs. An
 * administrator who is also a student sees them plus Admin. A dedicated administrator
 * account has no student profile, so the student tabs (and "My profile") would only
 * bounce it back to Admin, and are left out.
 */
function useNavigation() {
  const { isSignedIn, userId } = useAuth()
  const access = useAdminAccess(isSignedIn ? userId : undefined)
  const isAdmin = access.status === 'ready' && access.admin
  const adminOnly = isAdmin && !access.hasStudentProfile
  return {
    items: adminOnly ? [ADMIN_ITEM] : isAdmin ? [...NAV_ITEMS, ADMIN_ITEM] : NAV_ITEMS,
    showProfile: !adminOnly,
  }
}

// Style objects, not Tailwind classes: Clerk's own styles outrank Tailwind's layered
// utilities, so classes here are ignored. Matches the nav rows above: full width, avatar
// first, name from 1024px (the rail shows the avatar alone).
const LG = '@media (min-width: 1024px)'
const SIDEBAR_ACCOUNT_ELEMENTS = {
  rootBox: { width: '100%' },
  userButtonTrigger: {
    width: '100%',
    justifyContent: 'center',
    padding: '0.5rem 0',
    borderRadius: '0.75rem',
    '&:hover': { backgroundColor: 'var(--color-surface-muted)' },
    [LG]: { justifyContent: 'flex-start', padding: '0.5rem 0.75rem' },
  },
  // Clerk renders the name before the avatar; reversed, the avatar leads like the nav icons.
  userButtonBox: { flexDirection: 'row-reverse', gap: '0.75rem', minWidth: 0 },
  userButtonOuterIdentifier: {
    display: 'none',
    padding: 0,
    fontSize: '0.875rem',
    fontWeight: 600,
    color: 'var(--color-ink-muted)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    [LG]: { display: 'block' },
  },
} as const

/**
 * The one account control: Clerk's avatar button, whose menu opens on the CampusBridge
 * profile first, then Clerk's own account page and sign-out. The sidebar shows the
 * person's name beside the avatar at ≥1024px; the rail and the phone header show the
 * avatar alone. A dedicated administrator account has no student profile, so its menu
 * keeps only Clerk's items.
 */
function AccountButton({ showProfile, inSidebar = false }: { showProfile: boolean; inSidebar?: boolean }) {
  const navigate = useNavigate()
  return (
    <UserButton
      afterSignOutUrl="/"
      showName={inSidebar}
      appearance={inSidebar ? { elements: SIDEBAR_ACCOUNT_ELEMENTS } : undefined}
    >
      {showProfile && (
        <UserButton.MenuItems>
          <UserButton.Action
            label="My profile"
            labelIcon={<UserRound className="h-4 w-4" />}
            onClick={() => navigate('/profile')}
          />
          <UserButton.Action label="manageAccount" />
          <UserButton.Action label="signOut" />
        </UserButton.MenuItems>
      )}
    </UserButton>
  )
}

/**
 * Sidebar at ≥1024px, icon-only rail between 640 and 1023px. One element rather than
 * two: the label collapses, the destinations do not change.
 *
 * The active item carries a tinted fill AND a left indicator bar — never colour alone,
 * so it survives a colour-vision difference and a school theme that tints the fill
 * closer to the surface.
 */
function SideNav({ items, showProfile }: { items: NavItem[]; showProfile: boolean }) {
  return (
    <aside className="fixed inset-y-0 left-0 z-40 hidden w-18 flex-col border-r border-[var(--color-border)] bg-[var(--color-surface)] sm:flex lg:w-60">
      <div className="flex h-16 items-center justify-center px-3 lg:justify-start lg:px-5">
        <span className="lg:hidden">
          <Wordmark compact />
        </span>
        <span className="hidden lg:block">
          <Wordmark />
        </span>
      </div>

      <nav className="flex flex-1 flex-col gap-1 px-2 lg:px-3" aria-label="Primary">
        {items.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            title={label}
            className={({ isActive }) =>
              `group relative flex items-center gap-3 rounded-xl py-2.5 text-sm font-semibold transition-colors
               justify-center lg:justify-start px-0 lg:px-3 ${
                 isActive
                   ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/40 dark:text-primary-200'
                   : 'text-[var(--color-ink-muted)] hover:bg-[var(--color-surface-muted)] hover:text-[var(--color-ink)]'
               }`
            }
          >
            {({ isActive }) => (
              <>
                {/* The indicator bar. Colour is never the only signal. */}
                <span
                  aria-hidden="true"
                  className={`absolute left-0 h-6 w-1 rounded-r-full transition-opacity ${
                    isActive ? 'bg-primary-600 opacity-100 dark:bg-primary-400' : 'opacity-0'
                  }`}
                />
                <Icon className="h-5 w-5 shrink-0" />
                <span className="hidden lg:inline">{label}</span>
              </>
            )}
          </NavLink>
        ))}
      </nav>

      {/* Pinned to the bottom, per the target shell. Notifications land here too once
          the notification centre exists. */}
      <div className="border-t border-[var(--color-border)] p-2 lg:p-3">
        <AccountButton showProfile={showProfile} inSidebar />
      </div>
    </aside>
  )
}

/** Thumb-reachable bottom bar below 640px, with the same destinations as the sidebar. */
function BottomBar({ items }: { items: NavItem[] }) {
  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-40 border-t border-[var(--color-border)] bg-[var(--color-surface)]/95 pb-[env(safe-area-inset-bottom)] backdrop-blur-lg sm:hidden"
      aria-label="Primary"
    >
      <div className="flex items-stretch justify-around">
        {items.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `relative flex min-h-13 flex-1 flex-col items-center justify-center gap-1 py-2 text-[11px] font-semibold transition-colors ${
                isActive ? 'text-primary-600 dark:text-primary-400' : 'text-[var(--color-ink-faint)]'
              }`
            }
          >
            {({ isActive }) => (
              <>
                <span
                  aria-hidden="true"
                  className={`absolute top-0 h-0.5 w-10 rounded-b-full transition-opacity ${
                    isActive ? 'bg-primary-600 opacity-100 dark:bg-primary-400' : 'opacity-0'
                  }`}
                />
                <Icon className="h-5 w-5" />
                {label}
              </>
            )}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}

export function AppShell() {
  // The offset has to track whether the sidebar is actually rendered. <SignedIn> can
  // only gate children, not a class on this wrapper, so a signed-out visitor was
  // getting a 240px indent against empty space.
  const { isSignedIn } = useAuth()
  const { items, showProfile } = useNavigation()

  return (
    <div className="min-h-full">
      <SignedIn>
        <SideNav items={items} showProfile={showProfile} />
        <BottomBar items={items} />
      </SignedIn>

      {/* The main column sits beside the rail/sidebar rather than under it. */}
      <div className={isSignedIn ? 'sm:pl-18 lg:pl-60' : ''}>
        <SignedIn>
          {/* Mobile only: the sidebar carries the wordmark and account controls at
              every larger width, so repeating them here would be noise. */}
          <header className="sticky top-0 z-30 flex h-16 items-center justify-between gap-4 border-b border-[var(--color-border)] bg-[var(--color-surface)]/85 px-4 backdrop-blur-lg sm:hidden">
            <Wordmark />
            {/* Below 640px the sidebar is hidden; the same account menu lives here. */}
            <AccountButton showProfile={showProfile} />
          </header>
        </SignedIn>

        <SignedOut>
          <header className="sticky top-0 z-30 border-b border-[var(--color-border)] bg-[var(--color-surface)]/85 backdrop-blur-lg">
            <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
              <Wordmark />
              <div className="flex items-center gap-2">
                <Link to="/sign-in" className="btn-ghost btn-sm">
                  Sign in
                </Link>
                <Link to="/sign-up" className="btn-primary btn-sm">
                  Join free
                </Link>
              </div>
            </div>
          </header>
        </SignedOut>

        <main className="mx-auto w-full max-w-6xl px-4 pt-6 pb-28 sm:px-6 sm:pb-12 lg:px-8 lg:pt-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
