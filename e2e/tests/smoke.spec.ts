import { test, expect } from '../support/fixtures';
import { think } from '../support/human';

test('a visitor reaches the sign-in form', async ({ page }) => {
  await test.step('open the landing page', async () => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await think(page, 'read');
  });

  await test.step('continue to Keycloak', async () => {
    await page.getByRole('button', { name: /continue securely/i }).click();
    await expect(page.getByLabel(/^password$/i).first()).toBeVisible();
    await think(page, 'glance');
  });
});
