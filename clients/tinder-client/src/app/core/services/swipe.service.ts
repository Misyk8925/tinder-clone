import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SwipeRequest } from '../models/profile.model';

@Injectable({ providedIn: 'root' })
export class SwipeService {
  private http = inject(HttpClient);
  private base = `${environment.apiGatewayUrl}/api/v1/swipes`;

  swipe(data: SwipeRequest): Observable<void> {
    return this.http.post<void>(this.base, data);
  }
}
