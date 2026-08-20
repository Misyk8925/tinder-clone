// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { LUCIDE_ICONS, LucideIconProvider, ShieldCheck } from 'lucide-angular';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { GeoLocationService } from '../../core/services/geo-location.service';
import { LocationPermissionComponent } from './location-permission.component';

describe('LocationPermissionComponent recovery', () => {
  const router = { navigate: vi.fn() };
  const geo = {
    hasCoords: vi.fn(),
    hasSkipped: vi.fn(),
    markSkipped: vi.fn(),
    requestPermission: vi.fn(),
  };

  let fixture: ComponentFixture<LocationPermissionComponent>;
  let component: LocationPermissionComponent;

  beforeEach(() => {
    vi.clearAllMocks();
    geo.hasCoords.mockReturnValue(false);
    geo.hasSkipped.mockReturnValue(false);

    TestBed.configureTestingModule({
      imports: [LocationPermissionComponent],
      providers: [
        { provide: Router, useValue: router },
        { provide: GeoLocationService, useValue: geo },
        {
          provide: LUCIDE_ICONS,
          multi: true,
          useValue: new LucideIconProvider({ ShieldCheck }),
        },
      ],
    });
    fixture = TestBed.createComponent(LocationPermissionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('Given location was denied, when the user continues without it, then the choice is persisted before Discover navigation', () => {
    component.status.set('denied');
    fixture.detectChanges();

    const skipButton = fixture.nativeElement.querySelector('.btn-skip') as HTMLButtonElement;
    skipButton.click();

    expect(geo.markSkipped).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/discover']);
  });

  it('Given location is allowed but unavailable, when the request times out, then retry remains available without claiming permission is denied', async () => {
    geo.requestPermission.mockResolvedValue({ coords: null, error: 'timeout' });

    await component.allow();
    fixture.detectChanges();

    expect(component.status()).toBe('unavailable');
    expect(router.navigate).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain("We couldn't get your location.");
    expect((fixture.nativeElement.querySelector('.btn-allow') as HTMLButtonElement).disabled).toBe(false);
  });
});
