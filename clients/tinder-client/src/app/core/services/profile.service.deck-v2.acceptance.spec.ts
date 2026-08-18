import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ProfileService } from './profile.service';

describe('Feature: The client requests generation-aware Deck pages (FR-9)', () => {
  let service: ProfileService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProfileService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('Scenario: Given the first Deck page, when the client requests it, then v2 is used without a v1 offset', () => {
    // Given the initial page has no cursor
    // When
    service.getMyDeck().subscribe();

    // Then
    const request = http.expectOne(req => req.url.endsWith('/api/v2/deck'));
    expect(request.request.method).toBe('GET');
    expect(request.request.params.has('offset')).toBe(false);
    expect(request.request.params.get('limit')).toBe('20');
    request.flush({
      items: [],
      nextCursor: null,
      generation: 1,
      cursorReset: false,
      state: 'EMPTY',
    });
  });

  it('Scenario: Given an opaque next cursor, when the client requests another page, then only that cursor and limit are sent', () => {
    // Given an opaque cursor supplied by Deck Read
    // When
    service.getMyDeck('opaque-cursor', 50).subscribe();

    // Then
    const request = http.expectOne(req => req.url.endsWith('/api/v2/deck'));
    expect(request.request.params.get('cursor')).toBe('opaque-cursor');
    expect(request.request.params.get('limit')).toBe('50');
    expect(request.request.params.has('offset')).toBe(false);
    request.flush({
      items: [],
      nextCursor: null,
      generation: 2,
      cursorReset: false,
      state: 'READY',
    });
  });
});
