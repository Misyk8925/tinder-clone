// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { DeckCard } from '../../core/models/deck.model';
import { ProfileService } from '../../core/services/profile.service';
import { SwipeService } from '../../core/services/swipe.service';
import { MatchService } from '../../core/services/match.service';
import { ReportService } from '../../core/services/report.service';
import { DiscoverComponent } from './discover.component';
import { Router } from '@angular/router';

describe('Feature: Discover Rewind button and HTTP client contract (FR-10, P2.5, P2.7)', () => {
  beforeAll(() => {
    try {
      TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
    } catch {
      // ng test already initialized the environment
    }
  });

  describe('SwipeService HTTP contract', () => {
    let service: SwipeService;
    let http: HttpTestingController;

    beforeEach(() => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [provideHttpClient(), provideHttpClientTesting()],
      });
      service = TestBed.inject(SwipeService);
      http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('Scenario: Given a rewind trigger, when SwipeService.rewind is called, then POST /api/v1/swipes/rewind is sent', () => {
      // Given
      expect(typeof (service as any).rewind).toBe('function');

      // When
      (service as any).rewind().subscribe();

      // Then
      const req = http.expectOne(r => r.url.endsWith('/api/v1/swipes/rewind'));
      expect(req.request.method).toBe('POST');
      req.flush({
        revokedSwipeEventId: '8c64d80e-4d5b-4df3-8b1e-1d8a3f9520b4',
        revocationEventId: '1f92a35c-7c21-4e7b-9d2c-6a018723cb11',
        profile2Id: '00000000-0000-0000-0000-000000000001',
        decision: false,
        revokedAt: '2026-11-25T21:55:12.410Z',
        alreadyRevoked: false,
      });
    });
  });

  describe('DiscoverComponent Rewind states and error handling', () => {
    let component: DiscoverComponent;
    const profileService = {
      getMe: vi.fn(),
      getMyDeck: vi.fn(),
    };
    const swipeService = {
      swipe: vi.fn(),
      rewind: vi.fn(),
    };

    beforeEach(() => {
      profileService.getMe.mockReset();
      profileService.getMyDeck.mockReset();
      swipeService.swipe.mockReset();
      swipeService.rewind.mockReset();
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          { provide: ProfileService, useValue: profileService },
          { provide: SwipeService, useValue: swipeService },
          { provide: MatchService, useValue: { getMatches: vi.fn(() => of([])), createConversation: vi.fn() } },
          { provide: ReportService, useValue: { reportProfile: vi.fn() } },
          { provide: Router, useValue: { navigate: vi.fn() } },
        ],
      });
      component = TestBed.runInInjectionContext(() => new DiscoverComponent());
      (component as any).myProfileId = '00000000-0000-0000-0000-000000000099';
    });

    afterEach(() => {
      component.ngOnDestroy();
      vi.useRealTimers();
    });

    it('Scenario: Given no swipe was made yet (disabled-no-swipe), when Discover renders, then canRewind() is false', () => {
      // Given / When / Then
      expect(typeof (component as any).canRewind).toBe('function');
      expect((component as any).canRewind()).toBe(false);
    });

    it('Scenario: Given a recent swipe succeeded, when the user clicks Rewind and server returns 200 OK, then the card returns to position 0 immediately', () => {
      // Given
      const first = card('00000000-0000-0000-0000-000000000001', 'Ada');
      const second = card('00000000-0000-0000-0000-000000000002', 'Erin');
      component.profiles.set([first, second]);
      component.currentIndex.set(0);
      swipeService.swipe.mockReturnValue(of(void 0));
      swipeService.rewind.mockReturnValue(of({
        revokedSwipeEventId: '8c64d80e-4d5b-4df3-8b1e-1d8a3f9520b4',
        revocationEventId: '1f92a35c-7c21-4e7b-9d2c-6a018723cb11',
        profile2Id: first.profileId,
        decision: false,
        revokedAt: '2026-11-25T21:55:12.410Z',
        alreadyRevoked: false,
      }));

      // When
      component.onSwipeCommitted(first);
      component.onSwipe('left', first);
      expect(typeof (component as any).canRewind).toBe('function');
      expect((component as any).canRewind()).toBe(true);
      (component as any).rewindLastSwipe();

      // Then
      expect(swipeService.rewind).toHaveBeenCalledTimes(1);
      expect(component.profiles()[component.currentIndex()]?.profileId).toBe(first.profileId);
      expect((component as any).canRewind()).toBe(false);
    });

    it('Scenario: Given the last swipe emptied the deck (empty-deck-rewindable), when Rewind succeeds, then the swiped profile returns as current card', () => {
      // Given
      const only = card('00000000-0000-0000-0000-000000000001', 'Ada');
      component.profiles.set([only]);
      component.currentIndex.set(0);
      swipeService.swipe.mockReturnValue(of(void 0));
      swipeService.rewind.mockReturnValue(of({
        revokedSwipeEventId: '8c64d80e-4d5b-4df3-8b1e-1d8a3f9520b4',
        revocationEventId: '1f92a35c-7c21-4e7b-9d2c-6a018723cb11',
        profile2Id: only.profileId,
        decision: true,
        revokedAt: '2026-11-25T21:55:12.410Z',
        alreadyRevoked: false,
      }));

      // When
      component.onSwipeCommitted(only);
      component.onSwipe('right', only);
      expect(component.profiles()[component.currentIndex()]).toBeUndefined();
      expect(typeof (component as any).canRewind).toBe('function');
      expect((component as any).canRewind()).toBe(true);
      (component as any).rewindLastSwipe();

      // Then
      expect(component.profiles()[component.currentIndex()]?.profileId).toBe(only.profileId);
    });

    it.each([
      ['REWIND_WINDOW_EXPIRED', 409, false],
      ['SWIPE_ALREADY_MATCHED', 409, false],
      ['NOT_LATEST_SWIPE', 409, false],
      ['NO_SWIPE_TO_REWIND', 404, false],
      ['RATE_LIMITED', 429, true],
      ['SERVICE_UNAVAILABLE', 503, true],
      ['UNAUTHENTICATED', 401, false],
      ['PROFILE_OWNERSHIP_DENIED', 403, false],
    ] as const)(
      'Scenario: Given server rejects Rewind with %s (%d), when rewindLastSwipe runs, then a user-facing toast is shown and canRewind=%s',
      (code, status, expectedCanRewindAfter) => {
        // Given
        const first = card('00000000-0000-0000-0000-000000000001', 'Ada');
        const second = card('00000000-0000-0000-0000-000000000002', 'Erin');
        component.profiles.set([first, second]);
        component.currentIndex.set(0);
        swipeService.swipe.mockReturnValue(of(void 0));
        swipeService.rewind.mockReturnValue(
          throwError(() => new HttpErrorResponse({ status, error: { code, message: 'err' } })),
        );

        // When
        component.onSwipeCommitted(first);
        component.onSwipe('left', first);
        expect(typeof (component as any).rewindLastSwipe).toBe('function');
        (component as any).rewindLastSwipe();

        // Then
        expect(component.toast()).toBeTruthy();
        expect((component as any).canRewind()).toBe(expectedCanRewindAfter);
      },
    );
  });
});

function card(profileId: string, name: string): DeckCard {
  return {
    profileId,
    name,
    age: 29,
    city: 'Vienna',
    bio: 'bio',
    isActive: true,
    preferences: { minAge: 18, maxAge: 99, gender: 'ALL', maxDistanceKm: 50 },
    photos: [],
    hobbies: [],
  };
}
