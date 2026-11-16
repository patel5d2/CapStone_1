/*
 * Makes sure the Clerk test account the signed-in suite uses exists and can sign in, before
 * any test runs (Playwright `globalSetup`, once per run, not once per worker).
 *
 * Why this exists: the suite used to assume the account was already in Clerk. When it was
 * deleted from the dev instance, every signed-in test failed at sign-in with "No Clerk user
 * with email ...", which said nothing about the app. Now a missing account is recreated, and
 * an account without a second factor gets one, so the suite repairs its own fixture.
 *
 * What it will and will not do:
 *  - Only ever touches the one address in E2E_CLERK_EMAIL.
 *  - Only creates an account for a Clerk TEST address (one containing "+clerk_test"), which
 *    Clerk never emails. A real person's address is looked up, never created.
 *  - Refuses a production secret key (sk_live_): test accounts belong in the dev instance.
 *  - Enrols a Clerk TEST phone number (+1 555-555-01xx) as the second factor. Those accept
 *    the fixed code 424242 and never send an SMS. The account gets no password: the suite
 *    signs in with a one-time ticket, so there is nothing to store or leak.
 *
 * It also checks the two settings agree before anything else. In CI they are repository
 * secrets nobody can read back, and both mistakes it catches have happened: a secret key
 * from a different Clerk instance than the app's publishable key (the key works, so the
 * account is simply "not found"), and a value pasted with a stray space or newline.
 *
 * The account's Clerk user id is handed to the workers in E2E_CLERK_USER_ID, so the tests
 * do not each look it up again.
 */

const API = 'https://api.clerk.com/v1'
/** Clerk's reserved test number used for this account; see the header of signed-in.spec.ts. */
export const TEST_PHONE = '+15555550142'

interface ClerkPhone {
  id: string
  phone_number: string
  reserved_for_second_factor: boolean
  verification: { status: string } | null
}
interface ClerkUser {
  id: string
  phone_numbers: ClerkPhone[]
}

class ClerkApiError extends Error {
  readonly status: number

  constructor(status: number, path: string, body: string) {
    super(`Clerk ${path} answered ${status}: ${body}`)
    this.status = status
  }
}

async function clerkApi<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${process.env.CLERK_SECRET_KEY}`, 'Content-Type': 'application/json' },
  })
  if (!response.ok) throw new ClerkApiError(response.status, path, await response.text())
  return (await response.json()) as T
}

async function findUser(email: string): Promise<ClerkUser | undefined> {
  const users = await clerkApi<ClerkUser[]>(`/users?email_address=${encodeURIComponent(email)}`)
  return users[0]
}

async function createUser(email: string): Promise<ClerkUser> {
  // A name, because the app pre-fills the profile from it and the profile test does not type one.
  return clerkApi<ClerkUser>('/users', {
    method: 'POST',
    body: JSON.stringify({
      email_address: [email],
      first_name: 'E2E',
      last_name: 'Student',
      skip_password_requirement: true,
    }),
  })
}

/** The second factor the instance requires, answered in the suite with Clerk's test code. */
async function ensureSecondFactor(user: ClerkUser) {
  const enrolled = user.phone_numbers.some((phone) =>
    phone.reserved_for_second_factor && phone.verification?.status === 'verified')
  if (enrolled) return
  try {
    await clerkApi('/phone_numbers', {
      method: 'POST',
      body: JSON.stringify({ user_id: user.id, phone_number: TEST_PHONE, verified: true, reserved_for_second_factor: true }),
    })
  } catch (error) {
    if (error instanceof ClerkApiError && error.status === 422) {
      throw new Error(`The e2e account has no second factor, and ${TEST_PHONE} could not be added to it `
        + '(it may belong to another account in this Clerk instance). Remove it there, or enrol any '
        + `+1 555-555-01xx test number on the account by hand. Clerk said: ${error.message}`, { cause: error })
    }
    throw error
  }
}

/** The Clerk Frontend API host a publishable key names: pk_test_<base64 of "host$">. */
export function hostOf(publishableKey: string): string | null {
  const encoded = publishableKey.split('_')[2]
  if (!encoded) return null
  const decoded = Buffer.from(encoded, 'base64').toString('utf8')
  return decoded.endsWith('$') ? decoded.slice(0, -1) : null
}

/** Refuses a secret key from a different Clerk instance than the app signs in to. */
async function requireSameInstance(publishableKey: string | undefined) {
  const expected = publishableKey ? hostOf(publishableKey.trim()) : null
  if (!expected) return // Nothing to compare with; the sign-in itself will say if they differ.
  const { data } = await clerkApi<{ data: { frontend_api_url?: string }[] }>('/domains')
  const hosts = data.map((domain) => (domain.frontend_api_url ?? '').replace(/^https?:\/\//, ''))
  if (!hosts.includes(expected)) {
    throw new Error(`CLERK_SECRET_KEY belongs to a different Clerk instance (${hosts.join(', ') || 'unknown'}) `
      + `than the app's publishable key (${expected}). Set CLERK_SECRET_KEY to the secret key of ${expected}, `
      + 'from that instance\'s API keys page in the Clerk dashboard.')
  }
}

export default async function ensureE2eAccount() {
  // Trimmed, and written back: a secret pasted with a trailing newline otherwise matches nothing.
  const email = process.env.E2E_CLERK_EMAIL?.trim()
  const secretKey = process.env.CLERK_SECRET_KEY?.trim()
  // The suite skips itself without these; nothing to prepare.
  if (!email || !secretKey) return
  process.env.E2E_CLERK_EMAIL = email
  process.env.CLERK_SECRET_KEY = secretKey

  if (secretKey.startsWith('sk_live_')) {
    throw new Error('CLERK_SECRET_KEY is a production key. The e2e suite creates and signs in test accounts, '
      + 'so it must run against the Clerk development instance (an sk_test_ key).')
  }

  await requireSameInstance(process.env.VITE_CLERK_PUBLISHABLE_KEY)

  let user = await findUser(email)
  if (!user) {
    if (!email.includes('+clerk_test')) {
      throw new Error(`No Clerk user with the email in E2E_CLERK_EMAIL. Only a Clerk test address `
        + `(one containing "+clerk_test", e.g. e2e+clerk_test@example.com) is created automatically, `
        + `so create this account in the Clerk dashboard or switch to a test address.`)
    }
    console.log('[e2e] The Clerk test account was missing; creating it.')
    user = await createUser(email)
  } else {
    console.log('[e2e] Using the existing Clerk test account.')
  }
  await ensureSecondFactor(user)
  process.env.E2E_CLERK_USER_ID = user.id
}
