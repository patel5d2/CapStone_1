import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { ToastProvider } from './components/ui/Toast'
import { restoreSchoolTheme } from './lib/schoolTheme'
import { ThemedClerkProvider } from './components/ThemedClerkProvider'
import './index.css'

// Publishable keys are meant to ship to the browser; the Clerk secret key
// stays server-side. This is the same Clerk instance the Spring Boot API
// already validates session tokens against.
const publishableKey = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY

if (!publishableKey) {
  throw new Error('Missing VITE_CLERK_PUBLISHABLE_KEY — copy frontend/.env.example to frontend/.env')
}

// Before first paint, so a returning student does not see the default green flash first.
restoreSchoolTheme()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemedClerkProvider publishableKey={publishableKey}>
      <BrowserRouter>
        <ToastProvider>
          <App />
        </ToastProvider>
      </BrowserRouter>
    </ThemedClerkProvider>
  </StrictMode>,
)
