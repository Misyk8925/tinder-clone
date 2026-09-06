import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ReportReason = 'spam' | 'harassment' | 'inappropriate' | 'other';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private http = inject(HttpClient);

  reportProfile(profileId: string, reason: ReportReason, details: string): Observable<unknown> {
    return this.http.post(`${environment.apiGatewayUrl}/api/v1/profiles/${profileId}/report`, {
      reason,
      details
    });
  }

  reportConversation(conversationId: string, reason: ReportReason, details: string, messageId?: string): Observable<unknown> {
    return this.http.post(`${environment.apiGatewayUrl}/rest/conversations/${conversationId}/report`, {
      reason,
      details,
      messageId: messageId ?? null
    });
  }
}
