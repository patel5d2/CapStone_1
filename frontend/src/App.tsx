import { useEffect, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from '@clerk/clerk-react'
import { AppShell } from './components/layout/AppShell'
import { setTokenGetter } from './lib/authToken'
import { Spinner } from './components/ui/Feedback'
import Landing from './pages/Landing'
import SignInPage from './pages/SignInPage'
import SignUpPage from './pages/SignUpPage'
import Marketplace from './pages/Marketplace'
import Messages from './pages/Messages'
import Community from './pages/Community'
import Support from './pages/Support'
import Profile from './pages/Profile'

/** Keeps `lib/api.ts` supplied with a fresh Clerk session token. */
function AuthTokenBridge() {
  const { getToken } = useAuth()
  useEffect(() => {
    setTokenGetter(() => getToken())
    return () => setTokenGetter(null)
  }, [getToken])
  return null
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { isLoaded, isSignedIn } = useAuth()
  if (!isLoaded) return <Spinner />
  if (!isSignedIn) return <Navigate to="/sign-in" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <>
      <AuthTokenBridge />
      <Routes>
        <Route path="/sign-in/*" element={<SignInPage />} />
        <Route path="/sign-up/*" element={<SignUpPage />} />
        <Route element={<AppShell />}>
          <Route path="/" element={<Landing />} />
          <Route
            path="/marketplace"
            element={
              <RequireAuth>
                <Marketplace />
              </RequireAuth>
            }
          />
          <Route
            path="/messages"
            element={
              <RequireAuth>
                <Messages />
              </RequireAuth>
            }
          />
          <Route
            path="/community"
            element={
              <RequireAuth>
                <Community />
              </RequireAuth>
            }
          />
          <Route
            path="/support"
            element={
              <RequireAuth>
                <Support />
              </RequireAuth>
            }
          />
          {/* The directory moved inside Community; keep old links working. */}
          <Route path="/directory" element={<Navigate to="/community?tab=directory" replace />} />
          <Route
            path="/profile"
            element={
              <RequireAuth>
                <Profile />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </>
  )
}
