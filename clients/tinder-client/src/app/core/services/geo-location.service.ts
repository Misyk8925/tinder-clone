import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

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
   * Emits whenever the user moves at least `thresholdKm` kilometres from the
   * last emitted position. Uses the browser Geolocation watchPosition API.
   * The returned Observable clears the watch on unsubscribe.
   *
   * Seeded from the last stored coords so GPS jitter right after app launch
   * (which may be within the threshold of the stored value) is suppressed.
   */
  watchPosition$(thresholdKm = 1.0): Observable<GeoCoords> {
    return new Observable<GeoCoords>(observer => {
      if (!navigator.geolocation) {
        observer.complete();
        return;
      }

      let lastEmitted: GeoCoords | null = this.getCoords();

      const watchId = navigator.geolocation.watchPosition(
        pos => {
          const next: GeoCoords = {
            latitude: pos.coords.latitude,
            longitude: pos.coords.longitude,
          };
          if (lastEmitted === null || this.haversineKm(lastEmitted, next) >= thresholdKm) {
            lastEmitted = next;
            this.setCoords(next);
            observer.next(next);
          }
        },
        err => observer.error(err),
        { enableHighAccuracy: false, maximumAge: 60_000 },
      );

      return () => navigator.geolocation.clearWatch(watchId);
    });
  }

  haversineKm(a: GeoCoords, b: GeoCoords): number {
    const R = 6371;
    const dLat = (b.latitude - a.latitude) * (Math.PI / 180);
    const dLon = (b.longitude - a.longitude) * (Math.PI / 180);
    const lat1 = a.latitude * (Math.PI / 180);
    const lat2 = b.latitude * (Math.PI / 180);
    const x =
      Math.sin(dLat / 2) ** 2 +
      Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
    return R * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x));
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
