import { useState } from 'react'
import { TaskSetupMFA, useSession } from '@clerk/clerk-react'
import { Navigate } from 'react-router-dom'
import { SignUpSteps } from '../components/ui/SignUpSteps'
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
  const { session } = useSession()
  // With no task Clerk renders nothing, so a visit that never had one (Back after finishing,
  // an old bookmark) would be an empty page: send it on. A task finished during this visit
  // is left alone, because Clerk is still showing its last screen and does its own redirect.
  const [hadTask, setHadTask] = useState(false)
  if (session?.currentTask && !hadTask) setHadTask(true)
  if (session?.status === 'active' && !session.currentTask && !hadTask) return <Navigate to="/marketplace" replace />

  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-6 px-4 py-12">
      <Wordmark />
      <SignUpSteps current={3} />
      {/* Clerk's card below says how; this says why, which it does not. */}
      <div className="max-w-md text-center">
        <h1 className="text-xl font-extrabold tracking-tight">Protect your account</h1>
        <p className="mt-1 text-sm text-[var(--color-ink-muted)]">
          CampusBridge asks every student for two-step verification. Set it up once, with an authenticator
          app or a text message code, and you are in.
        </p>
      </div>
      {/* MFA enrolment is part of sign-up (required_for_sign_up), so continue to its last
          step, the profile form; a student who already has a profile is sent on into the app. */}
      <TaskSetupMFA redirectUrlComplete="/sign-up/profile" />
    </div>
  )
}
