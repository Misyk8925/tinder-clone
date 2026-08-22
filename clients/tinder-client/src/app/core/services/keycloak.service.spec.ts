import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { PREMIUM_REALM_ROLE } from './keycloak.service';

const here = dirname(fileURLToPath(import.meta.url));
const profileSrc = readFileSync(
  resolve(here, '../../features/profile/profile.component.ts'),
  'utf8'
);

describe('premium realm role', () => {
  it('Given Keycloak grants premium, when the Profile UI checks access, then it looks for USER_PREMIUM', () => {
    expect(PREMIUM_REALM_ROLE).toBe('USER_PREMIUM');
    expect(profileSrc).toContain('hasPremium()');
    expect(profileSrc).not.toContain("hasRole('premium')");
  });
});
