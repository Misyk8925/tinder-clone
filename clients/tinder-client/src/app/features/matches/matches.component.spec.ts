// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { MatchService } from '../../core/services/match.service';
import { ProfileService } from '../../core/services/profile.service';
import { MatchesComponent } from './matches.component';

describe('MatchesComponent new-conversation loading state', () => {
  const router = { navigate: vi.fn() };
  const matchService = {
    getMatches: vi.fn(),
    getMyChats: vi.fn(),
    createConversation: vi.fn(),
  };
  const profileService = { getMe: vi.fn(), getProfile: vi.fn() };

  let fixture: ComponentFixture<MatchesComponent>;

  beforeAll(() => {
    try {
      TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
    } catch {
      // ng test already initialized the environment
    }
  });

  beforeEach(async () => {
    vi.clearAllMocks();
    profileService.getMe.mockReturnValue(of({ profileId: 'me-profile' }));
    profileService.getProfile.mockReturnValue(of({
      profileId: 'new-match-profile',
      name: 'Alex',
      photos: [{ url: '/alex.jpg' }],
    }));
    matchService.getMatches.mockReturnValue(of([{
      id: 'match-1',
      profile1Id: 'me-profile',
      profile2Id: 'new-match-profile',
    }]));
    matchService.getMyChats.mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [MatchesComponent],
      providers: [
        { provide: Router, useValue: router },
        { provide: MatchService, useValue: matchService },
        { provide: ProfileService, useValue: profileService },
      ],
    }).compileComponents();
  });

  afterEach(() => fixture?.destroy());

  it('Given a new match, when its conversation is loading, then the spinner stays inside its avatar until navigation finishes', async () => {
    const conversation = new Subject<{ id: string }>();
    let finishNavigation!: (result: boolean) => void;
    const navigation = new Promise<boolean>(resolve => { finishNavigation = resolve; });
    matchService.createConversation.mockReturnValue(conversation.asObservable());
    router.navigate.mockReturnValue(navigation);

    fixture = TestBed.createComponent(MatchesComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.page-header h1')?.textContent).toContain('Messages');
    expect(fixture.nativeElement.querySelector('.new-match-section h2')?.textContent).toContain('New connections');
    const matchButton = fixture.nativeElement.querySelector('.match-bubble') as HTMLButtonElement;
    matchButton.click();
    fixture.detectChanges();

    const avatar = matchButton.querySelector('.bubble-avatar');
    const spinner = avatar?.querySelector(':scope > .bubble-spinner') as HTMLElement;
    expect(spinner).toBeTruthy();
    expect(getComputedStyle(spinner).display).toBe('grid');
    expect(getComputedStyle(spinner).overflow).toBe('hidden');
    expect(matchButton.getAttribute('aria-busy')).toBe('true');

    conversation.next({ id: 'conversation-1' });
    conversation.complete();
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/chat', 'conversation-1']);
    expect(avatar?.querySelector(':scope > .bubble-spinner')).toBeTruthy();

    finishNavigation(true);
    await navigation;
    await fixture.whenStable();
    fixture.detectChanges();

    expect(matchButton.querySelector('.bubble-spinner')).toBeNull();
    expect(matchButton.getAttribute('aria-busy')).toBe('false');
  });
});
