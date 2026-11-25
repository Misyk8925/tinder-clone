import type { Locator, Page } from '@playwright/test';

/**
 * Human pacing for recorded runs.
 *
 * Playwright's slowMo would delay every protocol call by the same amount (twice per key typed);
 * people instead pause to read, hesitate before a decision and type at an uneven speed. These
 * helpers are the run's whole rhythm (slowMo is 0 by default) so the recording
 * is watchable and timing-sensitive UI (debounced inputs, animations) is exercised the way a
 * user would hit it. E2E_PACE=fast shrinks every pause for quick local iteration.
 */
export type PauseKind = 'glance' | 'read' | 'decide';

const fast = process.env.E2E_PACE === 'fast';

const PAUSE_RANGE_MS: Record<PauseKind, [number, number]> = {
  glance: [200, 450],
  read: [600, 1250],
  decide: [400, 900],
};

function between(min: number, max: number): number {
  return Math.round(min + Math.random() * (max - min));
}

export function pauseMs(kind: PauseKind): number {
  const [min, max] = PAUSE_RANGE_MS[kind];
  return fast ? Math.round(min / 10) : between(min, max);
}

/** Wait the way a person does before their next action. */
export async function think(page: Page, kind: PauseKind = 'glance'): Promise<void> {
  await page.waitForTimeout(pauseMs(kind));
}

/** Pause long enough to have read what `locator` shows. */
export async function readingPause(page: Page, locator: Locator): Promise<void> {
  await locator.first().waitFor({ state: 'visible' });
  // TODO(human): scale the pause with how much text the locator shows, instead of the flat
  // 'read' range. Get the text with `await locator.first().innerText()`, turn its length into
  // milliseconds, clamp it, honour `fast`, then `await page.waitForTimeout(ms)`.
  await think(page, 'read');
}

/** Click a field, then type into it key by key at a typing speed. */
export async function typeLikeHuman(locator: Locator, text: string): Promise<void> {
  await locator.click();
  await locator.fill('');
  await locator.pressSequentially(text, { delay: fast ? 5 : between(90, 160) });
}
