import { test, expect } from '@playwright/test';

/**
 * Command Palette (Cmd+K) E2E Tests
 *
 * TC-10.1: Cmd+K opens command palette
 * TC-10.2: ESC closes it
 * TC-10.3: Click search button also opens it
 *
 * The command palette is implemented in the Header component.
 * It opens as a modal with a search input and is controlled by the
 * searchOpen state. Keyboard shortcut: Cmd+K (Mac) / Ctrl+K (other).
 */

test.describe('TC-10.1: Cmd+K Opens Command Palette', () => {
  test('should open command palette with Cmd+K', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page
      .locator('text=Welcome to Forge')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isDashboard) {
      // Redirected to login
      // TODO: Implement authenticated test flow
      test.skip();
      return;
    }

    // Verify command palette is NOT visible initially
    await expect(
      page.locator('input[placeholder="Search workspaces, files, knowledge..."]')
    ).not.toBeVisible();

    // Press Cmd+K (Meta+K on Mac, Control+K on Linux/Windows)
    await page.keyboard.press('Meta+k');

    // Command palette modal should now be visible
    const paletteInput = page.locator(
      'input[placeholder="Search workspaces, files, knowledge..."]'
    );
    await expect(paletteInput).toBeVisible({ timeout: 2_000 });

    // The input should be auto-focused
    await expect(paletteInput).toBeFocused();

    // The ESC hint should be visible
    await expect(
      page.locator('.fixed').locator('text=ESC')
    ).toBeVisible();

    // The "Start typing to search..." placeholder text should be visible
    await expect(page.locator('text=Start typing to search...')).toBeVisible();
  });

  test('should open command palette with Ctrl+K (cross-platform)', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page
      .locator('text=Welcome to Forge')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isDashboard) {
      test.skip();
      return;
    }

    // Press Ctrl+K (works on all platforms)
    await page.keyboard.press('Control+k');

    const paletteInput = page.locator(
      'input[placeholder="Search workspaces, files, knowledge..."]'
    );
    await expect(paletteInput).toBeVisible({ timeout: 2_000 });
  });
});

test.describe('TC-10.2: ESC Closes Command Palette', () => {
  test('should close command palette when ESC is pressed', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page
      .locator('text=Welcome to Forge')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isDashboard) {
      test.skip();
      return;
    }

    // Open command palette
    await page.keyboard.press('Meta+k');
    const paletteInput = page.locator(
      'input[placeholder="Search workspaces, files, knowledge..."]'
    );
    await expect(paletteInput).toBeVisible({ timeout: 2_000 });

    // Press ESC to close
    await page.keyboard.press('Escape');

    // Command palette should be hidden
    await expect(paletteInput).not.toBeVisible({ timeout: 2_000 });
  });

  test('should close command palette when clicking the backdrop overlay', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page
      .locator('text=Welcome to Forge')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isDashboard) {
      test.skip();
      return;
    }

    // Open command palette
    await page.keyboard.press('Meta+k');
    const paletteInput = page.locator(
      'input[placeholder="Search workspaces, files, knowledge..."]'
    );
    await expect(paletteInput).toBeVisible({ timeout: 2_000 });

    // Click the backdrop overlay (the fixed inset-0 div with bg-black/50)
    // Click at the edge of the viewport to hit the backdrop, not the modal
    await page.click('.bg-black\\/50');

    // Command palette should be hidden
    await expect(paletteInput).not.toBeVisible({ timeout: 2_000 });
  });

  test('should toggle command palette with repeated Cmd+K presses', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page
      .locator('text=Welcome to Forge')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isDashboard) {
      test.skip();
      return;
    }

    const paletteInput = page.locator(
      'input[placeholder="Search workspaces, files, knowledge..."]'
    );

    // First Cmd+K: open
    await page.keyboard.press('Meta+k');
    await expect(paletteInput).toBeVisible({ timeout: 2_000 });

    // Second Cmd+K: close (toggle behavior)
    await page.keyboard.press('Meta+k');
    await expect(paletteInput).not.toBeVisible({ timeout: 2_000 });

    // Third Cmd+K: open again
    await page.keyboard.press('Meta+k');
    await expect(paletteInput).toBeVisible({ timeout: 2_000 });
  });
});

test.describe('TC-10.3: Search Button Opens Command Palette', () => {
  test('should open command palette when clicking the search button in header', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page
      .locator('text=Welcome to Forge')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isDashboard) {
      test.skip();
      return;
    }

    // The search button in the header shows "Search..." and "Cmd+K"
    const searchButton = page.locator('header button', { hasText: 'Search...' });
    await expect(searchButton).toBeVisible();

    // Click the search button
    await searchButton.click();

    // Command palette should open
    const paletteInput = page.locator(
      'input[placeholder="Search workspaces, files, knowledge..."]'
    );
    await expect(paletteInput).toBeVisible({ timeout: 2_000 });
    await expect(paletteInput).toBeFocused();
  });

  test('should allow typing in the command palette search', async ({ page }) => {
    await page.goto('/');

    const isDashboard = await page
      .locator('text=Welcome to Forge')
      .isVisible({ timeout: 10_000 })
      .catch(() => false);

    if (!isDashboard) {
      test.skip();
      return;
    }

    // Open via search button
    const searchButton = page.locator('header button', { hasText: 'Search...' });
    await searchButton.click();

    const paletteInput = page.locator(
      'input[placeholder="Search workspaces, files, knowledge..."]'
    );
    await expect(paletteInput).toBeVisible({ timeout: 2_000 });

    // Type a search query
    await paletteInput.fill('workspace');
    await expect(paletteInput).toHaveValue('workspace');

    // The "Start typing to search..." text should still be visible
    // (since there's no actual search implementation yet)
    // Close with ESC
    await page.keyboard.press('Escape');
    await expect(paletteInput).not.toBeVisible();
  });
});
