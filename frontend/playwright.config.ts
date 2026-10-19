import { defineConfig, devices } from '@playwright/test'

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
  webServer: {
    command: 'npm run dev -- --port 5174 --strictPort',
    url: 'http://localhost:5174',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
