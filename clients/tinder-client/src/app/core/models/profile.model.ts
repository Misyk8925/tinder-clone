export interface Preferences {
  minAge: number;
  maxAge: number;
  gender: 'male' | 'female' | 'other' | 'all';
  maxRange: number;
}

export type Hobby =
  | 'HIKING' | 'CYCLING' | 'RUNNING' | 'GYM' | 'YOGA' | 'SWIMMING'
  | 'FOOTBALL' | 'BASKETBALL' | 'TENNIS' | 'VOLLEYBALL'
  | 'PHOTOGRAPHY' | 'PAINTING' | 'DRAWING' | 'WRITING' | 'MUSIC'
  | 'SINGING' | 'DANCING' | 'COOKING' | 'BAKING' | 'CRAFTING'
  | 'GAMING' | 'READING' | 'MOVIES' | 'TRAVELING' | 'PODCASTS'
  | 'VOLUNTEERING' | 'PETS' | 'GARDENING' | 'MEDITATION' | 'ASTROLOGY';

export interface Photo {
  photoId?: string;
  photoID?: string;
  url: string;
  position: number;
  isPrimary?: boolean;
}

/** Profiles JSON uses `photoId`; some client fixtures still use `photoID`. */
export function profilePhotoId(photo: Pick<Photo, 'photoId' | 'photoID' | 'position'>): string {
  return photo.photoId || photo.photoID || `pos-${photo.position}`;
}

export interface Profile {
  profileId: string;
  name: string;
  age: number;
  gender: string;
  bio: string;
  city: string;
  isActive: boolean;
  isDeleted: boolean;
  preferences: Preferences;
  photos: Photo[];
  hobbies: Hobby[];
  location?: {
    latitude: number;
    longitude: number;
  };
}

export interface CreateProfileRequest {
  name: string;
  age: number;
  gender: string;
  bio: string;
  city: string;
  preferences: Preferences;
  hobbies: Hobby[];
  latitude?: number;
  longitude?: number;
}

export interface SwipeRequest {
  profile1Id: string;
  profile2Id: string;
  decision: boolean;
  isSuper?: boolean;
}
