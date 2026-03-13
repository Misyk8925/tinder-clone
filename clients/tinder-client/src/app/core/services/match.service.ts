import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
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

export interface Message {
  id: string;
  senderId: string;
  content: string;
  type: 'text' | 'photo';
  sentAt: string;
}

@Injectable({ providedIn: 'root' })
export class MatchService {
  private http = inject(HttpClient);

  getMatches(): Observable<Match[]> {
    return this.http.get<Match[]>(`${environment.apiGatewayUrl}/match`);
  }

  createConversation(participant1Id: string, participant2Id: string): Observable<Conversation> {
    return this.http.post<Conversation>(`${environment.apiGatewayUrl}/rest/conversations`, {
      participant1Id,
      participant2Id
    });
  }

  getConversation(conversationId: string): Observable<Conversation> {
    return this.http.get<Conversation>(`${environment.apiGatewayUrl}/rest/conversations/${conversationId}`);
  }
}
