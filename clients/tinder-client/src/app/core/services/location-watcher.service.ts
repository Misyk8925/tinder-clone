import { Injectable, OnDestroy } from '@angular/core';
import { EMPTY, Subscription } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { GeoLocationService } from './geo-location.service';
import { ProfileService } from './profile.service';

@Injectable({ providedIn: 'root' })
export class LocationWatcherService implements OnDestroy {
  constructor(
    private readonly geo: GeoLocationService,
    private readonly profiles: ProfileService,
  ) {}

  private sub?: Subscription;

  /**
   * Starts watching the browser's GPS position. Each time the user moves at
   * least `thresholdKm` kilometres from the last reported position, PATCHes
   * the profile with the new coordinates. The backend detects the location
   * change, publishes a LOCATION_CHANGE Kafka event, and the deck service
   * immediately rebuilds the user's deck with candidates near the new location.
   *
   * Call this after the user grants location permission.
   */
  startWatching(thresholdKm = 1.0): void {
    this.sub?.unsubscribe();

    this.sub = this.geo
      .watchPosition$(thresholdKm)
      .pipe(
        switchMap(coords =>
          this.profiles
            .patchProfile({ latitude: coords.latitude, longitude: coords.longitude })
            .pipe(
              catchError(err => {
                console.warn('[LocationWatcher] Failed to sync location to server:', err);
                return EMPTY;
              }),
            ),
        ),
      )
      .subscribe();
  }

  stopWatching(): void {
    this.sub?.unsubscribe();
    this.sub = undefined;
  }

  ngOnDestroy(): void {
    this.stopWatching();
  }
}
