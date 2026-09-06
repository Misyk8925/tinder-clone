import { describe, expect, it } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { isContentBlockedError, moderationFailureMessage } from './moderation-errors';

describe('moderation errors', () => {
  it('Given a 422 CONTENT_BLOCKED response, when mapped, then the user sees the blocked copy', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: { code: 'CONTENT_BLOCKED', message: 'This content was blocked by moderation.' }
    });

    expect(isContentBlockedError(error)).toBe(true);
    expect(moderationFailureMessage(error, 'Failed')).toBe('This content was blocked by moderation.');
  });

  it('Given a generic failure, when mapped, then the fallback is used', () => {
    const error = new HttpErrorResponse({ status: 500, error: { code: 'INTERNAL_ERROR' } });

    expect(isContentBlockedError(error)).toBe(false);
    expect(moderationFailureMessage(error, 'Failed to save profile. Please try again.'))
      .toBe('Failed to save profile. Please try again.');
  });
});
