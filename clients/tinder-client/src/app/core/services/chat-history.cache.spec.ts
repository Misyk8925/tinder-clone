// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ChatHistoryCache,
  CHAT_HISTORY_CACHE_PREFIX,
  CHAT_PHOTO_URL_MAX_AGE_MS
} from './chat-history.cache';
import { Message } from './match.service';

describe('ChatHistoryCache', () => {
  let cache: ChatHistoryCache;

  beforeEach(() => {
    sessionStorage.clear();
    cache = new ChatHistoryCache();
    vi.useRealTimers();
  });

  it('Given a conversation was stored, when it is read again, then messages are returned without HTTP', () => {
    cache.write('profile-a', 'conv-1', [text('m1', 'hello')]);

    expect(cache.read('profile-a', 'conv-1')).toEqual([text('m1', 'hello')]);
  });

  it('Given blob photo previews, when written, then the photo slot is kept without the blob URL', () => {
    cache.write('profile-a', 'conv-1', [
      text('m1', 'hello'),
      { id: 'p1', senderId: 'me', content: 'blob:http://localhost/preview', type: 'photo', sentAt: '2026-08-22T10:00:00Z' }
    ]);

    expect(cache.read('profile-a', 'conv-1')).toEqual([
      text('m1', 'hello'),
      { id: 'p1', senderId: 'me', content: '', type: 'photo', sentAt: '2026-08-22T10:00:00Z' }
    ]);
  });

  it('Given cached photo URLs younger than four minutes, when read, then URLs are returned', () => {
    cache.write('profile-a', 'conv-1', [photo('p1', 'https://cdn.example/fresh.jpg')]);

    expect(cache.read('profile-a', 'conv-1')).toEqual([photo('p1', 'https://cdn.example/fresh.jpg')]);
  });

  it('Given cached photo URLs older than four minutes, when read, then photo content is empty', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-22T10:00:00Z'));
    cache.write('profile-a', 'conv-1', [photo('p1', 'https://cdn.example/stale.jpg')]);

    vi.setSystemTime(new Date(Date.now() + CHAT_PHOTO_URL_MAX_AGE_MS + 1));

    expect(cache.read('profile-a', 'conv-1')).toEqual([photo('p1', '')]);
  });

  it('Given two profiles, when they cache the same conversation id, then histories stay isolated', () => {
    cache.write('profile-a', 'conv-1', [text('a', 'from a')]);
    cache.write('profile-b', 'conv-1', [text('b', 'from b')]);

    expect(cache.read('profile-a', 'conv-1')).toEqual([text('a', 'from a')]);
    expect(cache.read('profile-b', 'conv-1')).toEqual([text('b', 'from b')]);
    expect(sessionStorage.getItem(`${CHAT_HISTORY_CACHE_PREFIX}profile-a:conv-1`)).toContain('from a');
  });

  it('Given cached history, when the cache is cleared, then reads are empty', () => {
    cache.write('profile-a', 'conv-1', [text('m1', 'hello')]);
    cache.clear();

    expect(cache.read('profile-a', 'conv-1')).toEqual([]);
  });
});

function text(id: string, content: string): Message {
  return { id, senderId: 'me', content, type: 'text', sentAt: '2026-08-22T10:00:00Z' };
}

function photo(id: string, content: string): Message {
  return { id, senderId: 'me', content, type: 'photo', sentAt: '2026-08-22T10:00:00Z' };
}
