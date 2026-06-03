// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { GeoLocationService, GeoCoords } from './geo-location.service';

function fakePos(latitude: number, longitude: number): GeolocationPosition {
  return { coords: { latitude, longitude } } as GeolocationPosition;
}

function mockGeolocation() {
  const watchPosition = vi.fn();
  const clearWatch = vi.fn();
  Object.defineProperty(navigator, 'geolocation', {
    value: { watchPosition, clearWatch },
    configurable: true,
    writable: true,
  });
  return { watchPosition, clearWatch };
}

describe('GeoLocationService', () => {
  let service: GeoLocationService;

  beforeEach(() => {
    service = new GeoLocationService();
    localStorage.clear();
  });

  // -------------------------------------------------------------------------
  // haversineKm — pure distance formula
  // -------------------------------------------------------------------------

  describe('haversineKm', () => {
    it('returns 0 for identical coordinates', () => {
      const a: GeoCoords = { latitude: 48.2, longitude: 16.37 };
      expect(service.haversineKm(a, a)).toBeCloseTo(0, 5);
    });

    it('returns ~527 km for Vienna → Berlin', () => {
      const vienna: GeoCoords = { latitude: 48.2092, longitude: 16.3728 };
      const berlin: GeoCoords = { latitude: 52.52, longitude: 13.405 };
      expect(service.haversineKm(vienna, berlin)).toBeCloseTo(527, -1);
    });

    it('is symmetric (a→b == b→a)', () => {
      const a: GeoCoords = { latitude: 51.5, longitude: -0.12 };
      const b: GeoCoords = { latitude: 48.85, longitude: 2.35 };
      expect(service.haversineKm(a, b)).toBeCloseTo(service.haversineKm(b, a), 5);
    });

    it('returns ~1.11 km for a 0.01° latitude step', () => {
      const a: GeoCoords = { latitude: 48.0, longitude: 16.0 };
      const b: GeoCoords = { latitude: 48.01, longitude: 16.0 };
      expect(service.haversineKm(a, b)).toBeCloseTo(1.11, 1);
    });
  });

  // -------------------------------------------------------------------------
  // watchPosition$ — emission behaviour
  // -------------------------------------------------------------------------

  describe('watchPosition$', () => {
    it('emits first position when no stored coords exist', () => {
      const { watchPosition } = mockGeolocation();

      watchPosition.mockImplementation((success: PositionCallback) => {
        success(fakePos(52.52, 13.405));
        return 1;
      });

      const emitted: GeoCoords[] = [];
      service.watchPosition$(1.0).subscribe(c => emitted.push(c));

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toEqual({ latitude: 52.52, longitude: 13.405 });
    });

    it('emits when user moves beyond the threshold', () => {
      service.setCoords({ latitude: 48.2, longitude: 16.37 }); // seed Vienna
      const { watchPosition } = mockGeolocation();

      watchPosition.mockImplementation((success: PositionCallback) => {
        success(fakePos(52.52, 13.405)); // Berlin — ~527 km away
        return 1;
      });

      const emitted: GeoCoords[] = [];
      service.watchPosition$(1.0).subscribe(c => emitted.push(c));

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toEqual({ latitude: 52.52, longitude: 13.405 });
    });

    it('does NOT emit when movement is below the threshold (GPS jitter)', () => {
      service.setCoords({ latitude: 48.2, longitude: 16.37 }); // seed Vienna
      const { watchPosition } = mockGeolocation();

      // Move ~11 metres north — well below the 1 km threshold
      watchPosition.mockImplementation((success: PositionCallback) => {
        success(fakePos(48.2001, 16.37));
        return 1;
      });

      const emitted: GeoCoords[] = [];
      service.watchPosition$(1.0).subscribe(c => emitted.push(c));

      expect(emitted).toHaveLength(0);
    });

    it('does NOT emit when position equals stored coords', () => {
      service.setCoords({ latitude: 48.2, longitude: 16.37 });
      const { watchPosition } = mockGeolocation();

      watchPosition.mockImplementation((success: PositionCallback) => {
        success(fakePos(48.2, 16.37));
        return 1;
      });

      const emitted: GeoCoords[] = [];
      service.watchPosition$(1.0).subscribe(c => emitted.push(c));

      expect(emitted).toHaveLength(0);
    });

    it('updates stored coords after emitting', () => {
      const { watchPosition } = mockGeolocation();

      watchPosition.mockImplementation((success: PositionCallback) => {
        success(fakePos(52.52, 13.405));
        return 1;
      });

      service.watchPosition$(1.0).subscribe();

      expect(service.getCoords()).toEqual({ latitude: 52.52, longitude: 13.405 });
    });

    it('emits successive large moves but suppresses jitter in one subscription', () => {
      const { watchPosition } = mockGeolocation();

      let capturedCallback: PositionCallback | null = null;
      watchPosition.mockImplementation((success: PositionCallback) => {
        capturedCallback = success;
        return 1;
      });

      const emitted: GeoCoords[] = [];
      service.watchPosition$(1.0).subscribe(c => emitted.push(c));

      capturedCallback!(fakePos(48.2, 16.37));    // Vienna — no prior coords, emits
      capturedCallback!(fakePos(52.52, 13.405));  // Berlin — >1 km, emits
      capturedCallback!(fakePos(52.5201, 13.405)); // jitter — suppressed

      expect(emitted).toHaveLength(2);
    });

    it('calls clearWatch on unsubscribe', () => {
      const { watchPosition, clearWatch } = mockGeolocation();
      watchPosition.mockReturnValue(99);

      const sub = service.watchPosition$(1.0).subscribe();
      sub.unsubscribe();

      expect(clearWatch).toHaveBeenCalledWith(99);
    });

    it('completes immediately when geolocation is unavailable', () => {
      Object.defineProperty(navigator, 'geolocation', {
        value: undefined,
        configurable: true,
        writable: true,
      });

      let completed = false;
      service.watchPosition$(1.0).subscribe({ complete: () => (completed = true) });
      expect(completed).toBe(true);
    });
  });
});
