import { test } from '../support/fixtures';
import { requireAccounts } from '../support/accounts';
import { expectSignedInAs, login, logout } from '../support/auth';

test('one browser: A signs in, signs out, then B signs in', async ({ page }) => {
  const { A, B } = requireAccounts('A', 'B');

  await test.step('A signs in', () => login(page, A));
  await test.step('the app acts as A', () => expectSignedInAs(page, A));
  await test.step('A signs out', () => logout(page));

  await test.step('B signs in on the same browser', () => login(page, B));
  await test.step('the app acts as B, not A', () => expectSignedInAs(page, B));
  await test.step('B signs out', () => logout(page));

  await test.step('A signs in again', () => login(page, A));
  await test.step('the app acts as A again', () => expectSignedInAs(page, A));
  await test.step('A signs out', () => logout(page));
});

test('two browsers: A and B are signed in at the same time', async ({ page, openSession }) => {
  const { A, B } = requireAccounts('A', 'B');
  const pageB = await openSession('B');

  await test.step('A signs in', () => login(page, A));
  await test.step('B signs in in a separate session', () => login(pageB, B));
  await test.step('sessions stay separate', async () => {
    await expectSignedInAs(page, A);
    await expectSignedInAs(pageB, B);
  });
  await test.step('both sign out', async () => {
    await logout(page);
    await logout(pageB);
  });
});
