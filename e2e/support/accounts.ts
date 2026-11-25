import { test } from '@playwright/test';

/**
 * Test accounts come from the environment only (on the VPS: ~/.config/e2e/accounts.env,
 * passed with --env-file by e2e-record). Never hard-code credentials in specs.
 *
 *   E2E_USER_A_USERNAME / E2E_USER_A_PASSWORD [/ E2E_USER_A_EMAIL]
 *   E2E_USER_B_USERNAME / E2E_USER_B_PASSWORD [/ E2E_USER_B_EMAIL]
 *
 * The e-mail must stay on @lunari.test: root's e2e-accounts-reset deletes only allowlisted
 * usernames whose e-mail is on that domain.
 */
export type AccountKey = 'A' | 'B';

export interface Account {
  key: AccountKey;
  username: string;
  password: string;
  email: string;
  firstName: string;
  lastName: string;
}

export function findAccount(key: AccountKey): Account | undefined {
  const username = process.env[`E2E_USER_${key}_USERNAME`];
  const password = process.env[`E2E_USER_${key}_PASSWORD`];
  if (!username || !password) return undefined;
  const email = process.env[`E2E_USER_${key}_EMAIL`] ?? `${username}@lunari.test`;
  return { key, username, password, email, firstName: 'E2E', lastName: `User-${key}` };
}

/** Returns the accounts, or skips the current test with a clear reason if any is missing. */
export function requireAccounts<K extends AccountKey>(...keys: K[]): Record<K, Account> {
  const found = {} as Record<K, Account>;
  const missing: string[] = [];
  for (const key of keys) {
    const account = findAccount(key);
    if (account) found[key] = account;
    else missing.push(`E2E_USER_${key}_USERNAME/PASSWORD`);
  }
  test.skip(missing.length > 0, `test accounts not configured: ${missing.join(', ')}`);
  return found;
}
