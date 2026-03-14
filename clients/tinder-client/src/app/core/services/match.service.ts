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

export interface ConversationDto {
  conversationId: string;
  participant1Id: string;
  participant2Id: string;
  status: string;
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
    return this.http.post<Conversation>(`${environment.apiGatewayUrl}/rest/conversations`, {
      participant1Id,
      participant2Id
    });
  }

  /**
   * Load conversation history and register the caller's profile ID on the backend
   * so the WS controller can map the JWT sub (Keycloak user ID) to their profile UUID.
   * @param conversationId the conversation to load
   * @param callerProfileId the current user's profile ID (used for JWT → profileId mapping)
   */
  getConversation(conversationId: string, callerProfileId?: string): Observable<Conversation> {
    const params: Record<string, string> = {};
    if (callerProfileId) params['callerProfileId'] = callerProfileId;

    return this.http.get<ConversationWithMessagesResponse>(
      `${environment.apiGatewayUrl}/rest/conversations/${conversationId}`,
      { params }
    ).pipe(
      map(r => ({
        id: r.conversationId,
        participant1Id: r.participant1Id,
        participant2Id: r.participant2Id,
        createdAt: '',
        messages: (r.messages ?? []).map(m => ({
          id: m.messageId,
          senderId: m.senderId,
          content: m.text ?? '',
          type: m.messageType === 'IMAGE' ? 'photo' as const : 'text' as const,
          sentAt: m.createdAt
        }))
      }))
    );
  }

  getMyChats(profileId: string): Observable<ConversationDto[]> {
    return this.http.get<ConversationDto[]>(`${environment.apiGatewayUrl}/rest/conversations/my-chats`, {
      params: { profileId }
    });
  }
}
