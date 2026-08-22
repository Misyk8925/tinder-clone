import { Message } from '../services/match.service';

export function isBlobUrl(content: string | undefined): boolean {
  return typeof content === 'string' && content.startsWith('blob:');
}

/**
 * Merge server history onto the currently displayed thread.
 * Keep a photo that is already on screen (blob preview or finished decode)
 * so a new presigned URL does not flash the skeleton and re-download.
 */
export function mergeServerChatMessages(
  previous: Message[],
  serverMsgs: Message[],
  readyPhotoIds: Set<string>
): Message[] {
  const previousById = new Map(previous.map(message => [message.id, message]));
  const serverIds = new Set(serverMsgs.map(message => message.id));
  const localOnly = previous.filter(message => !serverIds.has(message.id));

  const mergedServer = serverMsgs.map(message => {
    const prior = previousById.get(message.id);
    if (message.type === 'photo' && prior?.type === 'photo' && shouldKeepDisplayedPhoto(prior, readyPhotoIds)) {
      return { ...message, content: prior.content };
    }
    return message;
  });

  return [...mergedServer, ...localOnly];
}

function shouldKeepDisplayedPhoto(prior: Message, readyPhotoIds: Set<string>): boolean {
  if (!prior.content) {
    return false;
  }
  return isBlobUrl(prior.content) || readyPhotoIds.has(prior.id);
}
