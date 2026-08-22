import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const here = dirname(fileURLToPath(import.meta.url));
const iconsSrc = readFileSync(resolve(here, 'lucide-icons.ts'), 'utf8');
const profileSrc = readFileSync(
  resolve(here, '../features/profile/profile.component.ts'),
  'utf8'
);

describe('profile Lucide icon registration', () => {
  it('Given the profile Active badge uses circle, when icons are provided, then Circle is registered', () => {
    expect(profileSrc).toMatch(/name="circle"/);
    expect(iconsSrc).toMatch(/\bCircle\b/);
  });

  it('Given the profile premium row uses crown, when icons are provided, then Crown is registered', () => {
    expect(profileSrc).toMatch(/name="crown"/);
    expect(iconsSrc).toMatch(/\bCrown\b/);
  });
});
