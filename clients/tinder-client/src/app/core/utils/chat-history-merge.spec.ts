import { describe, expect, it } from 'vitest';
import { Message } from '../services/match.service';
import { mergeServerChatMessages } from './chat-history-merge';

describe('mergeServerChatMessages', () => {
  it('Given a photo already on screen, when history returns a new signed URL, then the displayed src is kept', () => {
    const previous = [photo('p1', 'https://cdn.example/a.jpg')];
    const server = [photo('p1', 'https://cdn.example/a.jpg?sig=2')];

    const merged = mergeServerChatMessages(previous, server, new Set(['p1']));

    expect(merged[0].content).toBe('https://cdn.example/a.jpg');
  });

  it('Given a photo still loading, when history returns a new signed URL, then the fresh URL is used', () => {
    const previous = [photo('p1', 'https://cdn.example/expired.jpg')];
    const server = [photo('p1', 'https://cdn.example/fresh.jpg')];

    const merged = mergeServerChatMessages(previous, server, new Set());

    expect(merged[0].content).toBe('https://cdn.example/fresh.jpg');
  });

  it('Given a local blob preview, when history returns a signed URL, then the blob stays on screen', () => {
    const previous = [photo('p1', 'blob:http://localhost/preview')];
    const server = [photo('p1', 'https://cdn.example/signed.jpg')];

    const merged = mergeServerChatMessages(previous, server, new Set());

    expect(merged[0].content).toBe('blob:http://localhost/preview');
  });

  it('Given an empty cached photo slot, when history returns a signed URL, then the signed URL is used', () => {
    const previous = [photo('p1', '')];
    const server = [photo('p1', 'https://cdn.example/signed.jpg')];

    const merged = mergeServerChatMessages(previous, server, new Set());

    expect(merged[0].content).toBe('https://cdn.example/signed.jpg');
  });
});

function photo(id: string, content: string): Message {
  return { id, senderId: 'me', content, type: 'photo', sentAt: '2026-08-22T10:00:00Z' };
}
