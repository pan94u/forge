import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E configuration for Forge Web IDE.
 *
 * Run with: npm run test:e2e
 * Or directly: npx playwright test
 *
 * Note: The app uses Keycloak SSO at http://localhost:8180.
 * When security is enabled, unauthenticated requests redirect to /login.
 * For CI without a real backend, tests should handle auth redirects gracefully.
 */
export default defineConfig({
  testDir: './e2e',

  /* Maximum time one test can run */
  timeout: 30_000,

  /* Expect timeout for assertions */
  expect: {
    timeout: 5_000,
  },

  /* Run tests sequentially in CI for stability */
  fullyParallel: true,

  /* Fail the build on CI if you accidentally left test.only in the source code */
  forbidOnly: !!process.env.CI,

  /* Retry once on CI to handle flakiness */
  retries: process.env.CI ? 1 : 0,

  /* Use 1 worker in CI for stability, local can use more */
  workers: process.env.CI ? 1 : undefined,

  /* Reporter: HTML for detailed reports */
  reporter: 'html',

  /* Shared settings for all projects */
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:9000',

    /* Collect trace on first retry for debugging */
    trace: 'on-first-retry',

    /* Screenshot on failure only */
    screenshot: 'only-on-failure',

    /* Video off to save CI time */
    video: 'off',
  },

  /* Only use Chromium */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
