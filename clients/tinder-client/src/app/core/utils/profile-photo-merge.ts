import { Photo, profilePhotoId } from '../models/profile.model';
import { isBlobUrl } from './chat-history-merge';

export function mergeProfilePhotos(
  previous: Photo[],
  incoming: Photo[],
  readyPhotoIds: Set<string>
): { photos: Photo[]; readyIds: Set<string> } {
  const previousByPosition = new Map(previous.map(photo => [photo.position, photo]));
  const readyIds = new Set(readyPhotoIds);

  const photos = incoming.map(photo => {
    const prior = previousByPosition.get(photo.position);
    if (!prior?.url) {
      return photo;
    }
    const priorId = profilePhotoId(prior);
    const incomingId = profilePhotoId(photo);
    const keepDisplayed = isBlobUrl(prior.url) || readyPhotoIds.has(priorId);
    if (!keepDisplayed) {
      return photo;
    }
    if (priorId !== incomingId && readyIds.has(priorId)) {
      readyIds.delete(priorId);
      readyIds.add(incomingId);
    }
    return { ...photo, url: prior.url };
  });

  return { photos, readyIds };
}
