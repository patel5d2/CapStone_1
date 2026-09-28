import { TaskSetupMFA } from '@clerk/clerk-react'
import { Wordmark } from '../components/ui/Wordmark'

/**
 * Hosts Clerk's `setup-mfa` session task.
 *
 * Clerk's own `<SignIn />` resolves this task for you. This app does not use it — sign-in
 * is a custom `useSignIn` flow — so without a route registered in `taskUrls` there is
 * nowhere for Clerk to send a user whose session is `pending` on MFA enrolment, and they
 * would sit signed-in-but-unusable.
 *
 * It only renders when there IS a pending task; on a session without one it renders
 * nothing. That is why the "add two-step verification" screen in `App.tsx` opens Clerk's
 * account UI instead of linking here — the two cover different situations:
 *
 *   - this page: Clerk's instance REQUIRES MFA (`required_for_sign_in`), so Clerk itself
 *     blocks the session and hands the user over as a task;
 *   - that screen: the instance does not require it, but CampusBridge does
 *     (`VITE_REQUIRE_TWO_FACTOR`), so there is no Clerk task to resolve.
 */
export default function SetupMfaPage() {
  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-6 px-4 py-12">
      <Wordmark />
      <TaskSetupMFA redirectUrlComplete="/marketplace" />
    </div>
  )
}
