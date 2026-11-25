import { expect, type Page } from '@playwright/test';
import type { Account } from './accounts';
import { think, typeLikeHuman } from './human';

/**
 * Sign-in and sign-out through the real UI, the way a user does it: the app's own login screen,
 * then the Keycloak form on another host, then back. No token injection, so every run also
 * proves the redirect chain (PKCE, redirect URIs, logout redirect) still works.
 */

const APP_ORIGIN = new URL(process.env.E2E_BASE_URL ?? 'https://lunari.misyk.tech').origin;

function onKeycloak(page: Page): boolean {
  return page.url().startsWith('http') && new URL(page.url()).origin !== APP_ORIGIN;
}

async function openKeycloak(page: Page): Promise<void> {
  if (onKeycloak(page)) return;
  await page.goto('/login');
  await think(page, 'read');
  await page.getByRole('button', { name: /continue securely/i }).click();
  await page.waitForURL(url => url.origin !== APP_ORIGIN);
}

export async function login(page: Page, account: Account): Promise<void> {
  if (!(await tryLogin(page, account))) {
    throw new Error(`sign-in as ${account.username} was rejected by Keycloak`);
  }
}

/** Signs in and returns true, or returns false if Keycloak rejects the credentials. */
export async function tryLogin(page: Page, account: Account): Promise<boolean> {
  await openKeycloak(page);

  const username = page.getByLabel(/username|e-?mail/i).first();
  await username.waitFor({ state: 'visible' });
  await think(page, 'glance');
  await typeLikeHuman(username, account.username);
  await think(page, 'glance');
  await typeLikeHuman(page.getByLabel(/^password$/i).first(), account.password);
  await think(page, 'decide');
  await page.getByRole('button', { name: /^(sign in|log in|login|continue)$/i }).first().click();

  const signedIn = await page
    .waitForURL(url => url.origin === APP_ORIGIN && !url.pathname.startsWith('/login'), { timeout: 20_000 })
    .then(() => true, () => false);
  await think(page, signedIn ? 'read' : 'glance');
  return signedIn;
}

/** Register through Keycloak's "Create account" form, as a new user would. Ends signed in. */
export async function register(page: Page, account: Account): Promise<void> {
  await openKeycloak(page);
  await page.getByRole('link', { name: /create account/i }).click();
  await page.getByRole('heading', { name: /create your account/i }).waitFor();
  await think(page, 'read');
  await typeLikeHuman(page.getByLabel(/^first name$/i), account.firstName);
  await typeLikeHuman(page.getByLabel(/^last name$/i), account.lastName);
  await typeLikeHuman(page.getByLabel(/^e-?mail$/i), account.email);
  await typeLikeHuman(page.getByLabel(/^username$/i), account.username);
  await typeLikeHuman(page.getByLabel(/^password$/i), account.password);
  await typeLikeHuman(page.getByLabel(/^confirm password$/i), account.password);
  await think(page, 'decide');
  await page.getByRole('button', { name: /^create account$/i }).click();
  await page.waitForURL(url => url.origin === APP_ORIGIN && !url.pathname.startsWith('/login'));
  await think(page, 'read');
}

/**
 * Delete the signed-in user's data the way the user would: Profile -> "Delete account" -> confirm.
 * That is the app's own deletion path (soft delete + ProfileDeleted event for downstream services).
 * Returns false when the account has no profile, so there is nothing to delete in the app.
 */
export async function deleteAccountInApp(page: Page): Promise<boolean> {
  await page.goto('/profile');
  await think(page, 'read');
  const deleteButton = page.getByRole('button', { name: /^delete account$/i });
  const createProfile = page.getByRole('button', { name: /^create profile$/i });
  await expect(deleteButton.or(createProfile).first()).toBeVisible();
  if (!(await deleteButton.isVisible())) return false;
  page.once('dialog', dialog => dialog.accept());
  await think(page, 'decide');
  await deleteButton.click();
  await page.waitForURL(url => url.origin !== APP_ORIGIN || url.pathname.startsWith('/login'));
  return true;
}

export async function logout(page: Page): Promise<void> {
  await page.goto('/profile');
  await think(page, 'read');
  await page.getByRole('button', { name: /^log ?out$/i }).first().click();
  // keycloak.logout() ends the SSO session and returns to the app, whose guard sends an
  // anonymous visitor straight back to the Keycloak form.
  await page.waitForURL(url => url.origin !== APP_ORIGIN || url.pathname.startsWith('/login'));
  await think(page, 'glance');
}

/**
 * Who the app is acting as right now: the `preferred_username` of the bearer token on the next
 * API call. Asserting on the token rather than on UI text keeps the check valid for accounts that
 * have no profile yet.
 */
export async function expectSignedInAs(page: Page, account: Account): Promise<void> {
  const apiCall = page.waitForRequest(request => /^Bearer /.test(request.headers()['authorization'] ?? ''));
  await page.goto('/profile');
  const token = (await apiCall).headers()['authorization'].slice('Bearer '.length);
  const claims = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
  expect(String(claims.preferred_username ?? claims.email).toLowerCase()).toBe(account.username.toLowerCase());
}
