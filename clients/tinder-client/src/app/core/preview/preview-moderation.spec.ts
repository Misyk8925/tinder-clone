import { describe, expect, it } from 'vitest';
import { previewTextBlocked } from './preview-moderation';

describe('preview text moderation', () => {
  it('Given a normal bio or message, when checked, then it is allowed', () => {
    expect(previewTextBlocked('Coffee, a trail, then live music.')).toBe(false);
    expect(previewTextBlocked('That trail sounds perfect. Saturday?')).toBe(false);
  });

  it('Given a hate profile bio, when checked, then it is blocked', () => {
    expect(previewTextBlocked('I hate all outsiders and they should die')).toBe(true);
  });

  it('Given a harassment chat message, when checked, then it is blocked', () => {
    expect(previewTextBlocked('kill yourself')).toBe(true);
  });
});
