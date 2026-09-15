import { describe, expect, it } from 'vitest';
import { generateCity, pickViewers } from './city';
import { emptyArmStats, likeProbability, MatchingEngine, matchesPerHundred, rankDeck } from './engine';
import { hashArm } from './rng';
import { ageScore, baselineScore, haversineKm, popularityScore, proximityScore } from './scoring';
import { VIENNA, type Agent } from './types';

function agent(partial: Partial<Agent> & Pick<Agent, 'id'>): Agent {
  return {
    lat: VIENNA.lat,
    lng: VIENNA.lng,
    x: 0,
    y: 0,
    age: 28,
    minAge: 24,
    maxAge: 34,
    maxRange: 20,
    appeal: 1,
    arm: 'baseline',
    likesReceived: 0,
    likesFrom: { baseline: 0, popularity: 0 },
    impressions: 0,
    liked: new Set(),
    name: 'Ada',
    district: 'Neubau',
    hue: 120,
    ...partial,
  };
}

describe('deck scoring parity with Java strategies', () => {
  it('Given a candidate inside the viewer age window, when age is scored, then the weight is 1.0', () => {
    const viewer = agent({ id: 1 });
    const candidate = agent({ id: 2, age: 30 });
    expect(ageScore(viewer, candidate)).toBe(1);
  });

  it('Given a candidate 5 years younger than minAge, when age is scored, then the score is 0.5', () => {
    const viewer = agent({ id: 1, minAge: 30, maxAge: 40 });
    const candidate = agent({ id: 2, age: 25 });
    expect(ageScore(viewer, candidate)).toBe(0.5);
  });

  it('Given two agents at the same point, when proximity is scored, then the weight is 0.8', () => {
    const viewer = agent({ id: 1 });
    const candidate = agent({ id: 2 });
    expect(proximityScore(viewer, candidate)).toBeCloseTo(0.8, 6);
    expect(baselineScore(viewer, candidate)).toBeCloseTo(1.8, 6);
  });

  it('Given a candidate exactly at maxRange, when proximity is scored, then the score is 0', () => {
    const viewer = agent({ id: 1, maxRange: 10, lat: VIENNA.lat, lng: VIENNA.lng });
    const candidate = agent({
      id: 2,
      lat: VIENNA.lat,
      lng: VIENNA.lng + 10 / (111.32 * Math.cos((VIENNA.lat * Math.PI) / 180)),
    });
    const distance = haversineKm(viewer.lat, viewer.lng, candidate.lat, candidate.lng);
    expect(distance).toBeGreaterThan(9.5);
    expect(proximityScore(viewer, candidate)).toBeGreaterThanOrEqual(0);
    expect(proximityScore(viewer, candidate)).toBeLessThan(0.05);
  });
});

describe('popularity ranking', () => {
  it('Given two candidates identical on age and location, when one has more likes, then popularity ranks that one first', () => {
    const viewer = agent({ id: 1, arm: 'popularity' });
    const quiet = agent({ id: 2, likesReceived: 0 });
    const star = agent({ id: 3, likesReceived: 10 });
    expect(baselineScore(viewer, quiet)).toBeCloseTo(baselineScore(viewer, star), 8);
    expect(popularityScore(viewer, star)).toBeGreaterThan(popularityScore(viewer, quiet));
    const deck = rankDeck(viewer, [quiet, star], 2, 0.12, 3);
    expect(deck[0].id).toBe(3);
  });

  it('Given a 12% newcomer quota, when the deck is filled, then a low-impression profile is interleaved rather than appended', () => {
    const viewer = agent({ id: 1, arm: 'popularity' });
    const established = Array.from({ length: 20 }, (_, i) =>
      agent({ id: 10 + i, likesReceived: 8, impressions: 12, age: 28 }),
    );
    const newcomer = agent({ id: 99, likesReceived: 0, impressions: 0, age: 28 });
    const deck = rankDeck(viewer, [...established, newcomer], 12, 0.12, 3);
    const period = Math.round(1 / 0.12);
    expect(deck[period - 1].id).toBe(99);
  });
});

describe('matching observatory city', () => {
  it('Given a seeded city, when arms are assigned, then the split is deterministic and mixed', () => {
    expect(hashArm(0)).not.toBe(hashArm(1));
    const city = generateCity({ seed: 1, population: 200, viewers: 40 });
    const baseline = city.filter((a) => a.arm === 'baseline').length;
    expect(baseline).toBeGreaterThan(80);
    expect(baseline).toBeLessThan(120);
    expect(generateCity({ seed: 1, population: 200 })[7].lat).toBe(city[7].lat);
    expect(city[7].name).toBe(generateCity({ seed: 1, population: 200 })[7].name);
    expect(city[7].name.length).toBeGreaterThan(1);
  });

  it('Given a seeded city of 400 agents, when 80 viewers each swipe a 12-card deck, then popularity records more matches per 100 swipes than baseline', () => {
    const agents = generateCity({ seed: 1, population: 400, viewers: 80, deckSize: 12, searchCap: 60 });
    const viewers = pickViewers(agents, 80, 1);
    const engine = new MatchingEngine(agents, viewers, {
      seed: 1,
      population: 400,
      viewers: 80,
      deckSize: 12,
      searchCap: 60,
      newcomerQuota: 0.12,
      newcomerMaxImpressions: 3,
      rounds: 3,
    });
    const stats = emptyArmStats();
    let guard = 0;
    while (guard++ < 20_000) {
      const { events, done } = engine.tick();
      if (done) break;
      for (const event of events) {
        if (event.type === 'pass' || event.type === 'like') {
          stats[event.arm].swipes += 1;
        }
        if (event.type === 'like') {
          stats[event.arm].likes += 1;
        }
        if (event.type === 'match') {
          stats[event.arm].matches += 1;
        }
      }
    }
    const baselineRate = matchesPerHundred(stats.baseline.swipes, stats.baseline.matches);
    const popularityRate = matchesPerHundred(stats.popularity.swipes, stats.popularity.matches);
    expect(stats.baseline.swipes).toBeGreaterThan(200);
    expect(stats.popularity.swipes).toBeGreaterThan(200);
    expect(popularityRate).toBeGreaterThan(baselineRate);
  });

  it('Given a popularity viewer likes a candidate, when the like is recorded, then only that arm\'s heat increases', () => {
    const agents = generateCity({ seed: 2, population: 80, viewers: 20, deckSize: 8, searchCap: 40, rounds: 1 });
    const viewers = pickViewers(agents, 20, 2).filter((agent) => agent.arm === 'popularity');
    const engine = new MatchingEngine(agents, viewers.slice(0, 8), {
      seed: 2,
      population: 80,
      viewers: 8,
      deckSize: 8,
      searchCap: 40,
      newcomerQuota: 0.12,
      newcomerMaxImpressions: 3,
      rounds: 1,
    });
    let liked = false;
    for (let i = 0; i < 4000 && !liked; i++) {
      const { events, done } = engine.tick();
      if (done) break;
      for (const event of events) {
        if (event.type !== 'like') continue;
        const candidate = engine.byId.get(event.to);
        expect(candidate?.likesFrom.popularity).toBeGreaterThan(0);
        expect(candidate?.likesFrom.baseline).toBe(0);
        liked = true;
      }
    }
    expect(liked).toBe(true);
  });

  it('Given two nearby appealing agents, when like probability is computed, then it stays inside (0, 1)', () => {
    const viewer = agent({ id: 1, appeal: 1.4 });
    const candidate = agent({ id: 2, appeal: 2.1 });
    const p = likeProbability(viewer, candidate);
    expect(p).toBeGreaterThan(0.03);
    expect(p).toBeLessThanOrEqual(0.72);
  });
});
