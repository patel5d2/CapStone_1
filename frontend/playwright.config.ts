import { existsSync, readFileSync } from 'node:fs'
import { parseEnv } from 'node:util'
import { defineConfig, devices } from '@playwright/test'

/*
 * Playwright runs in plain Node, and Node reads no .env file. Vite does read frontend/.env,
 * but only into the browser bundle, so nothing a spec reads from `process.env` ever came
 * from a file: the signed-in suite saw no keys and skipped itself. The keys it needs are
 * copied in here from the git-ignored env files (frontend first, then the project root,
 * where CLERK_SECRET_KEY already lives for the backend). A value already in the shell or
 * in CI wins. Only these keys are copied: the root .env also holds database settings that
 * must not reach the servers started below.
 */
const E2E_KEYS = ['CLERK_SECRET_KEY', 'E2E_CLERK_EMAIL', 'VITE_CLERK_PUBLISHABLE_KEY']
for (const file of ['.env.local', '.env', '../.env.local', '../.env']) {
  const path = new URL(file, import.meta.url)
  if (!existsSync(path)) continue
  const values = parseEnv(readFileSync(path, 'utf8'))
  for (const key of E2E_KEYS) {
    if (!process.env[key] && values[key]) process.env[key] = values[key]
  }
}

/** Same test as the gate at the top of e2e/signed-in.spec.ts. */
const signedIn = Boolean(process.env.CLERK_SECRET_KEY && process.env.E2E_CLERK_EMAIL)
const reuseExistingServer = !process.env.CI

// Say so where a skipped suite would otherwise go unnoticed (once, not in every worker).
if (process.env.CI && !signedIn && process.env.TEST_WORKER_INDEX === undefined) {
  console.warn('::warning title=Signed-in e2e skipped::CLERK_SECRET_KEY and E2E_CLERK_EMAIL are not set, so e2e/signed-in.spec.ts did not run.')
}

/**
 * End-to-end tests against the real SPA (Vite dev server) and the real Clerk instance.
 * `e2e/public.spec.ts` covers everything reachable without signing in and runs anywhere.
 * `e2e/signed-in.spec.ts` needs a Clerk test account and skips without one (see its header).
 * The Definition of Done asks for Chrome, Safari and Firefox at phone width too, so every
 * test runs in each browser at desktop and at 375px; `npx playwright install` fetches the
 * engines a machine does not have yet.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:5174',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'chromium-375', use: { ...devices['Desktop Chrome'], viewport: { width: 375, height: 812 } } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
    { name: 'webkit-375', use: { ...devices['iPhone 13'], viewport: { width: 375, height: 812 } } },
  ],
  webServer: [
    // The signed-in suite also needs the API. Both servers run with two-step enforcement
    // off (a ticket sign-in has no second factor to show), and the backend is pinned to
    // the in-memory H2 profile so test data never lands in a shared database. A server
    // already listening on the port is reused locally and must have been started the
    // same way.
    ...(signedIn
      ? [
          {
            command: './mvnw --batch-mode spring-boot:run',
            cwd: '..',
            url: 'http://localhost:8080/actuator/health',
            env: { CAMPUSBRIDGE_AUTH_REQUIRE_TWO_FACTOR: 'false', SPRING_PROFILES_ACTIVE: 'default' },
            reuseExistingServer,
            timeout: 300_000,
          },
        ]
      : []),
    {
      command: 'npm run dev -- --port 5174 --strictPort',
      url: 'http://localhost:5174',
      env: signedIn ? { VITE_REQUIRE_TWO_FACTOR: 'false' } : {},
      reuseExistingServer,
      timeout: 120_000,
    },
  ],
})
