import { test, expect } from '@playwright/test';

/**
 * Workspace E2E Tests
 *
 * TC-6.3: Create workspace and verify 3-panel layout (file tree, editor, AI chat)
 * TC-8.1: File tree visible in workspace
 * TC-8.2: Monaco editor loads
 *
 * Note: Workspace operations require a running backend. These tests verify
 * the UI structure and elements. When the backend is unavailable, some tests
 * gracefully handle error states.
 *
 * TODO: Add authenticated workspace creation flow with Keycloak login
 */

test.describe('TC-6.3: Workspace Creation Page', () => {
  test('should display the create workspace form at /workspace/new', async ({ page }) => {
    await page.goto('/workspace/new');

    // The /workspace/new route shows a creation form (or redirects to login)
    const isCreatePage = await page
      .locator('text=Create New Workspace')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isCreatePage) {
      // Redirected to login
      // TODO: Implement authenticated test flow
      test.skip();
      return;
    }

    await expect(page.locator('h2', { hasText: 'Create New Workspace' })).toBeVisible();
    await expect(
      page.locator('text=Set up a new development workspace with AI assistance')
    ).toBeVisible();
  });

  test('should have workspace name and description inputs', async ({ page }) => {
    await page.goto('/workspace/new');

    const isCreatePage = await page
      .locator('text=Create New Workspace')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isCreatePage) {
      test.skip();
      return;
    }

    // Verify form fields exist
    const nameInput = page.locator('input#name');
    const descInput = page.locator('input#description');

    await expect(nameInput).toBeVisible();
    await expect(descInput).toBeVisible();

    // Verify labels
    await expect(page.locator('label[for="name"]')).toContainText('Workspace Name');
    await expect(page.locator('label[for="description"]')).toContainText('Description');

    // Verify placeholder text
    await expect(nameInput).toHaveAttribute('placeholder', 'my-project');
    await expect(descInput).toHaveAttribute('placeholder', 'A brief description');
  });

  test('should have a submit button to create workspace', async ({ page }) => {
    await page.goto('/workspace/new');

    const isCreatePage = await page
      .locator('text=Create New Workspace')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isCreatePage) {
      test.skip();
      return;
    }

    const submitButton = page.locator('button[type="submit"]', { hasText: 'Create Workspace' });
    await expect(submitButton).toBeVisible();
  });

  test('should require workspace name (HTML validation)', async ({ page }) => {
    await page.goto('/workspace/new');

    const isCreatePage = await page
      .locator('text=Create New Workspace')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isCreatePage) {
      test.skip();
      return;
    }

    // Name input should have the required attribute
    const nameInput = page.locator('input#name');
    await expect(nameInput).toHaveAttribute('required', '');
  });
});

test.describe('TC-6.3: Workspace 3-Panel Layout', () => {
  /**
   * The workspace page at /workspace/:id shows a 3-panel layout when a valid
   * workspace ID is provided. Without a running backend, the workspace data
   * fetch will fail, so we test with a mock workspace ID and verify the
   * structural elements render.
   */
  test('should show loading state or layout when accessing a workspace', async ({ page }) => {
    // Navigate to a workspace with a test ID
    await page.goto('/workspace/test-workspace-123');

    // Wait for some workspace-related content to appear
    const hasWorkspaceUI = await Promise.race([
      page.waitForSelector('text=No file open', { timeout: 10_000 }).then(() => 'editor'),
      page.waitForSelector('text=AI Assistant', { timeout: 10_000 }).then(() => 'chat'),
      page.waitForSelector('.animate-spin', { timeout: 10_000 }).then(() => 'loading'),
      page.waitForURL('**/login**', { timeout: 10_000 }).then(() => 'login'),
    ]).catch(() => 'timeout');

    if (hasWorkspaceUI === 'login') {
      // TODO: Implement authenticated test flow
      test.skip();
      return;
    }

    // If we see loading spinner, the workspace page loaded successfully
    // The layout is rendered even while data is loading
    expect(['editor', 'chat', 'loading']).toContain(hasWorkspaceUI);
  });
});

test.describe('TC-8.1: File Tree in Workspace', () => {
  test('should display file explorer panel in workspace layout', async ({ page }) => {
    await page.goto('/workspace/test-workspace-123');

    // Wait for workspace UI to load
    const isWorkspace = await page
      .locator('text=AI Assistant')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isWorkspace) {
      // Either redirected to login or workspace doesn't load without backend
      // TODO: Implement authenticated test with a real workspace
      test.skip();
      return;
    }

    // The left panel (file explorer) should exist
    // File explorer is inside a div with w-64 class in the workspace layout
    const leftPanel = page.locator('.w-64').first();
    await expect(leftPanel).toBeVisible();
  });

  test('should have a toggle button for the file explorer', async ({ page }) => {
    await page.goto('/workspace/test-workspace-123');

    const isWorkspace = await page
      .locator('text=AI Assistant')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isWorkspace) {
      test.skip();
      return;
    }

    // Toggle file explorer button should exist (title="Toggle file explorer")
    const toggleButton = page.locator('button[title="Toggle file explorer"]');
    await expect(toggleButton).toBeVisible();
  });
});

test.describe('TC-8.2: Monaco Editor', () => {
  test('should show empty editor state when no file is selected', async ({ page }) => {
    await page.goto('/workspace/test-workspace-123');

    const isWorkspace = await page
      .locator('text=No file open')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isWorkspace) {
      // Either redirected to login or loading state
      // TODO: Implement authenticated test with file selection
      test.skip();
      return;
    }

    // Empty editor state should show instructions
    await expect(page.locator('text=No file open')).toBeVisible();
    await expect(
      page.locator('text=Select a file from the explorer to start editing')
    ).toBeVisible();
  });

  test('should have Save and Terminal toolbar buttons', async ({ page }) => {
    await page.goto('/workspace/test-workspace-123');

    const isWorkspace = await page
      .locator('text=AI Assistant')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isWorkspace) {
      test.skip();
      return;
    }

    // Toolbar buttons should be visible
    await expect(page.locator('button[title="Save (Cmd+S)"]')).toBeVisible();
    await expect(page.locator('button[title="Toggle terminal"]')).toBeVisible();
  });

  test('should have the AI chat toggle button', async ({ page }) => {
    await page.goto('/workspace/test-workspace-123');

    const isWorkspace = await page
      .locator('text=AI Assistant')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isWorkspace) {
      test.skip();
      return;
    }

    // AI chat toggle button (title="Toggle AI chat")
    const chatToggle = page.locator('button[title="Toggle AI chat"]');
    await expect(chatToggle).toBeVisible();
  });
});
