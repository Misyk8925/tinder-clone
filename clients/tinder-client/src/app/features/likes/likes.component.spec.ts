// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { NEVER, of, throwError } from 'rxjs';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { KeycloakService } from '../../core/services/keycloak.service';
import { LikesService } from '../../core/services/likes.service';
import { ProfileService } from '../../core/services/profile.service';
import { SwipeService } from '../../core/services/swipe.service';
import { SubscriptionService } from '../../core/services/subscription.service';
import { LikesComponent } from './likes.component';

describe('LikesComponent non-premium placeholder', () => {
  const keycloak = { hasPremium: vi.fn(), refreshRoles: vi.fn(() => Promise.resolve()) };
  const likesService = { getLikedMe: vi.fn() };
  const profileService = { getMe: vi.fn(), getProfile: vi.fn() };
  const subscriptionService = { syncEntitlement: vi.fn(), createCheckoutSession: vi.fn() };

  let fixture: ComponentFixture<LikesComponent>;

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
    likesService.getLikedMe.mockReturnValue(of([]));
    subscriptionService.syncEntitlement.mockReturnValue(of({ premium: false }));
    subscriptionService.createCheckoutSession.mockReturnValue(NEVER);

    await TestBed.configureTestingModule({
      imports: [LikesComponent],
      providers: [
        { provide: KeycloakService, useValue: keycloak },
        { provide: LikesService, useValue: likesService },
        { provide: ProfileService, useValue: profileService },
        { provide: SwipeService, useValue: { swipe: vi.fn() } },
        { provide: SubscriptionService, useValue: subscriptionService },
      ],
    }).compileComponents();
  });

  it('Given a non-premium user, when Likes opens with an empty liked-me list, then the purchase placeholder is shown', () => {
    keycloak.hasPremium.mockReturnValue(false);

    fixture = TestBed.createComponent(LikesComponent);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.upgrade-box h2')?.textContent).toContain('See everyone who likes you');
    expect(root.querySelectorAll('.premium-benefits li')).toHaveLength(3);
    expect(root.querySelector('.checkout-note')?.textContent).toContain('Cancel anytime');
    const upgrade = root.querySelector('.btn-upgrade') as HTMLButtonElement;
    expect(upgrade).toBeTruthy();
    upgrade.click();
    expect(subscriptionService.createCheckoutSession).toHaveBeenCalledOnce();
  });

  it('Given a premium user with likes, when Likes opens, then people are presented as profile-first cards', () => {
    keycloak.hasPremium.mockReturnValue(true);
    likesService.getLikedMe.mockReturnValue(of([{
      likerProfileId: 'profile-1',
      likedAt: '2026-08-23T18:00:00Z',
      isSuper: true,
    }]));
    profileService.getProfile.mockReturnValue(of({
      profileId: 'profile-1',
      name: 'Mila',
      age: 24,
      city: 'St. Pölten · 3 km',
      isActive: true,
      photos: [{ url: '/mila.jpg' }],
    }));

    fixture = TestBed.createComponent(LikesComponent);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.likes-grid')).toBeTruthy();
    expect(root.querySelector('.profile-visual img')?.getAttribute('alt')).toBe('Mila');
    expect(root.querySelector('.profile-copy h2')?.textContent).toContain('Mila, 24');
    expect(root.querySelector('.priority-badge')?.textContent).toContain('Priority like');
  });

  it('Given Stripe says Premium is active but the token is stale, when Likes opens, then roles refresh before likes load', async () => {
    keycloak.hasPremium.mockReturnValue(false);
    subscriptionService.syncEntitlement.mockReturnValue(of({ premium: true }));

    fixture = TestBed.createComponent(LikesComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(keycloak.refreshRoles).toHaveBeenCalledOnce();
    expect(likesService.getLikedMe).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelector('.premium-gate')).toBeNull();
    expect(fixture.nativeElement.querySelector('.empty-state')).toBeTruthy();
  });

  it('Given entitlement verification is unavailable, when Likes opens, then it shows recovery instead of an upgrade prompt', () => {
    keycloak.hasPremium.mockReturnValue(false);
    subscriptionService.syncEntitlement.mockReturnValue(throwError(() => new Error('unavailable')));

    fixture = TestBed.createComponent(LikesComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.entitlement-error h2')?.textContent)
      .toContain("couldn't verify your plan");
    expect(fixture.nativeElement.querySelector('.premium-gate')).toBeNull();
  });
});
