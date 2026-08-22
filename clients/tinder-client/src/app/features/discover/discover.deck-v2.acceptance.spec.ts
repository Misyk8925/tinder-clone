// @vitest-environment jsdom
import { TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { of } from 'rxjs';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { DeckCard, DeckPage } from '../../core/models/deck.model';
import { ProfileService } from '../../core/services/profile.service';
import { SwipeService } from '../../core/services/swipe.service';
import { DiscoverComponent } from './discover.component';
import { Router } from '@angular/router';

describe('Feature: Discover consumes a changing Deck generation (FR-9)', () => {
  let component: DiscoverComponent;
  const profileService = {
    getMe: vi.fn(),
    getMyDeck: vi.fn(),
  };

  beforeAll(() => {
    try {
      TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
    } catch {
      // ng test already initialized the environment
    }
  });

  beforeEach(() => {
    profileService.getMe.mockReset();
    profileService.getMyDeck.mockReset();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: ProfileService, useValue: profileService },
        { provide: SwipeService, useValue: { swipe: vi.fn() } },
        { provide: Router, useValue: { navigate: vi.fn() } },
      ],
    });
    component = TestBed.runInInjectionContext(() => new DiscoverComponent());
  });

  afterEach(() => {
    component.ngOnDestroy();
    vi.useRealTimers();
  });

  it('Scenario: Given a visible card, when a new generation resets the cursor, then the visible card remains and profile IDs stay unique', () => {
    // Given
    const consumed = card('00000000-0000-0000-0000-000000000001', 'Consumed');
    const current = card('00000000-0000-0000-0000-000000000002', 'Current card');
    const replacement = card(current.profileId, 'New payload must wait');
    const next = card('00000000-0000-0000-0000-000000000003', 'Next');
    component.profiles.set([consumed, current]);
    component.currentIndex.set(1);
    (component as any).generation = 1;

    // When
    (component as any).applyPage(page(2, [replacement, next], true, 'DEGRADED'), false);

    // Then
    expect(component.currentIndex()).toBe(0);
    expect(component.profiles().map(item => item.profileId)).toEqual([current.profileId, next.profileId]);
    expect(component.profiles()[0].name).toBe('Current card');
    expect(component.retrying()).toBe(false);
  });

  it('Scenario: Given an empty building deck, when 30 seconds pass, then polling changes from two seconds to retry-state every ten seconds', () => {
    // Given
    vi.useFakeTimers();
    const startedAt = new Date('2026-08-11T12:00:00Z').getTime();
    vi.setSystemTime(startedAt);
    profileService.getMyDeck.mockReturnValue(of(page(1, [], false, 'EMPTY')));
    (component as any).pollStartedAt = startedAt;

    // When / Then: the first 30 seconds use two-second polling
    (component as any).schedulePoll();
    vi.advanceTimersByTime(1_999);
    expect(profileService.getMyDeck).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(profileService.getMyDeck).toHaveBeenCalledTimes(1);

    // When / Then: after 30 seconds the retry state uses ten-second polling
    vi.setSystemTime(startedAt + 31_000);
    (component as any).schedulePoll();
    expect(component.retrying()).toBe(true);
    vi.advanceTimersByTime(9_999);
    expect(profileService.getMyDeck).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(1);
    expect(profileService.getMyDeck).toHaveBeenCalledTimes(2);
    expect(profileService.getMyDeck).toHaveBeenCalledWith(undefined, 20, false);
  });

  it('Scenario: Given an exhausted deck, when Refresh is clicked, then the client requests a rebuild', () => {
    profileService.getMyDeck.mockReturnValue(of({ state: 'BUILDING', retryAfterSeconds: 2 }));

    component.refresh();

    expect(profileService.getMyDeck).toHaveBeenCalledWith(undefined, 20, true);
  });

  it('Scenario: Given a swipe starts, when the outgoing card commits, then the next card is current and the outgoing card only leaves on its own layer', () => {
    const first = card('00000000-0000-0000-0000-000000000001', 'Ada');
    const second = card('00000000-0000-0000-0000-000000000002', 'Erin');
    const third = card('00000000-0000-0000-0000-000000000003', 'Mia');
    component.profiles.set([first, second, third]);
    component.currentIndex.set(0);

    component.onSwipeCommitted(first);

    expect(component.leavingId()).toBe(first.profileId);
    expect(component.currentIndex()).toBe(1);
    expect(component.visibleProfiles().map(item => item.profileId))
      .toEqual([first.profileId, second.profileId, third.profileId]);
    expect(component.wrapperClass(first, 0)).toBe('leaving');
    expect(component.wrapperClass(second, 1)).toBe('z3');
    expect(component.isFrontCard(first, 0)).toBe(true);
    expect(component.isFrontCard(second, 1)).toBe(true);
    expect(component.isFrontCard(third, 2)).toBe(false);
    expect(component.frontCards().map(item => item.profileId)).toEqual([first.profileId, second.profileId]);
  });

  it('Scenario: Given a stacked deck, when the current card is shown, then the next card is only a blank plate', () => {
    const first = card('00000000-0000-0000-0000-000000000001', 'Ada');
    const second = card('00000000-0000-0000-0000-000000000002', 'Erin');
    const third = card('00000000-0000-0000-0000-000000000003', 'Mia');
    component.profiles.set([first, second, third]);
    component.currentIndex.set(0);

    expect(component.wrapperClass(first, 0)).toBe('z3');
    expect(component.isFrontCard(first, 0)).toBe(true);
    expect(component.wrapperClass(second, 1)).toBe('z2');
    expect(component.isFrontCard(second, 1)).toBe(false);
    expect(component.isFrontCard(third, 2)).toBe(false);
    expect(component.frontCards().map(item => item.profileId)).toEqual([first.profileId]);
  });

  it('Scenario: Given a leaving card, when the swipe is accepted, then the outgoing card is gone and the next card stays current', () => {
    const first = card('00000000-0000-0000-0000-000000000001', 'Ada');
    const second = card('00000000-0000-0000-0000-000000000002', 'Erin');
    component.profiles.set([first, second]);
    component.currentIndex.set(0);

    component.onSwipeCommitted(first);
    (component as any).finishDeparture(first);

    expect(component.leavingId()).toBeNull();
    expect(component.currentIndex()).toBe(1);
    expect(component.visibleProfiles().map(item => item.profileId)).toEqual([second.profileId]);
  });

  it('Scenario: Given a leaving card, when the swipe is rejected, then the outgoing card is restored as current', () => {
    const first = card('00000000-0000-0000-0000-000000000001', 'Ada');
    const second = card('00000000-0000-0000-0000-000000000002', 'Erin');
    component.profiles.set([first, second]);
    component.currentIndex.set(0);

    component.onSwipeCommitted(first);
    (component as any).restoreRejectedSwipe(first);

    expect(component.leavingId()).toBeNull();
    expect(component.currentIndex()).toBe(0);
    expect(component.visibleProfiles()[0].profileId).toBe(first.profileId);
    expect(component.wrapperClass(first, 0)).toBe('z3');
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

function page(
  generation: number,
  items: DeckCard[],
  cursorReset: boolean,
  state: DeckPage['state'],
): DeckPage {
  return { items, nextCursor: null, generation, cursorReset, state };
}
