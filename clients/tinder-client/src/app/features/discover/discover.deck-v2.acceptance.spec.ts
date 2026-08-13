import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

  beforeEach(() => {
    profileService.getMe.mockReset();
    profileService.getMyDeck.mockReset();
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
