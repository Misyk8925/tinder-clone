import { randomUUID } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Ask root's e2e-accounts-reset service to delete the test users from Keycloak.
 *
 * The agent has no Keycloak admin rights. e2e-record mounts ~/e2e-requests at E2E_REQUESTS_DIR;
 * an empty <nonce>.req there is the whole request, and the service answers with <nonce>.done.
 * Which users get deleted is fixed on the root side (allowlist + @lunari.test e-mail).
 */
export type ResetAnswer = { ok: boolean; accounts?: Record<string, string>; error?: string };

export async function deleteTestUsersFromKeycloak(timeoutMs = 90_000): Promise<ResetAnswer> {
  const dir = process.env.E2E_REQUESTS_DIR;
  if (!dir) throw new Error('E2E_REQUESTS_DIR is not set; run through e2e-record --accounts ...');
  const nonce = randomUUID();
  writeFileSync(join(dir, `${nonce}.req`), '');
  const done = join(dir, `${nonce}.done`);
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (existsSync(done)) return JSON.parse(readFileSync(done, 'utf8')) as ResetAnswer;
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error(`no answer from e2e-accounts-reset within ${timeoutMs / 1000}s`);
}
