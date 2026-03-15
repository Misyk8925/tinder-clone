import { Injectable } from '@angular/core';

export interface GeoCoords {
  latitude: number;
  longitude: number;
}

@Injectable({ providedIn: 'root' })
export class GeoLocationService {
  private readonly SKIPPED_KEY = 'locationPermissionSkipped';
  private readonly COORDS_KEY = 'locationCoords';

  /** User explicitly chose to proceed without location (from idle state, never denied). */
  hasSkipped(): boolean {
    return localStorage.getItem(this.SKIPPED_KEY) === 'true';
  }

  markSkipped(): void {
    localStorage.setItem(this.SKIPPED_KEY, 'true');
  }

  getCoords(): GeoCoords | null {
    const raw = localStorage.getItem(this.COORDS_KEY);
    return raw ? JSON.parse(raw) : null;
  }

  setCoords(coords: GeoCoords): void {
    localStorage.setItem(this.COORDS_KEY, JSON.stringify(coords));
  }

  hasCoords(): boolean {
    return this.getCoords() !== null;
  }

  /**
   * Requests GPS permission.
   * On success: stores coords and resolves with coords.
   * On browser/OS denial: resolves with null so the user can retry after enabling in device settings.
   */
  requestPermission(): Promise<GeoCoords | null> {
    return new Promise((resolve) => {
      if (!navigator.geolocation) {
        resolve(null);
        return;
      }
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const coords: GeoCoords = {
            latitude: pos.coords.latitude,
            longitude: pos.coords.longitude,
          };
          this.setCoords(coords);
          resolve(coords);
        },
        () => {
          // Do NOT mark as asked — user can retry after enabling in device settings
          resolve(null);
        },
        { timeout: 10000 }
      );
    });
  }
}
