// @vitest-environment jsdom
import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { designPreviewInterceptor, resetDesignPreviewFixtures } from './design-preview.interceptor';

describe('design preview interceptor content events', () => {
  let http: HttpClient;
  const previousPreview = environment.designPreview;

  beforeEach(() => {
    environment.designPreview = true;
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([designPreviewInterceptor]))]
    });
    http = TestBed.inject(HttpClient);
  });

  afterEach(() => {
    resetDesignPreviewFixtures();
    environment.designPreview = previousPreview;
  });

  it('Given a hate bio on profile save, when preview posts the profile, then the client sees CONTENT_BLOCKED', async () => {
    const error = await firstValueFrom(
      http.put('https://preview.test/api/v1/profiles', {
        name: 'Michael',
        bio: 'I hate all outsiders and they should die'
      })
    ).then(
      () => null,
      (err: unknown) => err
    ) as HttpErrorResponse;

    expect(error).toBeInstanceOf(HttpErrorResponse);
    expect(error.status).toBe(422);
    expect(error.error).toEqual({
      code: 'CONTENT_BLOCKED',
      message: 'This content was blocked by moderation. Please choose different wording.'
    });
  });

  it('Given a clean bio, when preview saves the profile, then the write is accepted', async () => {
    const response = await firstValueFrom(
      http.put('https://preview.test/api/v1/profiles', {
        name: 'Michael',
        bio: 'A good conversation and a long walk are a strong start.'
      })
    );

    expect(response).toEqual({ message: 'saved', id: 'preview-me' });
  });

  it('Given a clean bio save, when the profile page reloads /me, then the new bio is returned', async () => {
    await firstValueFrom(
      http.put('https://preview.test/api/v1/profiles', {
        name: 'Michael',
        bio: 'Coffee, a trail, then live music.'
      })
    );

    const me = await firstValueFrom(
      http.get<{ bio: string }>('https://preview.test/api/v1/profiles/me')
    );

    expect(me.bio).toBe('Coffee, a trail, then live music.');
  });

  it('Given a blocked bio, when /me is read again, then the previous bio is unchanged', async () => {
    await firstValueFrom(
      http.put('https://preview.test/api/v1/profiles', {
        name: 'Michael',
        bio: 'I hate all outsiders and they should die'
      })
    ).catch(() => undefined);

    const me = await firstValueFrom(
      http.get<{ bio: string }>('https://preview.test/api/v1/profiles/me')
    );

    expect(me.bio).toBe('A good conversation and a long walk are a strong start.');
  });
});
