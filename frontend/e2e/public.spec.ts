import { expect, test, type Page } from '@playwright/test'

// Everything a visitor can reach without signing in. No account, no backend needed:
// signed-out routing, the auth forms' own validation, school theming and phone layout.

const PROTECTED = ['/marketplace', '/messages', '/community', '/support', '/profile', '/sign-up/profile']

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
  // Never disabled for an empty password: a browser-filled one may not be visible to the page.
  await expect(page.getByRole('button', { name: /^sign in$/i })).toBeEnabled({ timeout: 20_000 })
  await page.getByRole('button', { name: 'Show password' }).click()
  await expect(password).toHaveAttribute('type', 'text')
  await noHorizontalScroll(page)
})

const PICKERS = ['School', 'Year of study', 'State']

/** The whole sign-up form, valid unless a test overrides a field. */
async function fillSignUp(page: Page, overrides: Record<string, string> = {}) {
  const values: Record<string, string> = {
    'First name': 'Jordan', 'Last name': 'Lee', Email: 'jordan.lee@example.com', Password: 'a-long-enough-password',
    School: 'Xavier University', 'Year of study': 'Junior', Major: 'Nursing', City: 'Norwood', State: 'OH', ...overrides,
  }
  await page.goto('/sign-up')
  for (const [label, value] of Object.entries(values)) {
    if (PICKERS.includes(label)) await page.getByLabel(label).selectOption(value)
    else await page.getByLabel(label).fill(value)
  }
}

test('sign up: a name of only spaces is refused before anything reaches Clerk', async ({ page }) => {
  await fillSignUp(page, { 'First name': '   ' })
  const submit = page.getByRole('button', { name: /create account|sign up|join/i })
  await expect(submit).toBeEnabled({ timeout: 20_000 }) // Clerk finished loading
  await submit.click()
  await expect(page.getByText('Enter your first name.')).toBeVisible()
  // The student is taken to the field, which on a phone is a screen above the button.
  await expect(page.getByLabel('First name')).toBeFocused()
  await expect(page).toHaveURL(/\/sign-up$/)
  await noHorizontalScroll(page)
})

// #114: the profile's required details are asked on the sign-up form itself.
for (const [label, value, message] of [
  ['Major', '   ', 'Enter your major.'],
  ['City', '   ', 'Enter your city.'],
  ['School', '', 'Select your school.'],
  ['State', '', 'Select your state.'],
]) {
  test(`sign up: ${label} "${value}" is refused before anything reaches Clerk`, async ({ page }) => {
    await fillSignUp(page, { [label]: value })
    const submit = page.getByRole('button', { name: /create account/i })
    await expect(submit).toBeEnabled({ timeout: 20_000 }) // Clerk finished loading
    await submit.click()
    await expect(page.getByRole('alert')).toHaveText(message)
    await expect(page.getByLabel(label)).toBeFocused()
    await expect(page).toHaveURL(/\/sign-up$/)
    await noHorizontalScroll(page)
  })
}

test('sign up: says which step this is and lets the password be checked', async ({ page }) => {
  await page.goto('/sign-up')
  await expect(page.getByRole('list', { name: 'Sign-up steps' }).locator('[aria-current="step"]')).toContainText('Your details')
  const password = page.getByLabel('Password')
  await password.fill('a-long-enough-password')
  await expect(password).toHaveAttribute('type', 'password')
  await page.getByRole('button', { name: 'Show password' }).click()
  await expect(password).toHaveAttribute('type', 'text')
  await page.getByRole('button', { name: 'Hide password' }).click()
  await expect(password).toHaveAttribute('type', 'password')
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

test('a school palette can colour one part of the page without touching the rest', async ({ page }) => {
  // Support and Marketplace stamp data-school on a section or a card, not on <html>.
  await page.goto('/')
  const [scoped, root] = await page.evaluate(() => {
    const section = document.createElement('div')
    section.dataset.school = 'miami'
    document.body.append(section)
    const read = (el: Element) => getComputedStyle(el).getPropertyValue('--color-primary-600').trim()
    return [read(section), read(document.documentElement)]
  })
  expect(scoped).toBe('#c41230')
  expect(root).toBe('#2c6a4d')
})
