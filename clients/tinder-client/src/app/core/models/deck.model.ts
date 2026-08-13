import { Hobby } from './profile.model';

export interface DeckCardPreferences {
  minAge: number;
  maxAge: number;
  gender: string;
  maxDistanceKm: number;
}

export interface DeckCardPhoto {
  photoId: string;
  url: string;
  order: number;
}

export interface DeckCard {
  profileId: string;
  name: string;
  age: number;
  city: string | null;
  bio: string | null;
  isActive: boolean;
  preferences: DeckCardPreferences;
  photos: DeckCardPhoto[];
  hobbies: Hobby[];
}

export type DeckPageState = 'READY' | 'REFRESHING' | 'DEGRADED' | 'EMPTY';

export interface DeckPage {
  items: DeckCard[];
  nextCursor: string | null;
  generation: number;
  cursorReset: boolean;
  state: DeckPageState;
}

export interface BuildingDeck {
  state: 'BUILDING';
  retryAfterSeconds: 2;
}

export type DeckResponse = DeckPage | BuildingDeck;

export function isBuildingDeck(response: DeckResponse): response is BuildingDeck {
  return response.state === 'BUILDING';
}
