import { Injectable } from '@angular/core';
import { Message } from './match.service';
import { isBlobUrl } from '../utils/chat-history-merge';

export const CHAT_HISTORY_CACHE_PREFIX = 'chatHistory:v2:';
export const CHAT_HISTORY_CACHE_LIMIT = 100;
export const CHAT_PHOTO_URL_MAX_AGE_MS = 4 * 60 * 1000;

interface CachedConversation {
  messages: Message[];
  writtenAt: number;
}

@Injectable({ providedIn: 'root' })
export class ChatHistoryCache {
  private readonly memory = new Map<string, CachedConversation>();

  read(profileId: string, conversationId: string): Message[] {
    if (!profileId || !conversationId) {
      return [];
    }
    const key = this.storageKey(profileId, conversationId);
    const fromMemory = this.memory.get(key);
    if (fromMemory) {
      return cloneMessages(projectForRead(fromMemory));
    }
    const raw = this.safeGet(key);
    if (!raw) {
      return [];
    }
    try {
      const parsed = JSON.parse(raw) as CachedConversation;
      const entry: CachedConversation = {
        messages: parsed.messages ?? [],
        writtenAt: parsed.writtenAt ?? 0
      };
      this.memory.set(key, entry);
      return cloneMessages(projectForRead(entry));
    } catch {
      this.safeRemove(key);
      return [];
    }
  }

  write(profileId: string, conversationId: string, messages: Message[]): void {
    if (!profileId || !conversationId) {
      return;
    }
    const key = this.storageKey(profileId, conversationId);
    const stored: CachedConversation = {
      messages: persistable(messages).slice(-CHAT_HISTORY_CACHE_LIMIT),
      writtenAt: Date.now()
    };
    this.memory.set(key, stored);
    this.safeSet(key, JSON.stringify(stored));
  }

  clear(): void {
    this.memory.clear();
    this.clearSessionKeys();
  }

  private storageKey(profileId: string, conversationId: string): string {
    return `${CHAT_HISTORY_CACHE_PREFIX}${profileId}:${conversationId}`;
  }

  private safeGet(key: string): string | null {
    try {
      return sessionStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private safeSet(key: string, value: string): void {
    try {
      sessionStorage.setItem(key, value);
    } catch {
      this.clearSessionKeys();
      try {
        sessionStorage.setItem(key, value);
      } catch {
        // Quota or private-mode storage; memory cache still applies.
      }
    }
  }

  private safeRemove(key: string): void {
    try {
      sessionStorage.removeItem(key);
    } catch {
      // ignore
    }
  }

  private clearSessionKeys(): void {
    try {
      const keys: string[] = [];
      for (let i = 0; i < sessionStorage.length; i++) {
        const key = sessionStorage.key(i);
        if (key?.startsWith(CHAT_HISTORY_CACHE_PREFIX) || key?.startsWith('chatHistory:v1:')) {
          keys.push(key);
        }
      }
      keys.forEach(key => sessionStorage.removeItem(key));
    } catch {
      // ignore
    }
  }
}

function persistable(messages: Message[]): Message[] {
  return messages
    .filter(message => message?.id && message.type)
    .map(message => ({
      id: message.id,
      senderId: message.senderId,
      content: persistableContent(message),
      type: message.type,
      sentAt: message.sentAt ?? ''
    }));
}

function persistableContent(message: Message): string {
  if (message.type === 'photo' && isBlobUrl(message.content)) {
    return '';
  }
  return message.content ?? '';
}

function projectForRead(entry: CachedConversation): Message[] {
  const photoUrlsExpired = Date.now() - entry.writtenAt > CHAT_PHOTO_URL_MAX_AGE_MS;
  return persistable(entry.messages).map(message => {
    if (message.type === 'photo' && photoUrlsExpired) {
      return { ...message, content: '' };
    }
    return message;
  });
}

function cloneMessages(messages: Message[]): Message[] {
  return messages.map(message => ({ ...message }));
}
