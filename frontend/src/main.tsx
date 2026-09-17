import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { ClerkProvider } from '@clerk/clerk-react'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { ToastProvider } from './components/ui/Toast'
import { clerkAppearance } from './lib/clerkAppearance'
import './index.css'

// Publishable keys are meant to ship to the browser; the Clerk secret key
// stays server-side. This is the same Clerk instance the Spring Boot API
// already validates session tokens against.
const publishableKey = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY

if (!publishableKey) {
  throw new Error('Missing VITE_CLERK_PUBLISHABLE_KEY — copy frontend/.env.example to frontend/.env')
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* taskUrls tells Clerk where to send a session that is `pending` on a task.
        Clerk's own <SignIn /> would resolve setup-mfa itself, but sign-in here is a
        custom useSignIn flow, so without this the user is stranded. */}
    <ClerkProvider
      publishableKey={publishableKey}
      afterSignOutUrl="/"
      appearance={clerkAppearance}
      taskUrls={{ 'setup-mfa': '/session-tasks/setup-mfa' }}
    >
      <BrowserRouter>
        <ToastProvider>
          <App />
        </ToastProvider>
      </BrowserRouter>
    </ClerkProvider>
  </StrictMode>,
)
