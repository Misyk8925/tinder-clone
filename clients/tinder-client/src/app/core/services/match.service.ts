import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface Match {
  id: string;
  profile1Id: string;
  profile2Id: string;
  matchedAt: string;
  profile?: {
    profileId: string;
    name: string;
    age: number;
    photos: { url: string }[];
  };
}

export interface Conversation {
  id: string;
  participant1Id: string;
  participant2Id: string;
  createdAt: string;
  messages: Message[];
}

export interface LastMessagePreview {
  messageId: string;
  senderId: string;
  messageType: string;
  text: string | null;
  createdAt: string;
}

export interface ConversationDto {
  conversationId: string;
  participant1Id: string;
  participant2Id: string;
  status: string;
  lastMessage: LastMessagePreview | null;
}

export interface Message {
  id: string;
  senderId: string;
  content: string;
  type: 'text' | 'photo';
  sentAt: string;
}

interface ConversationWithMessagesResponse {
  conversationId: string;
  participant1Id: string;
  participant2Id: string;
  status: string;
  messages: {
    messageId: string;
    senderId: string;
    messageType: string;
    text: string | null;
    attachments: { url: string; mimeType: string }[];
    createdAt: string;
  }[];
}

@Injectable({ providedIn: 'root' })
export class MatchService {
  private http = inject(HttpClient);

  getMatches(profileId: string): Observable<Match[]> {
    return this.http.get<Match[]>(`${environment.apiGatewayUrl}/match/${profileId}`);
  }

  createConversation(participant1Id: string, participant2Id: string): Observable<Conversation> {
    return this.http.post<{ conversationId: string }>(`${environment.apiGatewayUrl}/rest/conversations`, {
      participant1Id,
      participant2Id
    }).pipe(
      map(r => ({ id: r.conversationId, participant1Id, participant2Id, createdAt: '', messages: [] }))
    );
  }

  /**
   * Load conversation history. The backend identifies the caller from their token and returns
   * 404 unless they are one of the participants, so no profile ID is sent.
   */
  getConversation(conversationId: string): Observable<Conversation> {
    return this.http.get<ConversationWithMessagesResponse>(
      `${environment.apiGatewayUrl}/rest/conversations/${conversationId}`
    ).pipe(
      map(r => ({
        id: r.conversationId,
        participant1Id: r.participant1Id,
        participant2Id: r.participant2Id,
        createdAt: '',
        messages: (r.messages ?? []).map(m => ({
          id: m.messageId,
          senderId: m.senderId,
          content: m.messageType === 'IMAGE' ? (m.attachments?.[0]?.url ?? '') : (m.text ?? ''),
          type: m.messageType === 'IMAGE' ? 'photo' as const : 'text' as const,
          sentAt: m.createdAt
        }))
      }))
    );
  }

  /** Chats belonging to the authenticated caller, resolved server-side from their token. */
  getMyChats(): Observable<ConversationDto[]> {
    return this.http.get<ConversationDto[]>(`${environment.apiGatewayUrl}/rest/conversations/my-chats`);
  }
}
