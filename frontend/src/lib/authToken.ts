// A tiny mutable bridge so the plain `api.ts` module (outside of React) can
// attach the current Clerk session token to requests, without every page
// having to pass `getToken` down through props. `AuthTokenBridge` (in
// components/AuthTokenBridge.tsx) is the only thing that writes to this.
type GetToken = () => Promise<string | null>

let currentGetToken: GetToken | null = null

export function setTokenGetter(getToken: GetToken | null) {
  currentGetToken = getToken
}

export async function getAuthToken(): Promise<string | null> {
  if (!currentGetToken) return null
  return currentGetToken()
}
