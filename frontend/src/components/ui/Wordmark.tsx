import { Link } from 'react-router-dom'
import { GraduationCap } from 'lucide-react'

/**
 * The one wordmark: a primary-600 tile with a white graduation cap, "Campus" in ink and
 * "Bridge" in the primary colour.
 *
 * It lives here because it was previously copy-pasted into AppShell, SignInPage and
 * SignUpPage, and the three copies had already drifted — each carried its own
 * `text-primary-600` with no dark-mode variant, so fixing the contrast meant finding all
 * three. One component, one fix.
 *
 * `dark:text-primary-400` is not optional: primary-600 is 2.59:1 on the dark surface,
 * well under 4.5:1. Green text and icons use primary-400 in dark mode, everywhere.
 */
export function Wordmark({ compact }: { compact?: boolean }) {
  return (
    <Link to="/" className="flex items-center gap-2.5">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary-600 text-white">
        <GraduationCap className="h-5 w-5" />
      </span>
      {!compact && (
        <span className="text-base font-extrabold tracking-tight whitespace-nowrap">
          Campus<span className="text-primary-600 dark:text-primary-400">Bridge</span>
        </span>
      )}
    </Link>
  )
}
