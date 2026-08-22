// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LUCIDE_ICONS, LucideIconProvider } from 'lucide-angular';
import { APP_LUCIDE_ICONS } from '../../core/lucide-icons';
import { Profile } from '../../core/models/profile.model';
import { KeycloakService } from '../../core/services/keycloak.service';
import { ProfileService } from '../../core/services/profile.service';
import { SubscriptionService } from '../../core/services/subscription.service';
import { ThemeService } from '../../core/services/theme.service';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent photo loading placeholders', () => {
  let fixture: ComponentFixture<ProfileComponent>;
  let component: ProfileComponent;
  const profileService = {
    getMe: vi.fn(),
    uploadPhoto: vi.fn(),
    deletePhoto: vi.fn(),
    deleteProfile: vi.fn()
  };

  beforeAll(() => {
    try {
      TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
    } catch {
      // ng test already initialized the environment
    }
  });

  beforeEach(async () => {
    vi.clearAllMocks();
    profileService.getMe.mockReturnValue(of(sampleProfile()));
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: ProfileService, useValue: profileService },
        {
          provide: KeycloakService,
          useValue: { hasPremium: vi.fn(() => false), logout: vi.fn(), refreshRoles: vi.fn() }
        },
        {
          provide: SubscriptionService,
          useValue: { syncEntitlement: vi.fn(() => of({ premium: false })) }
        },
        {
          provide: ThemeService,
          useValue: { isDark: () => false, toggle: vi.fn() }
        },
        { provide: LUCIDE_ICONS, multi: true, useValue: new LucideIconProvider(APP_LUCIDE_ICONS) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileComponent);
    component = fixture.componentInstance;
  });

  it('Given a profile photo has not loaded, when Profile renders, then a placeholder is shown', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.photo-hero .photo-skeleton')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.photo-hero img')).toBeTruthy();
  });

  it('Given a profile photo placeholder, when the image loads, then the photo is shown without the skeleton', () => {
    fixture.detectChanges();

    const img = fixture.nativeElement.querySelector('.photo-hero img') as HTMLImageElement;
    expect(img.getAttribute('data-photo-id')).toBe('p1');
    img.dispatchEvent(new Event('load'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.photo-hero .photo-skeleton')).toBeNull();
    expect(img.classList.contains('ready')).toBe(true);
  });

  it('Given a profile photo that is already complete, when rendered, then the skeleton is gone', () => {
    fixture.detectChanges();

    const img = fixture.nativeElement.querySelector('.photo-hero img') as HTMLImageElement;
    Object.defineProperty(img, 'complete', { configurable: true, value: true });
    Object.defineProperty(img, 'naturalWidth', { configurable: true, value: 120 });
    component.ngAfterViewChecked();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.photo-hero .photo-skeleton')).toBeNull();
    expect(img.classList.contains('ready')).toBe(true);
  });
});

function sampleProfile(): Profile {
  return {
    profileId: 'me',
    name: 'Alex',
    age: 28,
    gender: 'other',
    bio: '',
    city: 'Berlin',
    isActive: true,
    isDeleted: false,
    preferences: { minAge: 21, maxAge: 35, gender: 'all', maxRange: 50 },
    photos: [{ photoId: 'p1', url: 'https://cdn.example/hero.jpg', position: 0 }],
    hobbies: []
  };
}
