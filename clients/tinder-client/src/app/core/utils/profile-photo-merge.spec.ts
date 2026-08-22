import { describe, expect, it } from 'vitest';
import { Photo, profilePhotoId } from '../models/profile.model';
import { mergeProfilePhotos } from './profile-photo-merge';

describe('profilePhotoId', () => {
  it('Given Profiles JSON with photoId, when the client reads the photo, then that id is used', () => {
    expect(profilePhotoId({ photoId: 'api-1', position: 0, url: 'https://cdn.example/a.jpg' })).toBe('api-1');
  });
});

describe('mergeProfilePhotos', () => {
  it('Given a profile photo already on screen, when getMe returns a new signed URL, then the displayed src is kept', () => {
    const previous = [photo('p1', 0, 'https://cdn.example/a.jpg')];
    const incoming = [photo('p1', 0, 'https://cdn.example/a.jpg?sig=2')];

    const merged = mergeProfilePhotos(previous, incoming, new Set(['p1']));

    expect(merged.photos[0].url).toBe('https://cdn.example/a.jpg');
    expect(merged.readyIds.has('p1')).toBe(true);
  });

  it('Given a photo still loading, when getMe returns a new signed URL, then the fresh URL is used', () => {
    const previous = [photo('p1', 0, 'https://cdn.example/expired.jpg')];
    const incoming = [photo('p1', 0, 'https://cdn.example/fresh.jpg')];

    const merged = mergeProfilePhotos(previous, incoming, new Set());

    expect(merged.photos[0].url).toBe('https://cdn.example/fresh.jpg');
  });

  it('Given a local blob preview, when getMe returns a signed URL, then the blob stays on screen', () => {
    const previous = [photo('pending-0', 0, 'blob:http://localhost/preview')];
    const incoming = [photo('p1', 0, 'https://cdn.example/signed.jpg')];

    const merged = mergeProfilePhotos(previous, incoming, new Set(['pending-0']));

    expect(merged.photos[0].url).toBe('blob:http://localhost/preview');
    expect(profilePhotoId(merged.photos[0])).toBe('p1');
    expect(merged.readyIds.has('p1')).toBe(true);
    expect(merged.readyIds.has('pending-0')).toBe(false);
  });
});

function photo(photoID: string, position: number, url: string): Photo {
  return { photoId: photoID, position, url };
}
