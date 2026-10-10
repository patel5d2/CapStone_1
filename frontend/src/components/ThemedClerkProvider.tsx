import { useMemo, useSyncExternalStore, type ReactNode } from 'react'
import { ClerkProvider } from '@clerk/clerk-react'
import { clerkAppearanceWith } from '../lib/clerkAppearance'
import { activePrimaryHex, currentSchool, subscribeSchoolTheme } from '../lib/schoolTheme'

/**
 * Clerk's own screens follow the school palette too. Clerk needs a literal hex for its
 * primary, so it is read from the active --color-primary-600 whenever the school changes.
 */
export function ThemedClerkProvider({ publishableKey, children }: { publishableKey: string; children: ReactNode }) {
  const school = useSyncExternalStore(subscribeSchoolTheme, currentSchool)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const appearance = useMemo(() => clerkAppearanceWith(activePrimaryHex()), [school])
  return (
    // taskUrls tells Clerk where to send a session that is `pending` on a task.
    // Clerk's own <SignIn /> would resolve setup-mfa itself, but sign-in here is a
    // custom useSignIn flow, so without this the user is stranded.
    <ClerkProvider
      publishableKey={publishableKey}
      afterSignOutUrl="/"
      appearance={appearance}
      taskUrls={{ 'setup-mfa': '/session-tasks/setup-mfa' }}
    >
      {children}
    </ClerkProvider>
  )
}
