import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateProfileRequest, Profile } from '../models/profile.model';
import { DeckResponse } from '../models/deck.model';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private http = inject(HttpClient);
  private base = `${environment.apiGatewayUrl}/api/v1/profiles`;
  private deckUrl = `${environment.apiGatewayUrl}/api/v2/deck`;

  getMyDeck(cursor?: string, limit = 20): Observable<DeckResponse> {
    let params = new HttpParams().set('limit', limit);
    if (cursor) params = params.set('cursor', cursor);
    return this.http.get<DeckResponse>(this.deckUrl, { params });
  }

  getProfile(id: string): Observable<Profile> {
    return this.http.get<Profile>(`${this.base}/${id}`);
  }

  getMe(): Observable<Profile> {
    return this.http.get<Profile>(`${this.base}/me`);
  }

  createProfile(data: CreateProfileRequest): Observable<{ message: string; id: string }> {
    return this.http.post<{ message: string; id: string }>(`${this.base}`, data);
  }

  updateProfile(data: CreateProfileRequest): Observable<{ message: string; id: string }> {
    return this.http.put<{ message: string; id: string }>(`${this.base}`, data);
  }

  patchProfile(data: Partial<CreateProfileRequest>): Observable<Profile> {
    return this.http.patch<Profile>(`${this.base}/`, data);
  }

  deleteProfile(): Observable<void> {
    return this.http.delete<void>(`${this.base}/`);
  }

  uploadPhoto(file: File, position: number): Observable<{ url: string }> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ url: string }>(`${this.base}/photos/upload`, form, {
      params: { position }
    });
  }

  deletePhoto(photoId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/photos/${photoId}`);
  }
}
