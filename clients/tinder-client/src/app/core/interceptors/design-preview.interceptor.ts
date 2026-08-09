import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Profile } from '../models/profile.model';

const myProfile: Profile = {
  profileId: 'preview-me',
  name: 'Michael',
  age: 27,
  gender: 'male',
  bio: 'A good conversation and a long walk are a strong start.',
  city: 'St. Pölten',
  isActive: true,
  isDeleted: false,
  preferences: { minAge: 22, maxAge: 34, gender: 'all', maxRange: 50 },
  photos: [{ photoID: 'preview-me-photo', url: '/assets/profiles/mila-discover.png', position: 0, isPrimary: true }],
  hobbies: ['HIKING', 'MUSIC', 'COOKING']
};

const milaProfile: Profile = {
  profileId: 'preview-mila',
  name: 'Mila',
  age: 24,
  gender: 'female',
  bio: 'Coffee, a trail, then live music.',
  city: 'St. Pölten · 3 km',
  isActive: true,
  isDeleted: false,
  preferences: { minAge: 23, maxAge: 31, gender: 'all', maxRange: 35 },
  photos: [
    { photoID: 'mila-1', url: '/assets/profiles/mila-discover.png', position: 0, isPrimary: true },
    { photoID: 'mila-2', url: '/assets/profiles/mila-discover.png', position: 1 }
  ],
  hobbies: ['HIKING', 'MUSIC', 'COOKING']
};

export const designPreviewInterceptor: HttpInterceptorFn = (request, next) => {
  if (!environment.designPreview) return next(request);

  const url = request.url;
  if (request.method === 'GET' && url.endsWith('/api/v1/profiles/me')) {
    return of(new HttpResponse({ status: 200, body: myProfile }));
  }

  if (request.method === 'GET' && url.includes('/api/v1/deck')) {
    return of(new HttpResponse({ status: 200, body: [milaProfile] }));
  }

  if (request.method === 'GET' && url.includes('/api/v1/profiles/')) {
    return of(new HttpResponse({ status: 200, body: milaProfile }));
  }

  if (request.method === 'GET' && url.endsWith('/api/v1/swipes/liked-me')) {
    return of(new HttpResponse({ status: 200, body: [] }));
  }

  if (request.method === 'POST' && url.includes('/api/v1/swipes')) {
    return of(new HttpResponse({ status: 200, body: null }));
  }

  if (request.method === 'GET' && url.includes('/match/')) {
    return of(new HttpResponse({ status: 200, body: [] }));
  }

  if (request.method === 'GET' && url.includes('/rest/conversations/my-chats')) {
    return of(new HttpResponse({ status: 200, body: [] }));
  }

  if (request.method === 'GET' && url.includes('/rest/conversations/')) {
    return of(new HttpResponse({
      status: 200,
      body: {
        conversationId: 'preview-chat',
        participant1Id: myProfile.profileId,
        participant2Id: milaProfile.profileId,
        status: 'ACTIVE',
        messages: []
      }
    }));
  }

  return next(request);
};
