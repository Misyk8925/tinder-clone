import { hashArm, mulberry32 } from './rng';
import { localKm } from './scoring';
import { DEFAULT_CITY, VIENNA, type Agent, type CityConfig } from './types';

const NAMES = [
  'Mira', 'Leo', 'Anja', 'Nico', 'Sofi', 'Omar', 'Lina', 'Jonas', 'Eva', 'Ilya',
  'Nora', 'Kai', 'Yara', 'Theo', 'Mina', 'Adam', 'Lu', 'Sasha', 'Iris', 'Max',
  'Vera', 'Joel', 'Aya', 'Ben', 'Nina', 'Rafi', 'Elsa', 'Hugo', 'Zara', 'Piotr',
];

const DISTRICTS = [
  'Innere Stadt', 'Leopoldstadt', 'Neubau', 'Josefstadt', 'Alsergrund',
  'Wieden', 'Mariahilf', 'Margareten', 'Favoriten', 'Ottakring',
];

function lognormal(rand: () => number, mu: number, sigma: number): number {
  const u1 = Math.max(rand(), 1e-12);
  const u2 = rand();
  const z = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
  return Math.exp(mu + sigma * z);
}

export function generateCity(config: Partial<CityConfig> = {}): Agent[] {
  const cfg = { ...DEFAULT_CITY, ...config };
  const rand = mulberry32(cfg.seed);
  const agents: Agent[] = [];

  for (let id = 0; id < cfg.population; id++) {
    const lat = VIENNA.lat + (rand() - 0.5) * 0.22;
    const lng = VIENNA.lng + (rand() - 0.5) * 0.32;
    const { x, y } = localKm(lat, lng);
    const age = 21 + Math.floor(rand() * 24);
    const window = 4 + Math.floor(rand() * 8);
    agents.push({
      id,
      lat,
      lng,
      x,
      y,
      age,
      minAge: Math.max(18, age - window),
      maxAge: Math.min(55, age + window),
      maxRange: 8 + Math.floor(rand() * 22),
      appeal: Math.min(3.2, lognormal(rand, 0, 0.55)),
      arm: hashArm(id),
      likesReceived: 0,
      likesFrom: { baseline: 0, popularity: 0 },
      impressions: 0,
      liked: new Set(),
      name: NAMES[id % NAMES.length],
      district: DISTRICTS[id % DISTRICTS.length],
      hue: (id * 47) % 360,
    });
  }

  return agents;
}

export function pickViewers(agents: Agent[], count: number, seed: number): Agent[] {
  const rand = mulberry32(seed ^ 0x9e3779b9);
  const order = agents.map((_, i) => i);
  for (let i = order.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    const tmp = order[i];
    order[i] = order[j];
    order[j] = tmp;
  }
  return order.slice(0, Math.min(count, agents.length)).map((i) => agents[i]);
}
