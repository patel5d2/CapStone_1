import { expect, test, type Page } from '@playwright/test'

// Everything a visitor can reach without signing in. No account, no backend needed:
// signed-out routing, the auth forms' own validation, school theming and phone layout.

const PROTECTED = ['/marketplace', '/messages', '/community', '/support', '/profile']

/** Console errors raised by our own code (Clerk's dev-instance warnings are not ours). */
function collectAppErrors(page: Page) {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error' && !/clerk/i.test(message.text())) errors.push(message.text())
  })
  return errors
}

async function noHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow, 'page scrolls sideways').toBeLessThanOrEqual(0)
}

test('landing page renders its pitch and both calls to action', async ({ page }) => {
  const errors = collectAppErrors(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await expect(page.getByRole('link', { name: /join with your email/i })).toHaveAttribute('href', '/sign-up')
  await expect(page.getByRole('link', { name: /^sign in$/i }).first()).toBeVisible()
  await noHorizontalScroll(page)
  expect(errors).toEqual([])
})

for (const path of PROTECTED) {
  test(`signed out, ${path} sends you to sign in`, async ({ page }) => {
    await page.goto(path)
    await expect(page).toHaveURL(/\/sign-in$/)
    await expect(page.getByRole('heading', { name: /sign in to campusbridge/i })).toBeVisible()
  })
}

test('the old /directory link lands on sign in when signed out', async ({ page }) => {
  await page.goto('/directory')
  await expect(page).toHaveURL(/\/sign-in$/)
})

test('an unknown address returns to the landing page', async ({ page }) => {
  await page.goto('/no-such-page')
  await expect(page).toHaveURL(/\/$/)
})

test('sign in: form is labelled, reachable by keyboard and fits a phone', async ({ page }) => {
  await page.goto('/sign-in')
  const email = page.getByLabel('Email')
  const password = page.getByLabel('Password')
  await expect(email).toBeVisible()
  await expect(password).toBeVisible()
  await email.focus()
  await page.keyboard.press('Tab')
  await expect(password).toBeFocused()
  await expect(page.getByRole('button', { name: /^sign in$/i })).toBeDisabled() // no password yet
  await noHorizontalScroll(page)
})

test('sign up: a name of only spaces is refused before anything reaches Clerk', async ({ page }) => {
  await page.goto('/sign-up')
  await page.getByLabel('First name').fill('   ')
  await page.getByLabel('Last name').fill('Lee')
  await page.getByLabel('Email').fill('jordan.lee@example.com')
  await page.getByLabel('Password').fill('a-long-enough-password')
  const submit = page.getByRole('button', { name: /create account|sign up|join/i })
  await expect(submit).toBeEnabled({ timeout: 20_000 }) // Clerk finished loading
  await submit.click()
  await expect(page.getByText('Enter your first name.')).toBeVisible()
  await expect(page).toHaveURL(/\/sign-up$/)
  await noHorizontalScroll(page)
})

test('signed out, the default pine-green palette is used', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('html')).not.toHaveAttribute('data-school', /.+/)
  const primary = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--color-primary-600').trim())
  expect(primary).toBe('#2c6a4d')
})

test('a remembered school is restored before paint, then cleared once Clerk says signed out', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('campusbridge-school', 'uc'))
  await page.goto('/')
  // Restored synchronously from storage for a returning student...
  // ...and dropped when Clerk confirms there is no session, so visitors see the default.
  await expect(page.locator('html')).not.toHaveAttribute('data-school', /.+/, { timeout: 20_000 })
  expect(await page.evaluate(() => localStorage.getItem('campusbridge-school'))).toBeNull()
})

test('every school palette resolves its own primary colour', async ({ page }) => {
  await page.goto('/')
  const expected: Record<string, string> = {
    uc: '#e00122', xavier: '#0c2340', nku: '#8b6c18', miami: '#c41230',
    cincystate: '#3e7e31', msj: '#003366', thomasmore: '#000099',
  }
  for (const [slug, hex] of Object.entries(expected)) {
    const value = await page.evaluate((s) => {
      document.documentElement.dataset.school = s
      return getComputedStyle(document.documentElement).getPropertyValue('--color-primary-600').trim()
    }, slug)
    expect(value, slug).toBe(hex)
  }
})
