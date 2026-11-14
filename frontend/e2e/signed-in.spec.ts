import { expect, test, type Page } from '@playwright/test'
import { clerk, clerkSetup, setupClerkTestingToken } from '@clerk/testing/playwright'

/*
 * Signed-in journeys against the real backend. Skipped unless both of these are set, in the
 * git-ignored .env at the project root (or frontend/.env.local, or the shell; never committed):
 *
 *   CLERK_SECRET_KEY        the Clerk DEV instance's secret key (testing tokens need it)
 *   E2E_CLERK_EMAIL         an existing test account in that instance, used through
 *                           Clerk's ticket sign-in, so no password is typed
 *
 * The instance requires two-step verification, so that account must have a Clerk TEST phone
 * number (+1 555-555-0100 to 0199) enrolled as its second factor: those accept the fixed code
 * 424242 and never send an SMS. The account in use is e2e+clerk_test@example.com, with
 * +15555550142.
 *
 * playwright.config.ts copies them into process.env (Playwright itself reads no .env file)
 * and, when both are there, starts the backend and the SPA with two-step enforcement off
 * on both sides (a ticket sign-in has no second factor to show). So this is the whole run:
 *
 *   npx playwright test e2e/signed-in.spec.ts
 */
const ready = Boolean(process.env.CLERK_SECRET_KEY && process.env.E2E_CLERK_EMAIL)
test.skip(!ready, 'needs CLERK_SECRET_KEY and E2E_CLERK_EMAIL in .env (see the header of this file)')
test.describe.configure({ mode: 'serial' })

const stamp = Date.now()

test.beforeAll(async () => {
  await clerkSetup({ publishableKey: process.env.VITE_CLERK_PUBLISHABLE_KEY })
})

/** Clerk Backend API call with the secret key; Node-side only, never in the page. */
async function clerkApi<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`https://api.clerk.com/v1${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${process.env.CLERK_SECRET_KEY}`, 'Content-Type': 'application/json' },
  })
  if (!response.ok) throw new Error(`Clerk ${path}: ${response.status} ${await response.text()}`)
  return (await response.json()) as T
}

/**
 * `clerk.signIn({ emailAddress })` from @clerk/testing, plus the step it leaves out: once the
 * account has a second factor, Clerk's ticket sign-in stops at `needs_second_factor`. The
 * test phone factor is answered with Clerk's fixed test code.
 */
async function signIn(page: Page, emailAddress: string) {
  await setupClerkTestingToken({ context: page.context() })
  await clerk.loaded({ page })
  const users = await clerkApi<{ id: string }[]>(`/users?email_address=${encodeURIComponent(emailAddress)}`)
  if (users.length === 0) throw new Error(`No Clerk user with email ${emailAddress}`)
  const { token } = await clerkApi<{ token: string }>('/sign_in_tokens', {
    method: 'POST',
    body: JSON.stringify({ user_id: users[0].id, expires_in_seconds: 300 }),
  })
  await page.evaluate(async (ticket) => {
    type Attempt = {
      status: string
      createdSessionId: string
      supportedSecondFactors?: { strategy: string; phoneNumberId?: string }[] | null
      prepareSecondFactor(p: { strategy: 'phone_code'; phoneNumberId?: string }): Promise<Attempt>
      attemptSecondFactor(p: { strategy: 'phone_code'; code: string }): Promise<Attempt>
    }
    const Clerk = (window as unknown as {
      Clerk: { client: { signIn: { create(p: object): Promise<Attempt> } }; setActive(p: object): Promise<void> }
    }).Clerk
    let attempt = await Clerk.client.signIn.create({ strategy: 'ticket', ticket })
    if (attempt.status === 'needs_second_factor') {
      const phone = attempt.supportedSecondFactors?.find((factor) => factor.strategy === 'phone_code')
      if (!phone) throw new Error('The e2e account has no test phone number enrolled as its second factor')
      await attempt.prepareSecondFactor({ strategy: 'phone_code', phoneNumberId: phone.phoneNumberId })
      attempt = await attempt.attemptSecondFactor({ strategy: 'phone_code', code: '424242' })
    }
    if (attempt.status !== 'complete') throw new Error(`Clerk sign-in stopped at ${attempt.status}`)
    await Clerk.setActive({ session: attempt.createdSessionId })
  }, token)
  await page.waitForFunction(() => (window as unknown as { Clerk?: { user: unknown } }).Clerk?.user != null)
}

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  await signIn(page, process.env.E2E_CLERK_EMAIL!)
})

test('profile: a signed-in student can open and save their profile', async ({ page }) => {
  await page.goto('/profile')
  await expect(page.getByRole('heading', { name: /complete your profile|my profile/i })).toBeVisible()
  const isNew = await page.getByRole('heading', { name: /complete your profile/i }).isVisible()
  if (isNew) {
    await page.getByLabel(/^School/).selectOption({ index: 1 })
    // By role: "Major visibility" under Optional details is also labelled "Major…".
    await page.getByRole('textbox', { name: /^Major/ }).fill('Information Technology')
    await page.getByLabel(/^City/).fill('Cincinnati')
    await page.getByLabel(/^State/).selectOption('OH')
  }
  await page.getByRole('button', { name: /save profile/i }).click()
  // A first save is the end of sign-up and says so; later saves confirm the save.
  await expect(page.getByText(/welcome to campusbridge|profile and visibility saved/i).first()).toBeVisible()
})

test('marketplace: post, find in My listings, then delete a listing', async ({ page }) => {
  const title = `E2E desk lamp ${stamp}`
  await page.goto('/marketplace')
  await page.getByRole('button', { name: 'New listing' }).first().click()
  await page.getByLabel('Title').fill(title)
  await page.getByRole('button', { name: 'Post listing' }).click()
  await page.getByRole('button', { name: 'My listings' }).click()
  const card = page.getByRole('article').filter({ hasText: title })
  await expect(card).toBeVisible()
  page.on('dialog', (dialog) => dialog.accept())
  await card.getByRole('button', { name: 'Delete' }).click()
  await expect(page.getByText(title)).toHaveCount(0)
})

test('community: post, like and delete a post', async ({ page }) => {
  const text = `E2E hello from Playwright ${stamp}`
  await page.goto('/community')
  await page.getByLabel('New post').fill(text)
  await page.getByRole('button', { name: 'Post', exact: true }).click()
  const post = page.getByRole('article').filter({ hasText: text })
  await expect(post).toBeVisible()
  await post.getByRole('button', { name: /^Like post/ }).click()
  await expect(post.getByRole('button', { name: /^Unlike post/ })).toBeVisible()
  page.on('dialog', (dialog) => dialog.accept())
  await post.getByRole('button', { name: 'Delete post' }).click()
  await expect(page.getByText(text)).toHaveCount(0)
})

test('support: an anonymous request shows up for its author without naming them', async ({ page }) => {
  const description = `E2E groceries ${stamp}`
  await page.goto('/support')
  // The page opens on Resources; asking for help lives under Open requests.
  await page.getByRole('button', { name: 'Open requests' }).click()
  await page.getByRole('button', { name: 'Ask for help' }).first().click()
  await page.getByRole('dialog').getByLabel('What you need').fill(description)
  await page.getByRole('button', { name: 'Post anonymously' }).click()
  await expect(page.getByText(description).first()).toBeVisible()
  await expect(page.getByText(process.env.E2E_CLERK_EMAIL!)).toHaveCount(0)
})

test('marketplace: the type filter only offers types the category allows', async ({ page }) => {
  await page.goto('/marketplace')
  const category = page.getByLabel('Filter by category')
  const type = page.getByLabel('Filter by listing type')
  await category.selectOption('BOOKS')
  await type.selectOption('RENT')
  // Furniture cannot be rented: the option goes and the chosen "For rent" is cleared.
  await category.selectOption('FURNITURE')
  await expect(type.locator('option[value="RENT"]')).toHaveCount(0)
  await expect(type).toHaveValue('')
  for (const value of ['CLOTHES', 'EVENT_TICKETS']) {
    await category.selectOption(value)
    await expect(type.locator('option[value="RENT"]'), value).toHaveCount(0)
  }
  await category.selectOption('ELECTRONICS')
  await expect(type.locator('option[value="RENT"]')).toHaveCount(1)
})

test('marketplace: switching a new listing to furniture drops "For rent"', async ({ page }) => {
  await page.goto('/marketplace')
  await page.getByRole('button', { name: 'New listing' }).first().click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Category', { exact: true }).selectOption('BOOKS')
  await dialog.getByLabel('Listing type').selectOption('RENT')
  await dialog.getByLabel('Category', { exact: true }).selectOption('FURNITURE')
  await expect(dialog.getByLabel('Listing type')).toHaveValue('SELL')
  await expect(dialog.getByLabel('Listing type').locator('option[value="RENT"]')).toHaveCount(0)
})

for (const path of ['/marketplace', '/support']) {
  test(`${path}: filtering by a school shows that school's colours`, async ({ page }) => {
    await page.goto(path)
    await page.getByLabel('Filter by school').selectOption({ label: 'Miami University' })
    const section = page.locator('[data-school="miami"]').first()
    await expect(section).toBeVisible()
    expect(await section.evaluate((el) => getComputedStyle(el).getPropertyValue('--color-primary-600').trim())).toBe('#c41230')
    await page.getByLabel('Filter by school').selectOption({ label: 'All schools' })
    // Miami cards keep their own colours; only the page section around the filter resets.
    await expect(page.locator('[data-school="miami"]').filter({ has: page.getByLabel('Filter by school') })).toHaveCount(0)
  })
}

test("community directory: picking a school shows that school's colours", async ({ page }) => {
  await page.goto('/community?tab=directory')
  await page.getByLabel('School', { exact: true }).selectOption('Miami University')
  const section = page.locator('[data-school="miami"]').filter({ has: page.getByLabel('School', { exact: true }) })
  expect(await section.evaluate((el) => getComputedStyle(el).getPropertyValue('--color-primary-600').trim())).toBe('#c41230')
  await page.getByLabel('School', { exact: true }).selectOption('')
  await expect(section).toHaveCount(0)
})
