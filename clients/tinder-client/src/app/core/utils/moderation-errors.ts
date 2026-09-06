import { HttpErrorResponse } from '@angular/common/http';

export function isContentBlockedError(error: unknown): boolean {
  const http = error as HttpErrorResponse;
  return http?.status === 422 && extractErrorCode(http) === 'CONTENT_BLOCKED';
}

export function moderationFailureMessage(error: unknown, fallback: string): string {
  if (isContentBlockedError(error)) {
    return extractErrorMessage(error as HttpErrorResponse)
      || 'This content was blocked by moderation. Please choose something else.';
  }
  return fallback;
}

function extractErrorCode(error: HttpErrorResponse): string | null {
  const body = error?.error;
  if (body && typeof body === 'object' && 'code' in body && typeof body.code === 'string') {
    return body.code;
  }
  return null;
}

function extractErrorMessage(error: HttpErrorResponse): string | null {
  const body = error?.error;
  if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string') {
    return body.message;
  }
  return null;
}
