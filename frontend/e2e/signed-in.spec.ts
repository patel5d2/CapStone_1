import { expect, test } from '@playwright/test'
import { clerk, clerkSetup } from '@clerk/testing/playwright'

/*
 * Signed-in journeys against the real backend. Skipped unless all of these are set in the
 * environment of the person running them (never committed):
 *
 *   CLERK_SECRET_KEY        the Clerk DEV instance's secret key (testing tokens need it)
 *   E2E_CLERK_EMAIL         an existing test account in that instance, used through
 *                           Clerk's ticket sign-in, so no password or code is typed
 *
 * and the stack started for testing, with two-step enforcement off on both sides (a ticket
 * sign-in has no second factor to show):
 *
 *   backend:  CAMPUSBRIDGE_AUTH_REQUIRE_TWO_FACTOR=false ./mvnw spring-boot:run
 *   frontend: VITE_REQUIRE_TWO_FACTOR=false npx playwright test e2e/signed-in.spec.ts
 */
const ready = Boolean(process.env.CLERK_SECRET_KEY && process.env.E2E_CLERK_EMAIL)
test.skip(!ready, 'needs CLERK_SECRET_KEY and E2E_CLERK_EMAIL, and the backend on :8080')
test.describe.configure({ mode: 'serial' })

const stamp = Date.now()

test.beforeAll(async () => {
  await clerkSetup({ publishableKey: process.env.VITE_CLERK_PUBLISHABLE_KEY })
})

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  await clerk.signIn({ page, emailAddress: process.env.E2E_CLERK_EMAIL! })
})

test('profile: a signed-in student can open and save their profile', async ({ page }) => {
  await page.goto('/profile')
  await expect(page.getByRole('heading', { name: /complete your profile|my profile/i })).toBeVisible()
  const isNew = await page.getByRole('heading', { name: /complete your profile/i }).isVisible()
  if (isNew) {
    await page.getByLabel(/^School/).selectOption({ index: 1 })
    await page.getByLabel(/^Major/).fill('Information Technology')
    await page.getByLabel(/^City/).fill('Cincinnati')
    await page.getByLabel(/^State/).fill('OH')
  }
  await page.getByRole('button', { name: /save profile/i }).click()
  await expect(page.getByText(/profile and visibility saved/i).first()).toBeVisible()
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
  await page.getByRole('button', { name: 'Ask for help' }).first().click()
  await page.getByRole('dialog').getByRole('textbox').first().fill(description)
  await page.getByRole('button', { name: 'Post anonymously' }).click()
  await expect(page.getByText(description).first()).toBeVisible()
  await expect(page.getByText(process.env.E2E_CLERK_EMAIL!)).toHaveCount(0)
})
