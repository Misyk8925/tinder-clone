import { test } from '../../support/fixtures';
import { requireAccounts } from '../../support/accounts';
import { expectSignedInAs, logout, register } from '../../support/auth';

test('register the test accounts again through the sign-up form', async ({ page }) => {
  for (const account of Object.values(requireAccounts('A', 'B'))) {
    await test.step(`${account.username}: create account`, () => register(page, account));
    await test.step(`${account.username}: the app acts as the new user`, () => expectSignedInAs(page, account));
    await test.step(`${account.username}: sign out`, () => logout(page));
  }
});
