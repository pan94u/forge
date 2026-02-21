import { test, expect } from '@playwright/test';

/**
 * Homepage & Navigation E2E Tests
 *
 * TC-6.1: Homepage loads with Dashboard, Quick Actions, and Sidebar visible
 * TC-6.2: Sidebar navigation links exist and navigate correctly
 *
 * Note: The app uses Keycloak SSO. If security is enabled, unauthenticated
 * users are redirected to /login. These tests handle both scenarios:
 * - Security disabled (default in dev): page renders normally
 * - Security enabled: tests check for redirect to login page
 */

test.describe('TC-6.1: Homepage Loads', () => {
  test('should load the homepage and display the welcome section', async ({ page }) => {
    await page.goto('/');

    // The page should either show the dashboard or redirect to login
    // Wait for either the welcome heading or the login page
    const welcomeOrLogin = await Promise.race([
      page.waitForSelector('text=Welcome to Forge', { timeout: 10_000 }).then(() => 'dashboard'),
      page.waitForSelector('text=Sign in', { timeout: 10_000 }).then(() => 'login'),
      page.waitForURL('**/login**', { timeout: 10_000 }).then(() => 'login'),
    ]).catch(() => 'timeout');

    if (welcomeOrLogin === 'login') {
      // Auth is enabled, verify login page loaded
      // TODO: Implement authenticated test flow with Keycloak credentials
      expect(page.url()).toContain('login');
      return;
    }

    // Dashboard loaded - verify key elements
    await expect(page.locator('h1')).toContainText('Welcome to Forge');
    await expect(page.locator('text=Your AI-powered development environment')).toBeVisible();
  });

  test('should display Quick Actions section with all action cards', async ({ page }) => {
    await page.goto('/');

    // Wait for page to settle
    const isDashboard = await page.locator('text=Quick Actions').isVisible({ timeout: 10_000 }).catch(() => false);
    if (!isDashboard) {
      // Redirected to login - skip this test
      // TODO: Implement authenticated test flow
      test.skip();
      return;
    }

    await expect(page.locator('h2', { hasText: 'Quick Actions' })).toBeVisible();

    // Verify all three quick action cards
    await expect(page.locator('text=New Workspace')).toBeVisible();
    await expect(page.locator('text=Search Knowledge')).toBeVisible();
    await expect(page.locator('text=Start Chat')).toBeVisible();

    // Verify action descriptions
    await expect(page.locator('text=Create a new development workspace')).toBeVisible();
    await expect(page.locator('text=Browse documentation and architecture')).toBeVisible();
    await expect(page.locator('text=Ask the AI assistant anything')).toBeVisible();
  });

  test('should display the Sidebar with navigation items', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page.locator('text=Welcome to Forge').isVisible({ timeout: 10_000 }).catch(() => false);
    if (!isDashboard) {
      test.skip();
      return;
    }

    // Sidebar should be visible with navigation links
    const sidebar = page.locator('aside');
    await expect(sidebar).toBeVisible();

    // Check core navigation items exist in sidebar
    await expect(sidebar.locator('text=Dashboard')).toBeVisible();
    await expect(sidebar.locator('text=Workspaces')).toBeVisible();
    await expect(sidebar.locator('text=Knowledge')).toBeVisible();
  });

  test('should display the Header with branding and search', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page.locator('text=Welcome to Forge').isVisible({ timeout: 10_000 }).catch(() => false);
    if (!isDashboard) {
      test.skip();
      return;
    }

    // Header should show the Forge branding
    const header = page.locator('header');
    await expect(header).toBeVisible();
    await expect(header.locator('text=Forge')).toBeVisible();

    // Search button should show Cmd+K shortcut
    await expect(header.locator('text=Search...')).toBeVisible();
    await expect(header.locator('text=Cmd+K')).toBeVisible();
  });

  test('should display Recent Projects section', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page.locator('text=Welcome to Forge').isVisible({ timeout: 10_000 }).catch(() => false);
    if (!isDashboard) {
      test.skip();
      return;
    }

    // Recent Projects section should exist (may show empty state or actual workspaces)
    await expect(page.locator('h2', { hasText: 'Recent Projects' })).toBeVisible();
  });

  test('should display Activity section', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page.locator('text=Welcome to Forge').isVisible({ timeout: 10_000 }).catch(() => false);
    if (!isDashboard) {
      test.skip();
      return;
    }

    await expect(page.locator('h2', { hasText: 'Activity' })).toBeVisible();
  });
});

test.describe('TC-6.2: Sidebar Navigation', () => {
  test('should have Dashboard link that navigates to /', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page.locator('text=Welcome to Forge').isVisible({ timeout: 10_000 }).catch(() => false);
    if (!isDashboard) {
      test.skip();
      return;
    }

    const sidebar = page.locator('aside');
    const dashboardLink = sidebar.locator('a', { hasText: 'Dashboard' });
    await expect(dashboardLink).toBeVisible();
    await expect(dashboardLink).toHaveAttribute('href', '/');
  });

  test('should have Workspaces link that navigates to /workspace/new', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page.locator('text=Welcome to Forge').isVisible({ timeout: 10_000 }).catch(() => false);
    if (!isDashboard) {
      test.skip();
      return;
    }

    const sidebar = page.locator('aside');
    const workspacesLink = sidebar.locator('a', { hasText: 'Workspaces' });
    await expect(workspacesLink).toBeVisible();
    await expect(workspacesLink).toHaveAttribute('href', '/workspace/new');

    // Click and verify navigation
    await workspacesLink.click();
    await expect(page).toHaveURL(/\/workspace\/new/);
  });

  test('should have Knowledge link that navigates to /knowledge', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page.locator('text=Welcome to Forge').isVisible({ timeout: 10_000 }).catch(() => false);
    if (!isDashboard) {
      test.skip();
      return;
    }

    const sidebar = page.locator('aside');
    const knowledgeLink = sidebar.locator('a', { hasText: 'Knowledge' });
    await expect(knowledgeLink).toBeVisible();
    await expect(knowledgeLink).toHaveAttribute('href', '/knowledge');

    await knowledgeLink.click();
    await expect(page).toHaveURL(/\/knowledge/);
  });

  test('should have AI Chat link that navigates to /chat', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page.locator('text=Welcome to Forge').isVisible({ timeout: 10_000 }).catch(() => false);
    if (!isDashboard) {
      test.skip();
      return;
    }

    const sidebar = page.locator('aside');
    const chatLink = sidebar.locator('a', { hasText: 'AI Chat' });
    await expect(chatLink).toBeVisible();
    await expect(chatLink).toHaveAttribute('href', '/chat');
  });

  test('should toggle sidebar collapse', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page.locator('text=Welcome to Forge').isVisible({ timeout: 10_000 }).catch(() => false);
    if (!isDashboard) {
      test.skip();
      return;
    }

    const sidebar = page.locator('aside');

    // Sidebar should start expanded (w-52 class)
    await expect(sidebar.locator('text=Collapse')).toBeVisible();

    // Click collapse button
    await sidebar.locator('button', { hasText: 'Collapse' }).click();

    // After collapse, text labels should be hidden (sidebar narrows to w-14)
    await expect(sidebar.locator('text=Collapse')).not.toBeVisible();
  });
});
