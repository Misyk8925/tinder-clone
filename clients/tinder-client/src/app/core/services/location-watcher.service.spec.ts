import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, of, throwError } from 'rxjs';
import { LocationWatcherService } from './location-watcher.service';
import { GeoLocationService, GeoCoords } from './geo-location.service';
import { ProfileService } from './profile.service';

describe('LocationWatcherService', () => {
  let service: LocationWatcherService;
  let mockGeo: { watchPosition$: ReturnType<typeof vi.fn> };
  let mockProfiles: { patchProfile: ReturnType<typeof vi.fn> };
  let positionSource$: Subject<GeoCoords>;

  beforeEach(() => {
    positionSource$ = new Subject<GeoCoords>();

    mockGeo = { watchPosition$: vi.fn().mockReturnValue(positionSource$.asObservable()) };
    mockProfiles = { patchProfile: vi.fn().mockReturnValue(of({} as any)) };

    service = new LocationWatcherService(
      mockGeo as unknown as GeoLocationService,
      mockProfiles as unknown as ProfileService,
    );
  });

  it('calls patchProfile when watchPosition$ emits a new location', () => {
    service.startWatching();

    positionSource$.next({ latitude: 52.52, longitude: 13.405 });

    expect(mockProfiles.patchProfile).toHaveBeenCalledWith({
      latitude: 52.52,
      longitude: 13.405,
    });
  });

  it('calls patchProfile for each emitted position', () => {
    service.startWatching();

    positionSource$.next({ latitude: 52.52, longitude: 13.405 });
    positionSource$.next({ latitude: 48.2, longitude: 16.37 });

    expect(mockProfiles.patchProfile).toHaveBeenCalledTimes(2);
  });

  it('does not call patchProfile before startWatching', () => {
    positionSource$.next({ latitude: 52.52, longitude: 13.405 });

    expect(mockProfiles.patchProfile).not.toHaveBeenCalled();
  });

  it('stops calling patchProfile after stopWatching', () => {
    service.startWatching();
    service.stopWatching();

    positionSource$.next({ latitude: 52.52, longitude: 13.405 });

    expect(mockProfiles.patchProfile).not.toHaveBeenCalled();
  });

  it('re-starts cleanly when startWatching is called twice', () => {
    const first$ = new Subject<GeoCoords>();
    const second$ = new Subject<GeoCoords>();

    mockGeo.watchPosition$
      .mockReturnValueOnce(first$.asObservable())
      .mockReturnValueOnce(second$.asObservable());

    service.startWatching();
    service.startWatching(); // unsubscribes first, starts second

    first$.next({ latitude: 0, longitude: 0 });
    expect(mockProfiles.patchProfile).not.toHaveBeenCalled();

    second$.next({ latitude: 52.52, longitude: 13.405 });
    expect(mockProfiles.patchProfile).toHaveBeenCalledTimes(1);
  });

  it('keeps watching after a patchProfile network error', () => {
    mockProfiles.patchProfile
      .mockReturnValueOnce(throwError(() => new Error('network error')))
      .mockReturnValueOnce(of({} as any));

    service.startWatching();

    positionSource$.next({ latitude: 52.52, longitude: 13.405 }); // fails, swallowed
    positionSource$.next({ latitude: 48.2, longitude: 16.37 });   // succeeds

    expect(mockProfiles.patchProfile).toHaveBeenCalledTimes(2);
  });

  it('passes the threshold argument to watchPosition$', () => {
    service.startWatching(5.0);
    expect(mockGeo.watchPosition$).toHaveBeenCalledWith(5.0);
  });
});
