export type Arm = 'baseline' | 'popularity';

export interface Agent {
  id: number;
  lat: number;
  lng: number;
  x: number;
  y: number;
  age: number;
  minAge: number;
  maxAge: number;
  maxRange: number;
  appeal: number;
  arm: Arm;
  likesReceived: number;
  likesFrom: Record<Arm, number>;
  impressions: number;
  liked: Set<number>;
  name: string;
  district: string;
  hue: number;
}

export interface CityConfig {
  seed: number;
  population: number;
  viewers: number;
  deckSize: number;
  searchCap: number;
  newcomerQuota: number;
  newcomerMaxImpressions: number;
  rounds: number;
}

export interface ArmStats {
  swipes: number;
  likes: number;
  matches: number;
}

export interface SimEvent {
  type: 'like' | 'pass' | 'match';
  from: number;
  to: number;
  arm: Arm;
}

export interface TickResult {
  events: SimEvent[];
  done: boolean;
}

export const DEFAULT_CITY: CityConfig = {
  seed: 1,
  population: 4000,
  viewers: 800,
  deckSize: 12,
  searchCap: 80,
  newcomerQuota: 0.12,
  newcomerMaxImpressions: 3,
  rounds: 3,
};

export const VIENNA = { lat: 48.208, lng: 16.373 };
