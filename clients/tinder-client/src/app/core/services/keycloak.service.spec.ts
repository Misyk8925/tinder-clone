import { describe, expect, it } from 'vitest';
import { PREMIUM_REALM_ROLE } from './keycloak.service';

describe('premium realm role', () => {
  it('Given Keycloak grants premium, when the Profile UI checks access, then it looks for USER_PREMIUM', () => {
    expect(PREMIUM_REALM_ROLE).toBe('USER_PREMIUM');
  });
});
