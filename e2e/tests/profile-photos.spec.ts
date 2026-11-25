import path from 'node:path';
import { test, expect } from '../support/fixtures';
import { requireAccounts } from '../support/accounts';
import { deleteAccountInApp, login, logout } from '../support/auth';
import { think, typeLikeHuman } from '../support/human';

const TEST_PHOTO = path.resolve(__dirname, '../fixtures/test-photo.png');

test('profile photo upload, persistence across reload, and deletion', async ({ page }) => {
  const { A } = requireAccounts('A');
  let uploadedSlot: number | null = null;
  let createdProfile = false;

  await test.step('A signs in', () => login(page, A));

  try {
    await test.step('ensure account A has a profile and open photo manager', async () => {
      await page.goto('/profile');
      await think(page, 'read');

      const editPhotos = page.getByRole('button', { name: /edit profile photos/i });
      const createProfile = page.getByRole('button', { name: /^create profile$/i });
      await expect(editPhotos.or(createProfile).first()).toBeVisible();

      if (!(await editPhotos.isVisible())) {
        await test.step('create a profile for account A', async () => {
          await think(page, 'decide');
          await createProfile.click();
          await expect(page.getByRole('heading', { name: /^create profile$/i })).toBeVisible();
          await think(page, 'glance');

          await typeLikeHuman(page.getByLabel(/^name/i), 'Alex E2E');
          await typeLikeHuman(page.getByLabel(/^age/i), '27');
          await page.getByLabel(/^gender/i).selectOption('other');
          await typeLikeHuman(page.getByLabel(/^city/i), 'Berlin');
          await typeLikeHuman(page.getByLabel(/^bio/i), 'Automated e2e photo verification profile.');
          await think(page, 'decide');

          const submitBtn = page.getByRole('button', { name: /^create profile$/i });
          await expect(submitBtn).toBeEnabled();
          await submitBtn.click();
          await expect(page).toHaveURL(/\/(profile|discover)/);
          createdProfile = true;
          await think(page, 'read');

          await page.goto('/profile');
          await expect(editPhotos).toBeVisible();
          await think(page, 'glance');
        });
      }

      await think(page, 'decide');
      await editPhotos.click();
      await expect(page.getByRole('heading', { name: /your photos/i })).toBeVisible();
      await think(page, 'glance');
    });

    await test.step('upload a photo into the next free slot', async () => {
      const addButton = page.getByRole('button', { name: /^add photo \d+$/i }).first();
      await expect(addButton).toBeVisible();

      const label = (await addButton.getAttribute('aria-label')) ?? '';
      const match = label.match(/add photo (\d+)/i);
      if (!match) {
        throw new Error(`Could not determine target photo slot from aria-label="${label}"`);
      }
      const slot = Number(match[1]);

      const uploadResponse = page.waitForResponse(
        res => res.url().includes('/api/v1/profiles/photos/upload') && res.request().method() === 'POST'
      );
      const fileChooserPromise = page.waitForEvent('filechooser');

      await think(page, 'decide');
      await addButton.click();
      const fileChooser = await fileChooserPromise;
      await fileChooser.setFiles(TEST_PHOTO);

      const res = await uploadResponse;
      expect(res.status(), 'photo upload should return 201 Created').toBe(201);
      uploadedSlot = slot;

      const uploadedImg = page.getByRole('img', { name: `Profile photo ${slot}` });
      await expect(uploadedImg).toBeVisible();
      await expect
        .poll(async () => uploadedImg.evaluate((el: HTMLImageElement) => el.complete && el.naturalWidth > 0))
        .toBe(true);
      await think(page, 'read');
    });

    await test.step('reload profile to verify the photo persists and loads', async () => {
      const slot = uploadedSlot!;
      await page.getByRole('button', { name: /close photo manager/i }).click();
      await think(page, 'glance');

      await page.reload();
      await think(page, 'read');

      await page.getByRole('button', { name: /edit profile photos/i }).click();
      await expect(page.getByRole('heading', { name: /your photos/i })).toBeVisible();

      const persistedImg = page.getByRole('img', { name: `Profile photo ${slot}` });
      await expect(persistedImg).toBeVisible();
      await expect
        .poll(async () => persistedImg.evaluate((el: HTMLImageElement) => el.complete && el.naturalWidth > 0))
        .toBe(true);
      await think(page, 'read');
    });

    await test.step('remove the uploaded photo so the account stays clean', async () => {
      const slot = uploadedSlot!;
      const deleteResponse = page.waitForResponse(
        res => res.url().includes('/api/v1/profiles/photos/') && res.request().method() === 'DELETE'
      );
      page.once('dialog', dialog => dialog.accept());

      await think(page, 'decide');
      await page.getByRole('button', { name: `Remove photo ${slot}` }).click();
      const res = await deleteResponse;
      expect(res.ok(), 'photo delete should succeed').toBe(true);

      await expect(page.getByRole('button', { name: `Add photo ${slot}` })).toBeVisible();
      uploadedSlot = null;
      await think(page, 'glance');

      await page.getByRole('button', { name: /close photo manager/i }).click();
    });
  } finally {
    if (uploadedSlot !== null) {
      try {
        const removeBtn = page.getByRole('button', { name: `Remove photo ${uploadedSlot}` });
        if (await removeBtn.isVisible()) {
          page.once('dialog', dialog => dialog.accept());
          await removeBtn.click();
          await expect(page.getByRole('button', { name: `Add photo ${uploadedSlot}` })).toBeVisible({ timeout: 10_000 });
        }
      } catch {
        // Ignore cleanup errors during teardown
      }
    }
  }

  if (createdProfile) {
    await test.step('delete the temporary profile created for the test', async () => {
      await deleteAccountInApp(page);
    });
  } else {
    await test.step('A signs out', () => logout(page));
  }
});
