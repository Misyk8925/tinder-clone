import { Component, inject, OnInit, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
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
  isSuper: boolean;
  profile: Profile | null;
}

@Component({
  selector: 'app-likes',
  imports: [NgClass],
  template: `
    <div class="likes-page">
      <header class="header">
        <h1>Likes You</h1>
        @if (likers().length > 0) {
          <span class="count-badge">{{ likers().length }}</span>
        }
      </header>

      @if (loading()) {
        <div class="state-center">
          <div class="spinner"></div>
        </div>
      } @else if (forbidden()) {
        <div class="forbidden-container">
          <div class="blur-grid">
            @for (i of placeholders; track i) {
              <div class="blur-card">
                <div class="blur-photo gradient-{{ i % 6 }}"></div>
                <div class="blur-info">
                  <div class="blur-line short"></div>
                  <div class="blur-line long"></div>
                </div>
              </div>
            }
          </div>

          <div class="upgrade-overlay">
            <div class="upgrade-box">
              <div class="gold-icon">
                <svg viewBox="0 0 24 24" fill="currentColor" width="36" height="36">
                  <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                </svg>
              </div>
              <h2>See Who Likes You</h2>
              <p>Upgrade to Tinder Gold to see everyone who already liked you.</p>
              <button class="btn-upgrade" (click)="goUpgrade()">
                Upgrade to Gold
              </button>
            </div>
          </div>
        </div>
      } @else if (likers().length === 0) {
        <div class="state-center">
          <div class="empty-flame">
            <svg viewBox="0 0 24 24" fill="#e0e0e0" width="72" height="72">
              <path d="M17.66 11.2c-.23-.3-.51-.56-.77-.82-.67-.6-1.43-1.03-2.07-1.66C13.33 7.26 13 4.85 13.95 3c-.95.23-1.78.75-2.49 1.32-2.59 2.11-3.66 5.65-2.67 8.9.04.14.08.28.08.43 0 .28-.19.52-.45.57-.28.07-.53-.09-.63-.37-.04-.1-.06-.21-.09-.32C7.15 13 7 12.5 7 11.85c0-.58.16-1.2.44-1.7-1.16 1.27-1.86 2.97-1.86 4.77 0 3.31 2.69 6 6 6s6-2.69 6-6c0-1.88-.82-3.63-2.09-4.82z"/>
            </svg>
          </div>
          <h3>No Likes Yet</h3>
          <p>When someone likes your profile, you'll see them here.</p>
        </div>
      } @else {
        <div class="grid">
          @for (card of likers(); track card.likerProfileId) {
            <div class="liker-card" [ngClass]="{ 'super-like': card.isSuper }">

              <!-- background: real photo or gradient -->
              @if (card.profile?.photos?.length) {
                <img class="card-img" [src]="card.profile!.photos[0].url" [alt]="card.profile!.name" (error)="onImgError($event)" />
              } @else {
                <div class="card-no-photo">
                  <span>{{ card.profile?.name?.[0] ?? '?' }}</span>
                </div>
              }

              <!-- super like badge -->
              @if (card.isSuper) {
                <div class="super-banner">
                  <svg viewBox="0 0 24 24" fill="currentColor" width="13" height="13"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>
                  Super Like
                </div>
              }

              <!-- bottom overlay: gradient + name + buttons -->
              <div class="card-bottom">
                <div class="card-name">
                  {{ card.profile?.name ?? 'Unknown' }}
                  @if (card.profile?.age) { <span class="card-age">, {{ card.profile!.age }}</span> }
                </div>
                <div class="card-actions">
                  <button class="btn-pass" (click)="pass(card)" title="Pass">
                    <svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
                  </button>
                  <button class="btn-like" (click)="like(card)" title="Like back">
                    <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.593c-5.63-5.539-11-10.297-11-14.402 0-3.791 3.068-5.191 5.281-5.191 1.312 0 4.151.501 5.719 4.457 1.59-3.968 4.464-4.447 5.726-4.447 2.54 0 5.274 1.621 5.274 5.181 0 4.069-5.136 8.625-11 14.402z"/></svg>
                  </button>
                </div>
              </div>

            </div>
          }
        </div>
      }
    </div>

    @if (toast()) {
      <div class="toast-msg">{{ toast() }}</div>
    }
  `,
  styles: [`
    .likes-page {
      display: flex;
      flex-direction: column;
      min-height: 100dvh;
      background: var(--bg);
      padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 72px);
    }

    @media (min-width: 768px) {
      .likes-page {
        padding-bottom: 0;
        min-height: 100dvh;
      }

      .grid {
        grid-template-columns: repeat(3, 1fr);
        max-width: 900px;
        margin: 0 auto;
      }

      .blur-grid {
        grid-template-columns: repeat(4, 1fr);
        max-width: 900px;
        margin: 0 auto;
      }

      .header {
        padding: 18px 32px 14px;
      }
    }

    @media (min-width: 1200px) {
      .grid {
        grid-template-columns: repeat(4, 1fr);
        max-width: 1100px;
      }

      .blur-grid {
        grid-template-columns: repeat(4, 1fr);
        max-width: 1100px;
      }
    }

    .header {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 18px 20px 14px;
      background: var(--surface);
      border-bottom: 1px solid var(--border);
      position: sticky;
      top: 0;
      z-index: 10;
      width: 100%;

      h1 {
        margin: 0;
        font-size: 22px;
        font-weight: 700;
        color: var(--text-primary);
        letter-spacing: -0.3px;
      }
    }

    .count-badge {
      background: linear-gradient(135deg, #fd267a, #ff6036);
      color: #fff;
      border-radius: 12px;
      padding: 2px 9px;
      font-size: 12px;
      font-weight: 700;
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

      .empty-flame { opacity: 0.35; }
      h3 { margin: 0; font-size: 20px; font-weight: 700; color: var(--text-primary); }
      p { margin: 0; color: var(--text-muted); font-size: 14px; max-width: 240px; }
    }

    .spinner {
      width: 44px; height: 44px;
      border: 3px solid var(--border);
      border-top: 3px solid #fd267a;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    /* ── Forbidden / Non-premium ── */
    .forbidden-container {
      position: relative;
      flex: 1;
    }

    .blur-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 12px;
      padding: 16px;
    }

    .blur-card {
      border-radius: 16px;
      overflow: hidden;
      background: var(--surface);
      filter: blur(10px);
      pointer-events: none;
    }

    .blur-photo {
      aspect-ratio: 3/4;

      &.gradient-0 { background: linear-gradient(160deg, #f093fb, #f5576c); }
      &.gradient-1 { background: linear-gradient(160deg, #4facfe, #00f2fe); }
      &.gradient-2 { background: linear-gradient(160deg, #43e97b, #38f9d7); }
      &.gradient-3 { background: linear-gradient(160deg, #fa709a, #fee140); }
      &.gradient-4 { background: linear-gradient(160deg, #a18cd1, #fbc2eb); }
      &.gradient-5 { background: linear-gradient(160deg, #fd267a, #ff6036); }
    }

    .blur-info {
      padding: 10px 12px;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .blur-line {
      height: 10px;
      background: var(--border);
      border-radius: 5px;

      &.short { width: 60%; }
      &.long { width: 80%; }
    }

    .upgrade-overlay {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(to bottom, transparent 0%, rgba(0,0,0,0.5) 40%, rgba(0,0,0,0.8) 100%);
      padding: 24px;
    }

    .upgrade-box {
      text-align: center;
      max-width: 300px;
      width: 100%;
    }

    .gold-icon {
      width: 72px;
      height: 72px;
      border-radius: 50%;
      background: linear-gradient(135deg, #f9a825, #f57f17);
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 16px;
      color: #fff;
      box-shadow: 0 8px 24px rgba(249,168,37,0.4);
    }

    .upgrade-box h2 {
      margin: 0 0 10px;
      font-size: 24px;
      font-weight: 800;
      color: #fff;
    }

    .upgrade-box p {
      margin: 0 0 24px;
      font-size: 14px;
      color: rgba(255,255,255,0.8);
      line-height: 1.5;
    }

    .btn-upgrade {
      width: 100%;
      padding: 15px;
      border: none;
      border-radius: 50px;
      background: linear-gradient(135deg, #f9a825, #f57f17);
      color: #fff;
      font-size: 16px;
      font-weight: 700;
      cursor: pointer;
      box-shadow: 0 4px 16px rgba(249,168,37,0.4);
      transition: transform 0.15s;

      &:active { transform: scale(0.96); }
    }

    /* ── Premium grid ── */
    .grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 10px;
      padding: 14px;
    }

    .liker-card {
      border-radius: 16px;
      overflow: hidden;
      aspect-ratio: 3/4;
      position: relative;
      box-shadow: 0 2px 14px var(--shadow-md);
      cursor: pointer;

      &.super-like {
        outline: 2.5px solid #00b4cc;
        outline-offset: -2px;
      }

      .card-img {
        position: absolute;
        inset: 0;
        width: 100%; height: 100%;
        object-fit: cover;
      }

      .card-no-photo {
        position: absolute;
        inset: 0;
        background: linear-gradient(160deg, #fd267a 0%, #c0392b 100%);
        display: flex;
        align-items: center;
        justify-content: center;

        span {
          font-size: 56px;
          font-weight: 800;
          color: rgba(255,255,255,0.35);
          text-transform: uppercase;
        }
      }

      .super-banner {
        position: absolute;
        top: 10px;
        left: 10px;
        background: #00b4cc;
        color: #fff;
        font-size: 11px;
        font-weight: 700;
        border-radius: 6px;
        padding: 4px 9px;
        display: flex;
        align-items: center;
        gap: 4px;
        letter-spacing: 0.3px;
        z-index: 2;
      }

      /* bottom overlay: name + action buttons */
      .card-bottom {
        position: absolute;
        bottom: 0;
        left: 0;
        right: 0;
        padding: 36px 10px 10px;
        background: linear-gradient(to top, rgba(0,0,0,0.75) 0%, transparent 100%);
        z-index: 2;
      }

      .card-name {
        color: #fff;
        font-size: 15px;
        font-weight: 700;
        margin-bottom: 8px;
        padding: 0 2px;

        .card-age { font-weight: 500; }
      }

      .card-actions {
        display: flex;
        gap: 8px;

        button {
          flex: 1;
          border: none;
          border-radius: 50px;
          padding: 10px 0;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          transition: transform 0.15s;
          svg { width: 20px; height: 20px; }
          &:active { transform: scale(0.9); }
        }

        .btn-pass {
          background: rgba(0,0,0,0.45);
          color: #fff;
          border: 1.5px solid rgba(255,255,255,0.5);
          backdrop-filter: blur(4px);
        }

        .btn-like {
          background: linear-gradient(135deg, #fd267a, #ff6036);
          color: #fff;
        }
      }
    }

    .toast-msg {
      position: fixed;
      bottom: 100px;
      left: 50%;
      transform: translateX(-50%);
      background: rgba(30, 30, 30, 0.92);
      color: #fff;
      padding: 12px 20px;
      border-radius: 24px;
      font-size: 14px;
      font-weight: 500;
      z-index: 2000;
      white-space: nowrap;
      max-width: 90vw;
      text-align: center;
      animation: fadeIn 0.2s ease;
      box-shadow: 0 4px 16px rgba(0,0,0,0.3);
      backdrop-filter: blur(8px);
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateX(-50%) translateY(6px); }
      to { opacity: 1; transform: translateX(-50%) translateY(0); }
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
  toast = signal<string | null>(null);
  readonly placeholders = [0, 1, 2, 3];
  private toastTimer: ReturnType<typeof setTimeout> | null = null;

  private myProfileId: string | null = null;

  private showToast(msg: string): void {
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toast.set(msg);
    this.toastTimer = setTimeout(() => this.toast.set(null), 4000);
  }

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
            map(profile => ({ likerProfileId: item.likerProfileId, likedAt: item.likedAt, isSuper: item.isSuper, profile })),
            catchError(() => of({ likerProfileId: item.likerProfileId, likedAt: item.likedAt, isSuper: item.isSuper, profile: null }))
          )
        );
        forkJoin(requests).subscribe({
          next: (cards) => {
            this.likers.set(cards);
            this.loading.set(false);
          }
        });
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.forbidden.set(true);
        } else if (err.status === 429) {
          this.showToast('Too many requests. Please wait before refreshing.');
        }
        this.loading.set(false);
      }
    });
  }

  like(card: LikerCard): void {
    if (!this.myProfileId) return;
    this.swipeService.swipe({ profile1Id: this.myProfileId, profile2Id: card.likerProfileId, decision: true })
      .subscribe({
        error: (err: HttpErrorResponse) => {
          if (err.status === 429) {
            this.showToast("You're acting too fast! Please slow down.");
          }
        }
      });
    this.removeCard(card.likerProfileId);
  }

  pass(card: LikerCard): void {
    if (!this.myProfileId) return;
    this.swipeService.swipe({ profile1Id: this.myProfileId, profile2Id: card.likerProfileId, decision: false })
      .subscribe({
        error: (err: HttpErrorResponse) => {
          if (err.status === 429) {
            this.showToast("You're acting too fast! Please slow down.");
          }
        }
      });
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
