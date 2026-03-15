import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface LikedMe {
  likerProfileId: string;
  likedAt: string;
  isSuper: boolean;
}

@Injectable({ providedIn: 'root' })
export class LikesService {
  private http = inject(HttpClient);
  private base = `${environment.apiGatewayUrl}/api/v1/swipes`;

  getLikedMe(): Observable<LikedMe[]> {
    return this.http.get<LikedMe[]>(`${this.base}/liked-me`);
  }
}
