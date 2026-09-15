import { haversineKm, scoreForArm } from './scoring';
import type { Agent, Arm, CityConfig, SimEvent, TickResult } from './types';
import { DEFAULT_CITY } from './types';

const CELL_KM = 4;

function cellKey(x: number, y: number): string {
  return `${Math.floor(x / CELL_KM)}:${Math.floor(y / CELL_KM)}`;
}

export class SpatialIndex {
  private readonly cells = new Map<string, Agent[]>();

  constructor(agents: Agent[]) {
    for (const agent of agents) {
      const key = cellKey(agent.x, agent.y);
      const bucket = this.cells.get(key);
      if (bucket) {
        bucket.push(agent);
      } else {
        this.cells.set(key, [agent]);
      }
    }
  }

  nearby(viewer: Agent, cap: number): Agent[] {
    const cx = Math.floor(viewer.x / CELL_KM);
    const cy = Math.floor(viewer.y / CELL_KM);
    const found: Agent[] = [];
    for (let dx = -2; dx <= 2 && found.length < cap * 3; dx++) {
      for (let dy = -2; dy <= 2 && found.length < cap * 3; dy++) {
        const bucket = this.cells.get(`${cx + dx}:${cy + dy}`);
        if (!bucket) continue;
        for (const agent of bucket) {
          if (agent.id !== viewer.id) {
            found.push(agent);
          }
        }
      }
    }
    found.sort(
      (a, b) =>
        haversineKm(viewer.lat, viewer.lng, a.lat, a.lng) -
        haversineKm(viewer.lat, viewer.lng, b.lat, b.lng),
    );
    return found.slice(0, cap);
  }
}

export function rankDeck(
  viewer: Agent,
  candidates: Agent[],
  deckSize: number,
  quota: number,
  newcomerMaxImpressions: number,
): Agent[] {
  const scored = candidates
    .map((candidate) => ({ candidate, score: scoreForArm(viewer, candidate) }))
    .sort((a, b) => b.score - a.score || a.candidate.id - b.candidate.id);

  if (viewer.arm === 'baseline') {
    return scored.slice(0, deckSize).map((row) => row.candidate);
  }

  const newcomers: Agent[] = [];
  const established: Agent[] = [];
  for (const row of scored) {
    if (row.candidate.impressions < newcomerMaxImpressions) {
      newcomers.push(row.candidate);
    } else {
      established.push(row.candidate);
    }
  }

  const period = Math.max(2, Math.round(1 / quota));
  const deck: Agent[] = [];
  let ni = 0;
  let ei = 0;
  for (let i = 0; i < deckSize; i++) {
    const takeNewcomer = (i + 1) % period === 0 && ni < newcomers.length;
    if (takeNewcomer) {
      deck.push(newcomers[ni++]);
    } else if (ei < established.length) {
      deck.push(established[ei++]);
    } else if (ni < newcomers.length) {
      deck.push(newcomers[ni++]);
    }
  }
  return deck;
}

/** Latent like: distance dominates, appeal is heavy-tailed, age is a soft window. */
export function likeProbability(viewer: Agent, candidate: Agent): number {
  const distance = haversineKm(viewer.lat, viewer.lng, candidate.lat, candidate.lng);
  const distFit = Math.exp(-distance / 10);
  const age = candidate.age;
  let ageFit = 1;
  if (age < viewer.minAge) {
    ageFit = Math.max(0.05, 1 - (viewer.minAge - age) / 12);
  } else if (age > viewer.maxAge) {
    ageFit = Math.max(0.05, 1 - (age - viewer.maxAge) / 12);
  }
  const z = 1.35 * candidate.appeal + 0.7 * ageFit + 1.8 * distFit - 2.55;
  const p = 1 / (1 + Math.exp(-z));
  return Math.min(0.72, Math.max(0.03, p));
}

export class MatchingEngine {
  readonly agents: Agent[];
  readonly viewers: Agent[];
  readonly config: CityConfig;
  readonly byId: Map<number, Agent>;
  private readonly index: SpatialIndex;
  private readonly incoming = new Map<number, number[]>();
  private readonly seen = new Map<number, Set<number>>();
  private viewerCursor = 0;
  private cardCursor = 0;
  private currentDeck: Agent[] = [];
  private round = 0;

  constructor(agents: Agent[], viewers: Agent[], config: Partial<CityConfig> = {}) {
    this.agents = agents;
    this.viewers = viewers;
    this.config = { ...DEFAULT_CITY, ...config };
    this.byId = new Map(agents.map((agent) => [agent.id, agent]));
    this.index = new SpatialIndex(agents);
  }

  tick(): TickResult {
    while (this.round < this.config.rounds) {
      if (this.viewerCursor >= this.viewers.length) {
        this.round += 1;
        this.viewerCursor = 0;
        this.currentDeck = [];
        this.cardCursor = 0;
        continue;
      }

      const viewer = this.viewers[this.viewerCursor];
      if (this.currentDeck.length === 0) {
        const nearby = this.index.nearby(viewer, this.config.searchCap);
        const already = this.seen.get(viewer.id) ?? new Set<number>();
        const fresh = nearby.filter((agent) => !already.has(agent.id));
        const queued = (this.incoming.get(viewer.id) ?? [])
          .map((id) => this.byId.get(id))
          .filter((agent): agent is Agent => !!agent && !already.has(agent.id));
        const ranked = rankDeck(
          viewer,
          fresh,
          this.config.deckSize,
          this.config.newcomerQuota,
          this.config.newcomerMaxImpressions,
        );
        const merged: Agent[] = [];
        const used = new Set<number>();
        for (const agent of [...queued, ...ranked]) {
          if (used.has(agent.id) || merged.length >= this.config.deckSize) continue;
          used.add(agent.id);
          merged.push(agent);
        }
        this.currentDeck = merged;
        this.cardCursor = 0;
        this.incoming.set(viewer.id, []);
        if (this.currentDeck.length === 0) {
          this.viewerCursor += 1;
          continue;
        }
      }

      if (this.cardCursor >= this.currentDeck.length) {
        this.viewerCursor += 1;
        this.currentDeck = [];
        this.cardCursor = 0;
        continue;
      }

      const candidate = this.currentDeck[this.cardCursor++];
      candidate.impressions += 1;
      const seen = this.seen.get(viewer.id) ?? new Set<number>();
      seen.add(candidate.id);
      this.seen.set(viewer.id, seen);
      const events: SimEvent[] = [];
      const p = likeProbability(viewer, candidate);
      const like = hashUnit(viewer.id, candidate.id) < p;
      if (!like) {
        events.push({ type: 'pass', from: viewer.id, to: candidate.id, arm: viewer.arm });
        return { events, done: false };
      }

      viewer.liked.add(candidate.id);
      candidate.likesReceived += 1;
      candidate.likesFrom[viewer.arm] += 1;
      events.push({ type: 'like', from: viewer.id, to: candidate.id, arm: viewer.arm });
      if (candidate.liked.has(viewer.id)) {
        events.push({ type: 'match', from: viewer.id, to: candidate.id, arm: viewer.arm });
      } else {
        const queue = this.incoming.get(candidate.id) ?? [];
        queue.push(viewer.id);
        this.incoming.set(candidate.id, queue);
      }
      return { events, done: false };
    }
    return { events: [], done: true };
  }
}

function hashUnit(a: number, b: number): number {
  let h = Math.imul(a + 1, 0x9e3779b1) ^ Math.imul(b + 3, 0x85ebca6b);
  h ^= h >>> 16;
  h = Math.imul(h, 0x7feb352d);
  h ^= h >>> 15;
  return (h >>> 0) / 4294967296;
}

export function matchesPerHundred(swipes: number, matches: number): number {
  if (swipes === 0) {
    return 0;
  }
  return (matches * 100) / swipes;
}

export function emptyArmStats(): Record<Arm, { swipes: number; likes: number; matches: number }> {
  return {
    baseline: { swipes: 0, likes: 0, matches: 0 },
    popularity: { swipes: 0, likes: 0, matches: 0 },
  };
}
