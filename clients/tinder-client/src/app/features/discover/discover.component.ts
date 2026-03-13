import { Component, inject, OnInit, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { Profile } from '../../core/models/profile.model';
import { ProfileService } from '../../core/services/profile.service';
import { SwipeService } from '../../core/services/swipe.service';
import { KeycloakService } from '../../core/services/keycloak.service';
import { SwipeCardComponent } from '../../shared/components/swipe-card/swipe-card.component';
import { Router } from '@angular/router';

@Component({
  selector: 'app-discover',
  imports: [SwipeCardComponent, NgClass],
  template: `
    <div class="discover">
      <header class="header">
        <div class="logo">
          <svg viewBox="0 0 24 24" fill="#fd5564"><path d="M12 21.593c-5.63-5.539-11-10.297-11-14.402 0-3.791 3.068-5.191 5.281-5.191 1.312 0 4.151.501 5.719 4.457 1.59-3.968 4.464-4.447 5.726-4.447 2.54 0 5.274 1.621 5.274 5.181 0 4.069-5.136 8.625-11 14.402z"/></svg>
          <span>Tinder</span>
        </div>
      </header>

      <div class="deck-area">
        @if (loading()) {
          <div class="empty-state">
            <div class="spinner"></div>
            <p>Loading profiles...</p>
          </div>
        } @else if (currentIndex() >= profiles().length) {
          <div class="empty-state">
            <div class="empty-icon">🎉</div>
            <h3>You've seen everyone!</h3>
            <p>Check back later for new people</p>
            <button class="btn-primary" (click)="refresh()">Refresh</button>
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

      @if (matchedProfile()) {
        <div class="match-overlay" (click)="dismissMatch()">
          <div class="match-dialog">
            <div class="match-title">It's a Match!</div>
            <div class="match-avatars">
              <div class="avatar-circle">
                <span>Me</span>
              </div>
              <div class="heart">❤️</div>
              <div class="avatar-circle">
                @if (matchedProfile()!.photos?.length) {
                  <img [src]="matchedProfile()!.photos[0].url" [alt]="matchedProfile()!.name" />
                } @else {
                  <span>{{ matchedProfile()!.name[0] }}</span>
                }
              </div>
            </div>
            <p>You and {{ matchedProfile()!.name }} liked each other</p>
            <button class="btn-primary" (click)="goToMatches()">Send a Message</button>
            <button class="btn-ghost" (click)="dismissMatch()">Keep Swiping</button>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .discover {
      display: flex;
      flex-direction: column;
      height: 100vh;
      background: #f5f5f5;
      padding-bottom: 70px;
    }

    .header {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 14px 20px;
      background: #fff;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06);

      .logo {
        display: flex;
        align-items: center;
        gap: 8px;

        svg { width: 28px; height: 28px; }
        span {
          font-size: 22px;
          font-weight: 700;
          color: #fd5564;
          letter-spacing: -0.5px;
        }
      }
    }

    .deck-area {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 16px 16px 0;
      gap: 16px;
      overflow: hidden;
    }

    .cards-stack {
      position: relative;
      width: 100%;
      max-width: 400px;
      flex: 1;

      .card-wrapper {
        position: absolute;
        inset: 0;

        &.z1 { z-index: 1; transform: scale(0.94) translateY(20px); }
        &.z2 { z-index: 2; transform: scale(0.97) translateY(10px); }
        &.z3 { z-index: 3; transform: scale(1); }
      }
    }

    .action-buttons {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 16px;
      padding-bottom: 16px;
    }

    .btn-action {
      border: none;
      border-radius: 50%;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 16px rgba(0,0,0,0.12);
      transition: transform 0.15s, box-shadow 0.15s;

      &:active { transform: scale(0.92); }

      svg { width: 26px; height: 26px; }

      &.nope {
        width: 60px; height: 60px;
        background: #fff;
        color: #fd5564;
        border: 2px solid #fd5564;
      }

      &.superlike {
        width: 52px; height: 52px;
        background: #fff;
        color: #1da1f2;
        border: 2px solid #1da1f2;
        svg { width: 22px; height: 22px; }
      }

      &.like {
        width: 60px; height: 60px;
        background: #fff;
        color: #00d26a;
        border: 2px solid #00d26a;
      }
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      flex: 1;
      gap: 16px;
      text-align: center;

      .empty-icon { font-size: 64px; }
      h3 { margin: 0; font-size: 22px; color: #333; }
      p { margin: 0; color: #888; }
    }

    .spinner {
      width: 48px; height: 48px;
      border: 4px solid #f0f0f0;
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

    .match-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0,0,0,0.7);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .match-dialog {
      background: linear-gradient(135deg, #fd5564, #ff8a00);
      border-radius: 24px;
      padding: 40px 32px;
      text-align: center;
      color: #fff;
      max-width: 320px;
      width: 90%;

      .match-title {
        font-size: 36px;
        font-weight: 800;
        margin-bottom: 24px;
        text-shadow: 0 2px 8px rgba(0,0,0,0.2);
      }

      p {
        font-size: 16px;
        opacity: 0.9;
        margin: 0 0 24px;
      }
    }

    .match-avatars {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      margin-bottom: 20px;

      .heart { font-size: 28px; }
    }

    .avatar-circle {
      width: 80px;
      height: 80px;
      border-radius: 50%;
      border: 3px solid rgba(255,255,255,0.8);
      overflow: hidden;
      background: rgba(255,255,255,0.3);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 28px;
      font-weight: 700;
      color: #fff;

      img { width: 100%; height: 100%; object-fit: cover; }
    }

    .btn-ghost {
      background: transparent;
      color: rgba(255,255,255,0.85);
      border: 2px solid rgba(255,255,255,0.5);
      border-radius: 30px;
      padding: 10px 28px;
      font-size: 15px;
      font-weight: 500;
      cursor: pointer;
      margin-top: 10px;
    }
  `]
})
export class DiscoverComponent implements OnInit {
  private profileService = inject(ProfileService);
  private swipeService = inject(SwipeService);
  private keycloak = inject(KeycloakService);
  private router = inject(Router);

  profiles = signal<Profile[]>([]);
  currentIndex = signal(0);
  loading = signal(true);
  matchedProfile = signal<Profile | null>(null);

  visibleProfiles = () => {
    const all = this.profiles();
    const idx = this.currentIndex();
    return all.slice(idx, idx + 3).reverse();
  };

  private myProfileId: string | null = null;

  ngOnInit(): void {
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
      error: (err) => {
        console.error('Failed to load deck', err);
        this.loading.set(false);
        if (err.status === 404) {
          this.router.navigate(['/profile/edit']);
        }
      }
    });
  }

  onSwipe(direction: 'left' | 'right', profile: Profile): void {
    const userInfo = this.keycloak.getUserInfo();
    if (!userInfo) return;

    if (!this.myProfileId) {
      this.myProfileId = userInfo.id;
    }

    const swipeData = {
      profile1Id: this.myProfileId,
      profile2Id: profile.profileId,
      decision: direction === 'right'
    };

    this.swipeService.swipe(swipeData).subscribe({
      next: () => {
        if (direction === 'right') {
          // Simulate match probability for demo
          if (Math.random() > 0.6) {
            this.matchedProfile.set(profile);
          }
        }
        this.currentIndex.update(v => v + 1);
      },
      error: () => {
        this.currentIndex.update(v => v + 1);
      }
    });
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
    this.swipeRight();
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
