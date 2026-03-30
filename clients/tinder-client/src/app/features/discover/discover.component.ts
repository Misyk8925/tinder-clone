import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgClass } from '@angular/common';
import { Profile } from '../../core/models/profile.model';
import { ProfileService } from '../../core/services/profile.service';
import { SwipeService } from '../../core/services/swipe.service';
import { SwipeCardComponent } from '../../shared/components/swipe-card/swipe-card.component';
import { Router } from '@angular/router';

@Component({
  selector: 'app-discover',
  imports: [SwipeCardComponent, NgClass],
  template: `
    <div class="discover">
      <header class="header">
        <div class="header-spacer"></div>

        <div class="logo">
          <svg viewBox="0 0 24 24" fill="#fd267a" width="26" height="26">
            <path d="M17.66 11.2c-.23-.3-.51-.56-.77-.82-.67-.6-1.43-1.03-2.07-1.66C13.33 7.26 13 4.85 13.95 3c-.95.23-1.78.75-2.49 1.32-2.59 2.11-3.66 5.65-2.67 8.9.04.14.08.28.08.43 0 .28-.19.52-.45.57-.28.07-.53-.09-.63-.37-.04-.1-.06-.21-.09-.32C7.15 13 7 12.5 7 11.85c0-.58.16-1.2.44-1.7-1.16 1.27-1.86 2.97-1.86 4.77 0 3.31 2.69 6 6 6s6-2.69 6-6c0-1.88-.82-3.63-2.09-4.82z"/>
          </svg>
          <span class="logo-text">tinder</span>
        </div>

        <button class="header-btn">
          <svg viewBox="0 0 24 24" fill="none" stroke="#9e9ea0" stroke-width="1.8" width="26" height="26">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          </svg>
        </button>
      </header>

      <div class="deck-area">
        @if (loading()) {
          <div class="empty-state">
            <div class="spinner"></div>
          </div>
        } @else if (currentIndex() >= profiles().length) {
          <div class="empty-state">
            <div class="card-illustration">
              <div class="ill-card ill-back-2"></div>
              <div class="ill-card ill-back-1"></div>
              <div class="ill-card ill-front">
                <div class="ill-like-stamp">LIKE</div>
              </div>
            </div>
            <h3>You've seen everyone!</h3>
            <p>Check back later for new people nearby</p>
            <button class="btn-refresh" (click)="refresh()">
              <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/></svg>
              Refresh
            </button>
          </div>
        } @else {
          <div class="cards-stack">
            @for (profile of visibleProfiles(); track profile.profileId; let i = $index) {
              <div class="card-wrapper" [ngClass]="'z' + (visibleProfiles().length - i)">
                <app-swipe-card
                  [profile]="profile"
                  (swiped)="onSwipe($event, profile)"
                />
              </div>
            }
          </div>

          <div class="action-buttons">
            <button class="btn-action nope" (click)="swipeLeft()" title="Nope">
              <svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
            </button>
            <button class="btn-action superlike" (click)="superLike()" title="Super Like">
              <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>
            </button>
            <button class="btn-action like" (click)="swipeRight()" title="Like">
              <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.593c-5.63-5.539-11-10.297-11-14.402 0-3.791 3.068-5.191 5.281-5.191 1.312 0 4.151.501 5.719 4.457 1.59-3.968 4.464-4.447 5.726-4.447 2.54 0 5.274 1.621 5.274 5.181 0 4.069-5.136 8.625-11 14.402z"/></svg>
            </button>
          </div>
        }
      </div>

      @if (showPremiumModal()) {
        <div class="match-overlay" (click)="dismissPremiumModal()">
          <div class="match-content premium-modal" (click)="$event.stopPropagation()">
            <div class="premium-icon">
              <svg viewBox="0 0 24 24" fill="#00b4cc" width="56" height="56">
                <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/>
              </svg>
            </div>
            <div class="premium-title">Super Like is Premium</div>
            <p class="premium-sub">Upgrade to Tinder Gold to send Super Likes and stand out from the crowd.</p>
            <div class="match-actions">
              <button class="btn-send-msg btn-upgrade" (click)="dismissPremiumModal()">
                Upgrade to Gold
              </button>
              <button class="btn-keep-swiping" (click)="dismissPremiumModal()">Maybe Later</button>
            </div>
          </div>
        </div>
      }

      @if (toast()) {
        <div class="toast-msg">{{ toast() }}</div>
      }

      @if (matchedProfile()) {
        <div class="match-overlay" (click)="dismissMatch()">
          <div class="match-content" (click)="$event.stopPropagation()">
            <div class="match-header">
              <svg viewBox="0 0 24 24" fill="white" width="36" height="36" class="match-flame">
                <path d="M17.66 11.2c-.23-.3-.51-.56-.77-.82-.67-.6-1.43-1.03-2.07-1.66C13.33 7.26 13 4.85 13.95 3c-.95.23-1.78.75-2.49 1.32-2.59 2.11-3.66 5.65-2.67 8.9.04.14.08.28.08.43 0 .28-.19.52-.45.57-.28.07-.53-.09-.63-.37-.04-.1-.06-.21-.09-.32C7.15 13 7 12.5 7 11.85c0-.58.16-1.2.44-1.7-1.16 1.27-1.86 2.97-1.86 4.77 0 3.31 2.69 6 6 6s6-2.69 6-6c0-1.88-.82-3.63-2.09-4.82z"/>
              </svg>
              <div class="match-title">It's a Match!</div>
              <p class="match-sub">You and {{ matchedProfile()!.name }} liked each other</p>
            </div>

            <div class="match-avatars">
              <div class="avatar-ring me">
                <div class="avatar-circle">
                  <span>Me</span>
                </div>
              </div>
              <div class="avatar-ring them">
                <div class="avatar-circle">
                  @if (matchedProfile()!.photos?.length) {
                    <img [src]="matchedProfile()!.photos[0].url" [alt]="matchedProfile()!.name" />
                  } @else {
                    <span>{{ matchedProfile()!.name[0] }}</span>
                  }
                </div>
              </div>
            </div>

            <div class="match-actions">
              <button class="btn-send-msg" (click)="goToMatches()">
                Send a Message
              </button>
              <button class="btn-keep-swiping" (click)="dismissMatch()">Keep Swiping</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .discover {
      display: flex;
      flex-direction: column;
      height: 100dvh;
      background: var(--bg);
      padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 72px);
    }

    @media (min-width: 768px) {
      .discover {
        padding-bottom: 0;
        height: 100dvh;
      }

      .cards-stack {
        max-width: 460px;
      }

      .btn-action {
        &.nope { width: 68px; height: 68px; svg { width: 32px; height: 32px; } }
        &.like { width: 68px; height: 68px; svg { width: 32px; height: 32px; } }
        &.superlike { width: 58px; height: 58px; svg { width: 26px; height: 26px; } }
      }

      .action-buttons {
        gap: 20px;
        padding: 14px 0 18px;
      }
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 16px 10px;
      background: var(--surface);
      border-bottom: 1px solid var(--border);
      flex-shrink: 0;
    }

    .header-spacer {
      width: 36px;
    }

    .header-btn {
      background: none;
      border: none;
      cursor: pointer;
      padding: 4px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      transition: background 0.15s;

      &:active { background: var(--surface-2); }
    }

    .logo {
      display: flex;
      align-items: center;
      gap: 5px;

      .logo-text {
        font-size: 22px;
        font-weight: 800;
        color: #fd267a;
        letter-spacing: -0.5px;
      }
    }

    .deck-area {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 12px 12px 0;
      gap: 12px;
      overflow: hidden;
      min-height: 0;
    }

    .cards-stack {
      position: relative;
      width: 100%;
      max-width: 400px;
      flex: 1;
      min-height: 0;

      .card-wrapper {
        position: absolute;
        inset: 0;

        &.z1 { z-index: 1; transform: scale(0.93) translateY(22px); }
        &.z2 { z-index: 2; transform: scale(0.96) translateY(11px); }
        &.z3 { z-index: 3; transform: scale(1); }
      }
    }

    .action-buttons {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 14px;
      padding: 10px 0 12px;
      flex-shrink: 0;
    }

    .btn-action {
      border: none;
      border-radius: 50%;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--surface);
      box-shadow: 0 2px 12px rgba(0,0,0,0.12);
      transition: transform 0.15s, box-shadow 0.15s;

      &:active { transform: scale(0.88); }

      &.nope {
        width: 58px; height: 58px;
        border: 2px solid #f04949;
        color: #f04949;
        svg { width: 28px; height: 28px; }
      }

      &.superlike {
        width: 50px; height: 50px;
        border: 2px solid #00b4cc;
        color: #00b4cc;
        svg { width: 22px; height: 22px; }
      }

      &.like {
        width: 58px; height: 58px;
        border: 2px solid #4dde8f;
        color: #4dde8f;
        svg { width: 28px; height: 28px; }
      }
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      flex: 1;
      gap: 14px;
      text-align: center;
      padding: 20px;

      h3 { margin: 0; font-size: 22px; font-weight: 800; color: var(--text-primary); }
      p { margin: 0; color: var(--text-muted); font-size: 14px; max-width: 240px; line-height: 1.5; }
    }

    .card-illustration {
      position: relative;
      width: 150px;
      height: 170px;
      margin-bottom: 8px;
    }

    .ill-card {
      position: absolute;
      border-radius: 14px;
    }

    .ill-back-2 {
      width: 106px; height: 138px;
      bottom: 0; left: 50%;
      transform: translateX(-50%) rotate(-6deg) translateY(4px);
      background: var(--surface);
      border: 2px solid var(--border);
    }

    .ill-back-1 {
      width: 110px; height: 142px;
      bottom: 0; left: 50%;
      transform: translateX(-50%) rotate(-2deg) translateY(2px);
      background: var(--surface);
      border: 2px solid var(--border);
    }

    .ill-front {
      width: 114px; height: 148px;
      bottom: 0; left: 50%;
      transform: translateX(-50%) rotate(8deg);
      background: #f0fff4;
      border: 2.5px solid #4dde8f;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    [data-theme="dark"] .ill-front {
      background: #0d2a1a;
    }

    .ill-like-stamp {
      font-size: 20px;
      font-weight: 800;
      color: #4dde8f;
      border: 3px solid #4dde8f;
      border-radius: 6px;
      padding: 3px 10px;
      letter-spacing: 2px;
      transform: rotate(-8deg);
    }

    .spinner {
      width: 44px; height: 44px;
      border: 3px solid var(--border);
      border-top: 3px solid #fd267a;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .btn-refresh {
      display: flex;
      align-items: center;
      gap: 8px;
      background: linear-gradient(135deg, #fd267a, #ff6036);
      color: #fff;
      border: none;
      border-radius: 30px;
      padding: 12px 28px;
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;
      box-shadow: 0 4px 14px rgba(253,38,122,0.3);
      transition: transform 0.15s;

      &:active { transform: scale(0.95); }
    }

    /* ── Match Overlay ── */
    .match-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.88);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      animation: fadeIn 0.25s ease;
    }

    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    .match-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 0 32px;
      width: 100%;
      max-width: 360px;
      animation: slideUp 0.3s ease;
    }

    @keyframes slideUp {
      from { transform: translateY(30px); opacity: 0; }
      to { transform: translateY(0); opacity: 1; }
    }

    .match-header {
      text-align: center;
      margin-bottom: 32px;

      .match-flame {
        opacity: 0.9;
        margin-bottom: 10px;
      }
    }

    .match-title {
      font-size: 42px;
      font-weight: 800;
      background: linear-gradient(135deg, #fd267a, #ff6036);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
      line-height: 1.1;
      margin-bottom: 10px;
    }

    .match-sub {
      margin: 0;
      font-size: 15px;
      color: rgba(255,255,255,0.75);
      font-weight: 400;
    }

    .match-avatars {
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 40px;

      .avatar-ring {
        padding: 3px;
        border-radius: 50%;
        background: linear-gradient(135deg, #fd267a, #ff6036);

        &:first-child { margin-right: -16px; z-index: 1; }
        &:last-child { margin-left: -16px; z-index: 2; }
      }
    }

    .avatar-circle {
      width: 96px;
      height: 96px;
      border-radius: 50%;
      border: 3px solid #1a1a1a;
      overflow: hidden;
      background: rgba(255,255,255,0.15);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 32px;
      font-weight: 700;
      color: #fff;

      img { width: 100%; height: 100%; object-fit: cover; }
    }

    .match-actions {
      display: flex;
      flex-direction: column;
      gap: 12px;
      width: 100%;
    }

    .btn-send-msg {
      width: 100%;
      padding: 16px;
      border: none;
      border-radius: 50px;
      background: linear-gradient(135deg, #fd267a, #ff6036);
      color: #fff;
      font-size: 16px;
      font-weight: 700;
      cursor: pointer;
      letter-spacing: 0.3px;
      box-shadow: 0 4px 20px rgba(253,38,122,0.4);
      transition: transform 0.15s;

      &:active { transform: scale(0.97); }
    }

    .btn-keep-swiping {
      width: 100%;
      padding: 14px;
      border: 1.5px solid rgba(255,255,255,0.35);
      border-radius: 50px;
      background: transparent;
      color: rgba(255,255,255,0.8);
      font-size: 15px;
      font-weight: 500;
      cursor: pointer;
      transition: border-color 0.15s;

      &:active { border-color: rgba(255,255,255,0.6); }
    }

    /* ── Premium Modal ── */
    .premium-modal {
      gap: 0;
    }

    .premium-icon {
      margin-bottom: 16px;
      filter: drop-shadow(0 0 16px rgba(0,180,204,0.5));
    }

    .premium-title {
      font-size: 28px;
      font-weight: 800;
      background: linear-gradient(135deg, #00b4cc, #a78bfa);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
      margin-bottom: 12px;
      text-align: center;
    }

    .premium-sub {
      margin: 0 0 32px;
      font-size: 15px;
      color: rgba(255,255,255,0.75);
      text-align: center;
      line-height: 1.5;
    }

    .btn-upgrade {
      background: linear-gradient(135deg, #00b4cc, #a78bfa);
      box-shadow: 0 4px 20px rgba(0,180,204,0.4);
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
  `]
})
export class DiscoverComponent implements OnInit {
  private profileService = inject(ProfileService);
  private swipeService = inject(SwipeService);
  private router = inject(Router);

  profiles = signal<Profile[]>([]);
  currentIndex = signal(0);
  loading = signal(true);
  matchedProfile = signal<Profile | null>(null);
  showPremiumModal = signal(false);
  toast = signal<string | null>(null);
  private toastTimer: ReturnType<typeof setTimeout> | null = null;

  private showToast(msg: string): void {
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toast.set(msg);
    this.toastTimer = setTimeout(() => this.toast.set(null), 4000);
  }

  visibleProfiles = () => {
    const all = this.profiles();
    const idx = this.currentIndex();
    return all.slice(idx, idx + 3);
  };

  private myProfileId: string | null = null;

  ngOnInit(): void {
    this.profileService.getMe().subscribe({
      next: (p) => { this.myProfileId = p.profileId; },
      error: (err: HttpErrorResponse) => {
        if (err.status === 429) {
          this.showToast('Too many requests. Please wait a moment.');
        } else {
          this.router.navigate(['/profile/edit']);
        }
      }
    });
    this.loadDeck();
  }

  loadDeck(): void {
    this.loading.set(true);
    this.profileService.getMyDeck().subscribe({
      next: (profiles) => {
        this.profiles.set(profiles);
        this.currentIndex.set(0);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        console.error('Failed to load deck', err);
        this.loading.set(false);
        if (err.status === 429) {
          this.showToast('Too many requests. Please wait before refreshing.');
        } else if (err.status === 404) {
          this.router.navigate(['/profile/edit']);
        }
      }
    });
  }

  onSwipe(direction: 'left' | 'right', profile: Profile, isSuper = false): void {
    if (!this.myProfileId) return;

    this.swipeService.swipe({
      profile1Id: this.myProfileId,
      profile2Id: profile.profileId,
      decision: direction === 'right',
      isSuper
    }).subscribe({
      next: () => { this.currentIndex.update(v => v + 1); },
      error: (err: HttpErrorResponse) => {
        if (isSuper && err.status === 403) {
          this.showPremiumModal.set(true);
        } else if (err.status === 429) {
          this.showToast("You're swiping too fast! Please slow down.");
        } else {
          this.currentIndex.update(v => v + 1);
        }
      }
    });
  }

  dismissPremiumModal(): void {
    this.showPremiumModal.set(false);
  }

  swipeLeft(): void {
    const current = this.profiles()[this.currentIndex()];
    if (current) this.onSwipe('left', current);
  }

  swipeRight(): void {
    const current = this.profiles()[this.currentIndex()];
    if (current) this.onSwipe('right', current);
  }

  superLike(): void {
    const current = this.profiles()[this.currentIndex()];
    if (current) this.onSwipe('right', current, true);
  }

  refresh(): void {
    this.loadDeck();
  }

  dismissMatch(): void {
    this.matchedProfile.set(null);
  }

  goToMatches(): void {
    this.matchedProfile.set(null);
    this.router.navigate(['/matches']);
  }
}
