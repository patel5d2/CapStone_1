import type { Appearance } from '@clerk/shared/types'

/**
 * Themes every prebuilt Clerk component — UserButton, UserProfile, TaskSetupMFA — to
 * match CampusBridge.
 *
 * Without it Clerk renders in its own violet, which is both off-brand and the exact
 * indigo-violet the palette was chosen to get away from. Applied once on ClerkProvider
 * rather than per component, so a Clerk component added later inherits it.
 *
 * Most values are `var(...)` references so dark mode follows the same tokens the rest of
 * the app does, with no second palette to keep in step. `colorPrimary` is the exception
 * — see below.
 *
 * NOT YET VERIFIED IN A BROWSER. Exercising it needs a signed-in session with a pending
 * task, and with no session `<TaskSetupMFA />` redirects to Clerk's hosted Account
 * Portal, which this object cannot reach: the portal is themed in the Clerk dashboard,
 * not here. Check this the first time a real MFA enrolment renders in-app.
 */
export const clerkAppearance: Appearance = {
  variables: {
    // Literal hex, not var(): Clerk derives hover/active shades from this value, which
    // means it has to parse it. The pass-through variables below take var() happily.
    // Keep in step with --color-primary-600.
    colorPrimary: '#2c6a4d',
    colorText: 'var(--color-ink)',
    colorTextSecondary: 'var(--color-ink-muted)',
    colorBackground: 'var(--color-surface)',
    colorInputBackground: 'var(--color-surface)',
    colorInputText: 'var(--color-ink)',
    colorDanger: 'var(--color-danger)',
    colorSuccess: 'var(--color-success)',
    colorWarning: 'var(--color-warning)',
    colorNeutral: 'var(--color-ink)',
    fontFamily: 'var(--font-sans)',
    // Matches radius-xl on the app's own buttons and fields.
    borderRadius: '0.75rem',
  },
  elements: {
    // Clerk's card would otherwise sit on its own white with its own shadow, which
    // reads as a second application pasted into this one.
    card: {
      backgroundColor: 'var(--color-surface)',
      border: '1px solid var(--color-border)',
      boxShadow: 'var(--shadow-card)',
    },
    formFieldInput: {
      // The 3:1 control boundary, same as .field.
      borderColor: 'var(--color-border-strong)',
    },
  },
}
