import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { delay, of, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Profile, profilePhotoId } from '../models/profile.model';
import { DeckCard } from '../models/deck.model';
import { previewTextBlocked } from '../preview/preview-moderation';

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

const milaDeckCard: DeckCard = {
  profileId: milaProfile.profileId,
  name: milaProfile.name,
  age: milaProfile.age,
  city: milaProfile.city,
  bio: milaProfile.bio,
  isActive: milaProfile.isActive,
  preferences: {
    minAge: milaProfile.preferences.minAge,
    maxAge: milaProfile.preferences.maxAge,
    gender: milaProfile.preferences.gender,
    maxDistanceKm: milaProfile.preferences.maxRange
  },
  photos: milaProfile.photos.map(photo => ({
    photoId: profilePhotoId(photo),
    url: photo.url,
    order: photo.position
  })),
  hobbies: milaProfile.hobbies
};

const noraProfile: Profile = {
  profileId: 'preview-nora',
  name: 'Nora',
  age: 25,
  gender: 'female',
  bio: 'Museum afternoons, slow Sundays and finding the best ramen in town.',
  city: 'Krems · 14 km',
  isActive: true,
  isDeleted: false,
  preferences: { minAge: 24, maxAge: 32, gender: 'all', maxRange: 40 },
  photos: [],
  hobbies: ['PAINTING', 'READING', 'TRAVELING']
};

const theoProfile: Profile = {
  profileId: 'preview-theo',
  name: 'Theo',
  age: 28,
  gender: 'male',
  bio: 'Design, cycling and cooking for friends.',
  city: 'Vienna · 48 km',
  isActive: false,
  isDeleted: false,
  preferences: { minAge: 24, maxAge: 34, gender: 'all', maxRange: 60 },
  photos: [],
  hobbies: ['CYCLING', 'COOKING', 'MUSIC']
};

const previewProfiles = new Map<string, Profile>([
  [milaProfile.profileId, milaProfile],
  [noraProfile.profileId, noraProfile],
  [theoProfile.profileId, theoProfile]
]);

export const designPreviewInterceptor: HttpInterceptorFn = (request, next) => {
  if (!environment.designPreview) return next(request);

  const url = request.url;
  if (request.method === 'GET' && url.endsWith('/api/v1/profiles/me')) {
    return of(new HttpResponse({ status: 200, body: myProfile }));
  }

  if (request.method === 'GET' && url.includes('/api/v2/deck')) {
    return of(new HttpResponse({
      status: 200,
      body: {
        items: [milaDeckCard],
        nextCursor: null,
        generation: 1,
        cursorReset: false,
        state: 'READY'
      }
    }));
  }

  if (request.method === 'GET' && url.includes('/api/v1/profiles/')) {
    const profileId = url.split('/').pop() ?? '';
    return of(new HttpResponse({ status: 200, body: previewProfiles.get(profileId) ?? milaProfile }));
  }

  if (request.method === 'GET' && url.endsWith('/api/v1/swipes/liked-me')) {
    return of(new HttpResponse({
      status: 200,
      body: [
        { likerProfileId: milaProfile.profileId, likedAt: '2026-08-23T18:42:00Z', isSuper: true },
        { likerProfileId: theoProfile.profileId, likedAt: '2026-08-23T17:16:00Z', isSuper: false }
      ]
    }));
  }

  if (request.method === 'POST' && url.includes('/api/v1/swipes')) {
    return of(new HttpResponse({ status: 200, body: null }));
  }

  if (request.method === 'GET' && url.includes('/match/')) {
    return of(new HttpResponse({
      status: 200,
      body: [
        {
          id: 'preview-match-nora',
          profile1Id: myProfile.profileId,
          profile2Id: noraProfile.profileId,
          matchedAt: '2026-08-23T19:04:00Z'
        },
        {
          id: 'preview-match-mila',
          profile1Id: myProfile.profileId,
          profile2Id: milaProfile.profileId,
          matchedAt: '2026-08-22T18:40:00Z'
        }
      ]
    }));
  }

  if (request.method === 'GET' && url.includes('/rest/conversations/my-chats')) {
    return of(new HttpResponse({
      status: 200,
      body: [
        {
          conversationId: 'preview-chat',
          participant1Id: myProfile.profileId,
          participant2Id: milaProfile.profileId,
          status: 'ACTIVE',
          lastMessage: {
            messageId: 'preview-message-3',
            senderId: milaProfile.profileId,
            messageType: 'TEXT',
            text: 'That trail sounds perfect. Saturday?',
            createdAt: '2026-08-23T19:12:00Z'
          }
        },
        {
          conversationId: 'preview-chat-theo',
          participant1Id: myProfile.profileId,
          participant2Id: theoProfile.profileId,
          status: 'ACTIVE',
          lastMessage: {
            messageId: 'preview-message-theo',
            senderId: myProfile.profileId,
            messageType: 'TEXT',
            text: 'Send me your favourite recipe.',
            createdAt: '2026-08-22T20:05:00Z'
          }
        }
      ]
    }));
  }

  if (request.method === 'GET' && url.includes('/rest/conversations/')) {
    const isTheo = url.endsWith('/preview-chat-theo');
    const otherProfile = isTheo ? theoProfile : milaProfile;
    return of(new HttpResponse({
      status: 200,
      body: {
        conversationId: isTheo ? 'preview-chat-theo' : 'preview-chat',
        participant1Id: myProfile.profileId,
        participant2Id: otherProfile.profileId,
        status: 'ACTIVE',
        messages: isTheo ? [] : [
          {
            messageId: 'preview-message-1',
            senderId: milaProfile.profileId,
            messageType: 'TEXT',
            text: 'Your hiking photo looks like a very good story.',
            attachments: [],
            createdAt: '2026-08-23T18:56:00Z'
          },
          {
            messageId: 'preview-message-2',
            senderId: myProfile.profileId,
            messageType: 'TEXT',
            text: 'It was worth the early train. I know a quieter trail nearby.',
            attachments: [],
            createdAt: '2026-08-23T19:03:00Z'
          },
          {
            messageId: 'preview-message-3',
            senderId: milaProfile.profileId,
            messageType: 'TEXT',
            text: 'That trail sounds perfect. Saturday?',
            attachments: [],
            createdAt: '2026-08-23T19:12:00Z'
          }
        ]
      }
    }));
  }

  if (request.method === 'POST' && url.endsWith('/rest/conversations')) {
    return of(new HttpResponse({ status: 200, body: { conversationId: 'preview-new-chat' } })).pipe(delay(650));
  }

  if (request.method === 'POST' && url.includes('/report')) {
    return of(new HttpResponse({ status: 202, body: { status: 'accepted' } }));
  }

  if ((request.method === 'POST' || request.method === 'PUT' || request.method === 'PATCH')
    && url.includes('/api/v1/profiles')
    && !url.includes('/photos')
    && !url.includes('/report')) {
    if (previewBodyBlocked(request.body)) {
      return throwError(() => new HttpErrorResponse({
        status: 422,
        statusText: 'Unprocessable Entity',
        url: request.url,
        error: {
          code: 'CONTENT_BLOCKED',
          message: 'This content was blocked by moderation. Please choose different wording.'
        }
      }));
    }
    return of(new HttpResponse({ status: 200, body: { message: 'saved', id: myProfile.profileId } }));
  }

  return next(request);
};

function previewBodyBlocked(body: unknown): boolean {
  if (!body || typeof body !== 'object') {
    return false;
  }
  const record = body as Record<string, unknown>;
  return previewTextBlocked(
    [record['name'], record['bio'], record['text']]
      .filter(value => typeof value === 'string')
      .join(' ')
  );
}
