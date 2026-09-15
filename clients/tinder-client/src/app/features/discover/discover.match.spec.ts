// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { Router } from '@angular/router';
import { LUCIDE_ICONS, LucideIconProvider } from 'lucide-angular';
import { of, throwError } from 'rxjs';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { DeckCard } from '../../core/models/deck.model';
import { Profile } from '../../core/models/profile.model';
import { APP_LUCIDE_ICONS } from '../../core/lucide-icons';
import { MatchService } from '../../core/services/match.service';
import { ProfileService } from '../../core/services/profile.service';
import { ReportService } from '../../core/services/report.service';
import { SwipeService } from '../../core/services/swipe.service';
import { DiscoverComponent } from './discover.component';

describe('Feature: Discover shows It’s a match after a mutual like', () => {
  let component: DiscoverComponent;
  let fixture: ComponentFixture<DiscoverComponent> | null;
  const router = { navigate: vi.fn() };
  const swipeService = { swipe: vi.fn() };
  const matchService = {
    getMatches: vi.fn(),
    createConversation: vi.fn(),
  };
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
    fixture = null;
    router.navigate.mockReset();
    swipeService.swipe.mockReset();
    matchService.getMatches.mockReset();
    matchService.createConversation.mockReset();
    profileService.getMe.mockReset();
    profileService.getMyDeck.mockReset();
    swipeService.swipe.mockReturnValue(of(null));
    matchService.getMatches.mockReturnValue(of([]));
    matchService.createConversation.mockReturnValue(of({
      id: 'chat-1',
      participant1Id: 'me',
      participant2Id: 'preview-mila',
      createdAt: '',
      messages: [],
    }));
    profileService.getMe.mockReturnValue(of(meProfile()));
    profileService.getMyDeck.mockReturnValue(of({
      items: [],
      nextCursor: null,
      generation: 1,
      cursorReset: false,
      state: 'READY',
    }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [DiscoverComponent],
      providers: [
        { provide: ProfileService, useValue: profileService },
        { provide: SwipeService, useValue: swipeService },
        { provide: MatchService, useValue: matchService },
        { provide: ReportService, useValue: { reportProfile: vi.fn() } },
        { provide: Router, useValue: router },
        { provide: LUCIDE_ICONS, multi: true, useValue: new LucideIconProvider(APP_LUCIDE_ICONS) },
      ],
    });
    component = TestBed.runInInjectionContext(() => new DiscoverComponent());
  });

  afterEach(() => {
    component.ngOnDestroy();
    fixture?.destroy();
    vi.useRealTimers();
  });

  it('Scenario: Given a successful like, when matches include that profile, then the overlay names them', () => {
    // Given
    vi.useFakeTimers();
    rememberMe(component);
    const mila = card('preview-mila', 'Mila');
    matchService.getMatches.mockReturnValue(of([
      { id: 'm1', profile1Id: 'me', profile2Id: mila.profileId, matchedAt: '2026-09-15T12:00:00Z' },
    ]));

    // When
    component.onSwipe('right', mila);
    vi.advanceTimersByTime(1);

    // Then
    expect(component.matchedProfile()?.name).toBe('Mila');
  });

  it('Scenario: Given a named match, when the overlay renders, then both faces and Send a message are visible', () => {
    fixture = TestBed.createComponent(DiscoverComponent);
    component = fixture.componentInstance;
    rememberMe(component);
    component.loading.set(false);
    component.matchedProfile.set(card('preview-mila', 'Mila'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#match-title')?.textContent).toContain('You and Mila liked each other');
    expect(fixture.nativeElement.textContent).toContain("It's a match");
    expect(fixture.nativeElement.querySelector('.match-faces')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.primary-button.wide')?.textContent).toContain('Send a message');
    const faces = [...fixture.nativeElement.querySelectorAll('.match-photo img')] as HTMLImageElement[];
    expect(faces.map(img => img.getAttribute('src'))).toEqual(['/me.png', '/them.png']);
  });

  it('Scenario: Given a successful like, when the match is not in the first poll, then the overlay waits until it appears', () => {
    // Given
    vi.useFakeTimers();
    rememberMe(component);
    const mila = card('preview-mila', 'Mila');
    matchService.getMatches
      .mockReturnValueOnce(of([]))
      .mockReturnValueOnce(of([
        { id: 'm1', profile1Id: mila.profileId, profile2Id: 'me', matchedAt: '2026-09-15T12:00:00Z' },
      ]));

    // When
    component.onSwipe('right', mila);
    vi.advanceTimersByTime(1);

    // Then
    expect(component.matchedProfile()).toBeNull();
    vi.advanceTimersByTime(400);
    expect(component.matchedProfile()?.profileId).toBe(mila.profileId);
    expect(matchService.getMatches).toHaveBeenCalledTimes(2);
  });

  it('Scenario: Given a successful like, when matches belong to someone else, then Discover stays quiet', () => {
    vi.useFakeTimers();
    rememberMe(component);
    matchService.getMatches.mockReturnValue(of([
      { id: 'other', profile1Id: 'me', profile2Id: 'preview-nora', matchedAt: '2026-09-15T12:00:00Z' },
    ]));

    component.onSwipe('right', card('preview-mila', 'Mila'));
    vi.advanceTimersByTime(4000);

    expect(component.matchedProfile()).toBeNull();
  });

  it('Scenario: Given a pass, when the swipe succeeds, then matches are not polled', () => {
    vi.useFakeTimers();
    rememberMe(component);

    component.onSwipe('left', card('preview-mila', 'Mila'));
    vi.advanceTimersByTime(4000);

    expect(matchService.getMatches).not.toHaveBeenCalled();
    expect(component.matchedProfile()).toBeNull();
  });

  it('Scenario: Given the overlay, when Send a message succeeds, then chat opens for that match', () => {
    rememberMe(component);
    component.matchedProfile.set(card('preview-mila', 'Mila'));

    component.goToMatchChat();

    expect(component.openingMatchChat()).toBe(true);
    expect(matchService.createConversation).toHaveBeenCalledWith('me', 'preview-mila');
    expect(router.navigate).toHaveBeenCalledWith(['/chat', 'chat-1']);
  });

  it('Scenario: Given the overlay, when opening chat fails, then Matches is the fallback', () => {
    rememberMe(component);
    component.matchedProfile.set(card('preview-mila', 'Mila'));
    matchService.createConversation.mockReturnValue(throwError(() => new Error('unavailable')));

    component.goToMatchChat();

    expect(component.openingMatchChat()).toBe(false);
    expect(component.matchedProfile()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/matches']);
  });
});

function rememberMe(component: DiscoverComponent): void {
  (component as unknown as { rememberMe(profile: Profile): void }).rememberMe(meProfile());
}

function meProfile(): Profile {
  return {
    profileId: 'me',
    name: 'Alex',
    age: 27,
    gender: 'male',
    bio: 'Hello',
    city: 'Vienna',
    isActive: true,
    isDeleted: false,
    preferences: { minAge: 22, maxAge: 34, gender: 'all', maxRange: 50 },
    photos: [{ photoId: 'me-photo', url: '/me.png', position: 0, isPrimary: true }],
    hobbies: [],
  };
}

function card(profileId: string, name: string): DeckCard {
  return {
    profileId,
    name,
    age: 24,
    city: 'Vienna',
    bio: 'Coffee, a trail, then live music.',
    isActive: true,
    preferences: { minAge: 18, maxAge: 99, gender: 'ALL', maxDistanceKm: 50 },
    photos: [{ photoId: 'p1', url: '/them.png', order: 0 }],
    hobbies: [],
  };
}
