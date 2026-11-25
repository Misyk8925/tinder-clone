import { test as base, type Page } from '@playwright/test';

/**
 * `openSession(label)` gives a test a second, fully separate browser session (own cookies and
 * Keycloak SSO session), for scenarios where two accounts are online at once.
 *
 * Playwright Test applies the config's context options and tracing to any context a test creates,
 * but it only records video for the built-in `page`/`context`. So extra sessions request video
 * themselves and attach it to the report when the test ends.
 */
type Fixtures = {
  openSession: (label: string) => Promise<Page>;
};

export const test = base.extend<Fixtures>({
  openSession: async ({ browser, viewport }, use, testInfo) => {
    const sessions: { label: string; page: Page }[] = [];

    await use(async (label: string) => {
      const context = await browser.newContext({
        recordVideo: {
          dir: testInfo.outputPath(`session-${label}`),
          size: viewport ?? undefined,
        },
      });
      const page = await context.newPage();
      sessions.push({ label, page });
      return page;
    });

    for (const { label, page } of sessions) {
      const video = page.video();
      await page.context().close();
      if (video) {
        await testInfo.attach(`video (${label})`, { path: await video.path(), contentType: 'video/webm' });
      }
    }
  },
});

export { expect } from '@playwright/test';
