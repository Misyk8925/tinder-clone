import { defineConfig, devices } from '@playwright/test';

/**
 * Recorded, human-paced browser tests.
 *
 * Every test is recorded in full (video + trace + final screenshot) because these runs are the
 * evidence attached to a pull request, not just a pass/fail signal. Runs go through
 * `e2e-record --pr <n>` on the VPS, which sets E2E_OUTPUT_DIR and archives the result.
 */
const outputRoot = process.env.E2E_OUTPUT_DIR ?? 'out';
// Pacing comes from three places; keep them in mind together when tuning speed:
//  - slowMo: added to every protocol input event, so typing pays it twice per key (down + up).
//    Off by default; human rhythm comes from support/human.ts instead.
//  - video.show.actions.duration: the video overlay holds every action this long.
//  - support/human.ts: think() pauses and per-key typing delay.
const slowMo = Number(process.env.E2E_SLOWMO_MS ?? 0);
const viewport = { width: 1280, height: 800 };

/**
 * E2E_ACCOUNTS (set by `e2e-record --accounts`) chains account lifecycle projects in front of the
 * regular tests, so a single recorded run can delete the test users, register them again through
 * the sign-up form and then exercise the app with them:
 *   keep      tests only
 *   delete    accounts-delete
 *   recreate  accounts-delete -> accounts-register
 *   fresh     accounts-delete -> accounts-register -> tests
 */
const accountsMode = process.env.E2E_ACCOUNTS ?? 'keep';
const runDelete = accountsMode !== 'keep';
const runRegister = accountsMode === 'recreate' || accountsMode === 'fresh';
const runTests = accountsMode === 'keep' || accountsMode === 'fresh';
const desktop = { ...devices['Desktop Chrome'], viewport };

export default defineConfig({
  testDir: './tests',
  // One browser at a time: the recording reads like a person using the app, and the VPS sandbox
  // (3 GB / 3 CPU shared by all agents) is not a place for parallel Chromiums.
  workers: 1,
  fullyParallel: false,
  retries: 0,
  forbidOnly: true,
  timeout: 5 * 60_000,
  expect: { timeout: 20_000 },
  outputDir: `${outputRoot}/test-results`,
  reporter: [
    ['list'],
    ['html', { outputFolder: `${outputRoot}/report`, open: 'never' }],
    ['json', { outputFile: `${outputRoot}/results.json` }],
  ],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'https://lunari.misyk.tech',
    video: {
      mode: 'on',
      size: viewport,
      // Headless video has no mouse pointer; let Playwright draw the cursor and name each action,
      // and caption the current test step so a reviewer can follow the run without the trace.
      show: {
        actions: { cursor: 'pointer', position: 'top-right', duration: 250 },
        test: { level: 'step', position: 'bottom' },
      },
    },
    trace: 'on',
    screenshot: 'on',
    launchOptions: { slowMo },
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
    locale: 'en-US',
    timezoneId: 'Europe/Berlin',
    // The app sends users to /location-permission without coordinates; a real phone would share them.
    geolocation: { latitude: 52.52, longitude: 13.405 },
    permissions: ['geolocation'],
  },
  projects: [
    ...(runDelete
      ? [{ name: 'accounts-delete', testMatch: /accounts[\\/]delete\.accounts\.ts$/, use: desktop }]
      : []),
    ...(runRegister
      ? [{
          name: 'accounts-register',
          testMatch: /accounts[\\/]register\.accounts\.ts$/,
          dependencies: ['accounts-delete'],
          use: desktop,
        }]
      : []),
    ...(runTests
      ? [{
          name: 'chromium-desktop',
          testIgnore: /accounts[\\/]/,
          dependencies: runRegister ? ['accounts-register'] : [],
          use: desktop,
        }]
      : []),
  ],
});
