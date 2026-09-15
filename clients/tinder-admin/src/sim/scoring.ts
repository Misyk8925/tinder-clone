import { VIENNA, type Agent } from './types';

const EARTH_KM = 6371;

export function toRadians(deg: number): number {
  return (deg * Math.PI) / 180;
}

/** Same Haversine as `LocationProximityStrategy.calculateDistance`. */
export function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const dLat = toRadians(lat2 - lat1);
  const dLng = toRadians(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return EARTH_KM * c;
}

export function localKm(lat: number, lng: number): { x: number; y: number } {
  const x = (lng - VIENNA.lng) * 111.32 * Math.cos(toRadians(VIENNA.lat));
  const y = (lat - VIENNA.lat) * 110.57;
  return { x, y };
}

/** Mirrors `AgeCompatibilityStrategy` with weight 1.0. */
export function ageScore(viewer: Agent, candidate: Agent): number {
  const { minAge, maxAge } = viewer;
  const age = candidate.age;
  if (age >= minAge && age <= maxAge) {
    return 1.0;
  }
  if (age < minAge) {
    const diff = minAge - age;
    return Math.max(0, 1.0 - diff / 10.0);
  }
  const diff = age - maxAge;
  return Math.max(0, 1.0 - diff / 10.0);
}

/** Mirrors `LocationProximityStrategy` with weight 0.8. */
export function proximityScore(viewer: Agent, candidate: Agent): number {
  const distance = haversineKm(viewer.lat, viewer.lng, candidate.lat, candidate.lng);
  const maxRange = viewer.maxRange;
  if (distance <= maxRange) {
    return (1.0 - distance / maxRange) * 0.8;
  }
  return 0;
}

export function baselineScore(viewer: Agent, candidate: Agent): number {
  return ageScore(viewer, candidate) + proximityScore(viewer, candidate);
}

export function popularityScore(viewer: Agent, candidate: Agent): number {
  return baselineScore(viewer, candidate) * (1 + 2.4 * Math.log1p(candidate.likesReceived));
}

export function scoreForArm(viewer: Agent, candidate: Agent): number {
  return viewer.arm === 'popularity' ? popularityScore(viewer, candidate) : baselineScore(viewer, candidate);
}
