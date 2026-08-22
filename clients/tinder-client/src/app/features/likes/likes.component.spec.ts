// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { KeycloakService } from '../../core/services/keycloak.service';
import { LikesService } from '../../core/services/likes.service';
import { ProfileService } from '../../core/services/profile.service';
import { SwipeService } from '../../core/services/swipe.service';
import { LikesComponent } from './likes.component';

describe('LikesComponent non-premium placeholder', () => {
  const router = { navigate: vi.fn() };
  const keycloak = { hasPremium: vi.fn() };
  const likesService = { getLikedMe: vi.fn() };
  const profileService = { getMe: vi.fn(), getProfile: vi.fn() };

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

    await TestBed.configureTestingModule({
      imports: [LikesComponent],
      providers: [
        { provide: Router, useValue: router },
        { provide: KeycloakService, useValue: keycloak },
        { provide: LikesService, useValue: likesService },
        { provide: ProfileService, useValue: profileService },
        { provide: SwipeService, useValue: { swipe: vi.fn() } },
      ],
    }).compileComponents();
  });

  it('Given a non-premium user, when Likes opens with an empty liked-me list, then the purchase placeholder is shown', () => {
    keycloak.hasPremium.mockReturnValue(false);

    fixture = TestBed.createComponent(LikesComponent);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.upgrade-box h2')?.textContent).toContain('See Who Likes You');
    const upgrade = root.querySelector('.btn-upgrade') as HTMLButtonElement;
    expect(upgrade).toBeTruthy();
    upgrade.click();
    expect(router.navigate).toHaveBeenCalledWith(['/profile']);
  });
});
