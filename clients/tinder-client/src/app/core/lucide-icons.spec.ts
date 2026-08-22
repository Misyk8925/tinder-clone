import { describe, expect, it } from 'vitest';
import { APP_LUCIDE_ICONS } from './lucide-icons';

describe('profile Lucide icon registration', () => {
  it('Given the profile Active badge uses circle, when icons are provided, then Circle is registered', () => {
    expect(APP_LUCIDE_ICONS.Circle).toBeDefined();
  });

  it('Given the profile premium row uses crown, when icons are provided, then Crown is registered', () => {
    expect(APP_LUCIDE_ICONS.Crown).toBeDefined();
  });
});
