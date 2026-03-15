import { Component, inject, OnInit, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { LikesService } from '../../core/services/likes.service';
import { ProfileService } from '../../core/services/profile.service';
import { SwipeService } from '../../core/services/swipe.service';
import { KeycloakService } from '../../core/services/keycloak.service';
import { Profile } from '../../core/models/profile.model';

interface LikerCard {
  likerProfileId: string;
  likedAt: string;
  profile: Profile | null;
}

@Component({
  selector: 'app-likes',
  imports: [NgClass],
  template: `
    <div class="likes-page">
      <header class="header">
        <h1>Likes You</h1>
        <span class="badge" [ngClass]="{ hidden: likers().length === 0 }">{{ likers().length }}</span>
      </header>

      @if (loading()) {
        <div class="state-center">
          <div class="spinner"></div>
          <p>Loading...</p>
        </div>
      } @else if (forbidden()) {
        <div class="state-center upgrade">
          <div class="lock-icon">⭐</div>
          <h2>Premium Feature</h2>
          <p>Upgrade to see everyone who already liked you.</p>
          <button class="btn-primary" (click)="goUpgrade()">Upgrade to Premium</button>
        </div>
      } @else if (likers().length === 0) {
        <div class="state-center">
          <div class="empty-icon">💛</div>
          <h3>No likes yet</h3>
          <p>When someone likes your profile you'll see them here.</p>
        </div>
      } @else {
        <div class="grid">
          @for (card of likers(); track card.likerProfileId) {
            <div class="liker-card">
              <div class="photo">
                @if (card.profile?.photos?.length) {
                  <img [src]="card.profile!.photos[0].url" [alt]="card.profile!.name" (error)="onImgError($event)" />
                } @else {
                  <div class="no-photo">
                    <span>{{ card.profile?.name?.[0] ?? '?' }}</span>
                  </div>
                }
              </div>
              <div class="info">
                <strong>{{ card.profile?.name ?? 'Unknown' }}</strong>
                @if (card.profile?.age) {
                  <span class="age">, {{ card.profile!.age }}</span>
                }
                @if (card.profile?.city) {
                  <p class="city">📍 {{ card.profile!.city }}</p>
                }
              </div>
              <div class="actions">
                <button class="btn-pass" (click)="pass(card)" title="Pass">✕</button>
                <button class="btn-like" (click)="like(card)" title="Like back">❤</button>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .likes-page {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
      background: var(--bg);
      padding-bottom: 80px;
    }

    .header {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 16px 20px 12px;
      background: var(--surface);
      box-shadow: 0 2px 8px var(--shadow-sm);

      h1 {
        margin: 0;
        font-size: 22px;
        font-weight: 700;
        color: var(--text-primary);
      }
    }

    .badge {
      background: #fd5564;
      color: #fff;
      border-radius: 12px;
      padding: 2px 8px;
      font-size: 12px;
      font-weight: 700;

      &.hidden { display: none; }
    }

    .state-center {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      flex: 1;
      gap: 14px;
      text-align: center;
      padding: 40px 24px;

      .empty-icon, .lock-icon { font-size: 60px; }
      h2 { margin: 0; font-size: 22px; color: var(--text-primary); }
      h3 { margin: 0; font-size: 20px; color: var(--text-primary); }
      p { margin: 0; color: var(--text-muted); font-size: 15px; }
    }

    .spinner {
      width: 44px; height: 44px;
      border: 4px solid var(--border-light);
      border-top: 4px solid #fd5564;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .btn-primary {
      background: linear-gradient(135deg, #fd5564, #ff8a00);
      color: #fff;
      border: none;
      border-radius: 30px;
      padding: 12px 32px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
      box-shadow: 0 4px 15px rgba(253,85,100,0.3);
    }

    .grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 12px;
      padding: 16px;
    }

    .liker-card {
      background: var(--surface);
      border-radius: 16px;
      overflow: hidden;
      box-shadow: 0 4px 16px var(--shadow-sm);
      display: flex;
      flex-direction: column;

      .photo {
        position: relative;
        aspect-ratio: 3/4;
        overflow: hidden;

        img { width: 100%; height: 100%; object-fit: cover; }

        .no-photo {
          width: 100%; height: 100%;
          background: linear-gradient(135deg, #fd5564, #ff8a00);
          display: flex;
          align-items: center;
          justify-content: center;
          span { font-size: 52px; color: rgba(255,255,255,0.5); font-weight: 700; }
        }
      }

      .info {
        padding: 10px 12px 4px;

        strong { font-size: 15px; font-weight: 700; color: var(--text-primary); }
        .age { font-size: 15px; color: var(--text-primary); }
        .city { margin: 2px 0 0; font-size: 12px; color: var(--text-muted); }
      }

      .actions {
        display: flex;
        justify-content: space-around;
        padding: 8px 12px 12px;
        gap: 8px;

        button {
          flex: 1;
          border: none;
          border-radius: 20px;
          padding: 8px 0;
          font-size: 18px;
          cursor: pointer;
          transition: transform 0.15s;
          &:active { transform: scale(0.92); }
        }

        .btn-pass {
          background: var(--bg);
          color: #fd5564;
          border: 1.5px solid #fd5564;
        }

        .btn-like {
          background: linear-gradient(135deg, #fd5564, #ff8a00);
          color: #fff;
        }
      }
    }
  `]
})
export class LikesComponent implements OnInit {
  private likesService = inject(LikesService);
  private profileService = inject(ProfileService);
  private swipeService = inject(SwipeService);
  private keycloak = inject(KeycloakService);
  private router = inject(Router);

  likers = signal<LikerCard[]>([]);
  loading = signal(true);
  forbidden = signal(false);

  private myProfileId: string | null = null;

  ngOnInit(): void {
    this.profileService.getMe().subscribe({
      next: (p) => { this.myProfileId = p.profileId; },
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.forbidden.set(false);

    this.likesService.getLikedMe().subscribe({
      next: (items) => {
        if (items.length === 0) {
          this.likers.set([]);
          this.loading.set(false);
          return;
        }
        const requests = items.map(item =>
          this.profileService.getProfile(item.likerProfileId).pipe(
            map(profile => ({ likerProfileId: item.likerProfileId, likedAt: item.likedAt, profile })),
            catchError(() => of({ likerProfileId: item.likerProfileId, likedAt: item.likedAt, profile: null }))
          )
        );
        forkJoin(requests).subscribe({
          next: (cards) => {
            this.likers.set(cards);
            this.loading.set(false);
          }
        });
      },
      error: (err) => {
        if (err.status === 403) {
          this.forbidden.set(true);
        }
        this.loading.set(false);
      }
    });
  }

  like(card: LikerCard): void {
    if (!this.myProfileId) return;
    this.swipeService.swipe({ profile1Id: this.myProfileId, profile2Id: card.likerProfileId, decision: true })
      .subscribe();
    this.removeCard(card.likerProfileId);
  }

  pass(card: LikerCard): void {
    if (!this.myProfileId) return;
    this.swipeService.swipe({ profile1Id: this.myProfileId, profile2Id: card.likerProfileId, decision: false })
      .subscribe();
    this.removeCard(card.likerProfileId);
  }

  goUpgrade(): void {
    this.router.navigate(['/profile']);
  }

  onImgError(e: Event): void {
    (e.target as HTMLImageElement).style.display = 'none';
  }

  private removeCard(likerProfileId: string): void {
    this.likers.update(cards => cards.filter(c => c.likerProfileId !== likerProfileId));
  }
}
