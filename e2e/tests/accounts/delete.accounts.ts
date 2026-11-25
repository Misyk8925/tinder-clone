import { test, expect } from '../../support/fixtures';
import { requireAccounts } from '../../support/accounts';
import { deleteAccountInApp, logout, tryLogin } from '../../support/auth';
import { deleteTestUsersFromKeycloak } from '../../support/keycloak-reset';

test('fully delete the test accounts', async ({ page }) => {
  const accounts = Object.values(requireAccounts('A', 'B'));

  for (const account of accounts) {
    await test.step(`${account.username}: delete in the app`, async () => {
      if (!(await tryLogin(page, account))) {
        test.info().annotations.push({ type: 'note', description: `${account.username} could not sign in; nothing to delete in the app` });
        return;
      }
      if (!(await deleteAccountInApp(page))) {
        test.info().annotations.push({ type: 'note', description: `${account.username} had no profile` });
        await logout(page);
      }
    });
  }

  await test.step('delete the users from Keycloak', async () => {
    const answer = await deleteTestUsersFromKeycloak();
    test.info().annotations.push({ type: 'keycloak', description: JSON.stringify(answer) });
    expect(answer.ok, answer.error).toBe(true);
    for (const account of accounts) {
      expect(['deleted', 'absent']).toContain(answer.accounts?.[account.username]);
    }
  });

  await test.step('the old credentials no longer work', async () => {
    expect(await tryLogin(page, accounts[0])).toBe(false);
  });
});
