import { NavLink, Outlet, Link } from 'react-router-dom'
import { SignedIn, SignedOut, UserButton } from '@clerk/clerk-react'
import { GraduationCap, Store, MessageCircle, Users, LifeBuoy, Search } from 'lucide-react'
import type { ComponentType } from 'react'

interface NavItem {
  to: string
  label: string
  icon: ComponentType<{ className?: string }>
}

// The same five destinations appear in the desktop header and the mobile
// bottom bar, so the web app already has the shape the mobile app will take.
const NAV_ITEMS: NavItem[] = [
  { to: '/marketplace', label: 'Marketplace', icon: Store },
  { to: '/community', label: 'Community', icon: Users },
  { to: '/support', label: 'Support', icon: LifeBuoy },
  { to: '/messages', label: 'Messages', icon: MessageCircle },
  { to: '/directory', label: 'Directory', icon: Search },
]

export function AppShell() {
  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-40 border-b border-[var(--color-border)] bg-[var(--color-surface)]/85 backdrop-blur-lg">
        <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
          <Link to="/" className="flex items-center gap-2">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary-600 text-white">
              <GraduationCap className="h-5 w-5" />
            </span>
            <span className="text-base font-extrabold tracking-tight">
              Campus<span className="text-primary-600">Bridge</span>
            </span>
          </Link>

          <nav className="hidden items-center gap-1 md:flex">
            {NAV_ITEMS.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `rounded-lg px-3 py-2 text-sm font-semibold transition-colors ${
                    isActive
                      ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/40 dark:text-primary-200'
                      : 'text-[var(--color-ink-muted)] hover:bg-[var(--color-surface-muted)] hover:text-[var(--color-ink)]'
                  }`
                }
              >
                {label}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-2">
            <SignedIn>
              <Link to="/profile" className="btn-ghost btn-sm hidden sm:inline-flex">
                My Profile
              </Link>
              <UserButton afterSignOutUrl="/" />
            </SignedIn>
            <SignedOut>
              <Link to="/sign-in" className="btn-ghost btn-sm">
                Sign in
              </Link>
              <Link to="/sign-up" className="btn-primary btn-sm">
                Join free
              </Link>
            </SignedOut>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 pt-6 pb-28 sm:px-6 md:pb-12">
        <Outlet />
      </main>

      <SignedIn>
        <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-[var(--color-border)] bg-[var(--color-surface)]/95 pb-[env(safe-area-inset-bottom)] backdrop-blur-lg md:hidden">
          <div className="flex items-stretch justify-around">
            {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `flex flex-1 flex-col items-center gap-1 py-2.5 text-[11px] font-semibold transition-colors ${
                    isActive ? 'text-primary-600' : 'text-[var(--color-ink-faint)]'
                  }`
                }
              >
                <Icon className="h-5 w-5" />
                {label}
              </NavLink>
            ))}
          </div>
        </nav>
      </SignedIn>
    </div>
  )
}
