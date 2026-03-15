import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private http = inject(HttpClient);
  private base = `${environment.apiGatewayUrl}/api/v1/billing`;

  createCheckoutSession(): Observable<string> {
    return this.http.post(`${this.base}/checkout-session`, null, { responseType: 'text' });
  }

  createPortalSession(): Observable<string> {
    return this.http.post(`${this.base}/portal-session`, null, { responseType: 'text' });
  }
}
