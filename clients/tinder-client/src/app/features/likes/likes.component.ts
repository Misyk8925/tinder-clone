import { Component, inject, OnInit, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { LikesService } from '../../core/services/likes.service';
import { ProfileService } from '../../core/services/profile.service';
import { SwipeService } from '../../core/services/swipe.service';
import { KeycloakService } from '../../core/services/keycloak.service';
import { SubscriptionService } from '../../core/services/subscription.service';
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
      <header class="page-header">
        <div class="header-copy">
          <span class="eyebrow">Interest</span>
          <div class="title-row">
            <h1>Likes you</h1>
            @if (likers().length > 0) {
              <span class="count-badge">{{ likers().length }}</span>
            }
          </div>
          <p>People who already noticed something in you.</p>
        </div>
      </header>

      @if (loading()) {
        <div class="state-center">
          <div class="spinner" role="status" aria-label="Loading likes"></div>
        </div>
      } @else if (entitlementError()) {
        <div class="state-center entitlement-error">
          <div class="premium-mark">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M12 3 4.5 6v5.4c0 4.7 3.2 8.2 7.5 9.6 4.3-1.4 7.5-4.9 7.5-9.6V6z"/>
              <path d="M9 12.2 11 14l4-4"/>
            </svg>
          </div>
          <span class="eyebrow">Premium check</span>
          <h2>We couldn't verify your plan</h2>
          <p>Your purchase is safe. Try again to refresh your access.</p>
          <button class="btn-retry" type="button" (click)="load()">Check again</button>
        </div>
      } @else if (forbidden()) {
        <section class="premium-gate">
          <div class="teaser-stage">
            <div class="teaser-grid" aria-hidden="true">
              @for (i of placeholders; track i) {
                <div class="teaser-card gradient-{{ i % 4 }}">
                  <span></span>
                </div>
              }
            </div>
            <div class="teaser-vignette" aria-hidden="true"></div>

            <div class="upgrade-box">
              <div class="premium-mark">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M4 16.5 3 7l5 4 4-7 4 7 5-4-1 9.5z"/>
                  <path d="M5 20h14"/>
                </svg>
              </div>
              <span class="eyebrow">Lunari Premium</span>
              <h2>See everyone who likes you</h2>
              <p>Four people are waiting. Unlock their profiles and choose who you want to meet.</p>
              <ul class="premium-benefits">
                <li><span>✓</span> Reveal every profile</li>
                <li><span>✓</span> Match without guessing</li>
                <li><span>✓</span> Unlimited likes</li>
              </ul>
              <button class="btn-upgrade" type="button" (click)="goUpgrade()" [disabled]="checkoutLoading()">
                {{ checkoutLoading() ? 'Opening checkout…' : 'Get Premium · €10/month' }}
                @if (!checkoutLoading()) {
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="m9 18 6-6-6-6"/>
                  </svg>
                }
              </button>
              <span class="checkout-note">Cancel anytime · Secure checkout by Stripe</span>
            </div>
          </div>
        </section>
      } @else if (likers().length === 0) {
        <div class="state-center empty-state">
          <div class="empty-mark">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
              <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1.1L12 21l7.8-7.5 1.1-1.1a5.5 5.5 0 0 0-.1-7.8Z"/>
              <path d="M12 8v8M8 12h8"/>
            </svg>
          </div>
          <span class="eyebrow">Nothing waiting yet</span>
          <h2>Your likes will land here</h2>
          <p>Keep your profile current and continue discovering people you genuinely want to meet.</p>
        </div>
      } @else {
        <section class="likes-grid" aria-label="People who like you">
          @for (card of likers(); track card.likerProfileId) {
            <article class="liker-card" [ngClass]="{ 'super-like': card.isSuper }">
              <div class="profile-visual">
                @if (card.profile?.photos?.length) {
                  <img class="card-img" [src]="card.profile!.photos[0].url" [alt]="card.profile!.name" (error)="onImgError($event)" />
                } @else {
                  <div class="card-no-photo">
                    <span>{{ card.profile?.name?.[0] ?? '?' }}</span>
                  </div>
                }
                <div class="visual-scrim"></div>

                @if (card.isSuper) {
                  <div class="priority-badge">
                    <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>
                    Priority like
                  </div>
                }

                <div class="card-actions">
                  <button class="btn-pass" (click)="pass(card)" [attr.aria-label]="'Pass on ' + (card.profile?.name ?? 'profile')">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="m7 7 10 10M17 7 7 17"/></svg>
                  </button>
                  <button class="btn-like" (click)="like(card)" [attr.aria-label]="'Like back ' + (card.profile?.name ?? 'profile')">
                    <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.35 10.55 20C5.4 15.36 2 12.28 2 8.5A5.5 5.5 0 0 1 7.5 3 6 6 0 0 1 12 5.09 6 6 0 0 1 16.5 3 5.5 5.5 0 0 1 22 8.5c0 3.78-3.4 6.86-8.55 11.51z"/></svg>
                  </button>
                </div>
              </div>

              <div class="profile-copy">
                <div class="profile-title">
                  <h2>{{ card.profile?.name ?? 'Unknown' }}@if (card.profile?.age) {<span>, {{ card.profile!.age }}</span>}</h2>
                  @if (card.profile?.isActive) { <span class="active-dot" aria-label="Active now"></span> }
                </div>
                <p>{{ card.profile?.city || 'Close to you' }}</p>
              </div>
            </article>
          }
        </section>
      }
    </div>

    @if (toast()) {
      <div class="toast-msg">{{ toast() }}</div>
    }
  `,
  styles: [`
    :host {
      display: block;
      height: 100%;
    }

    .likes-page {
      display: flex;
      flex-direction: column;
      height: 100dvh;
      background: transparent;
      padding-bottom: calc(env(safe-area-inset-bottom, 0px) + var(--mobile-bottombar-height));
      overflow-y: auto;
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
      border-top: 3px solid var(--brand);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .toast-msg {
      position: fixed;
      bottom: calc(env(safe-area-inset-bottom, 0px) + var(--mobile-bottombar-height) + 16px);
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

    /* Fresh connections layout */
    .page-header {
      min-height: 62px;
      padding: 8px 18px;
      display: flex;
      align-items: center;
      position: sticky;
      top: 0;
      z-index: 10;
      background: var(--header-surface);
      border-bottom: 1px solid var(--border-light);
      backdrop-filter: blur(18px);
      -webkit-backdrop-filter: blur(18px);
    }

    .header-copy { min-width: 0; }
    .eyebrow {
      display: block;
      color: var(--brand-ink);
      font-size: 10px;
      font-weight: 800;
      line-height: 1;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }

    .page-header .eyebrow,
    .page-header p { display: none; }
    .title-row { display: flex; align-items: center; gap: 9px; }
    .title-row h1 {
      margin: 0;
      color: var(--text-primary);
      font-family: var(--font-editorial);
      font-size: 25px;
      font-weight: 600;
      letter-spacing: -0.04em;
    }
    .count-badge {
      min-width: 24px;
      height: 24px;
      padding: 0 7px;
      display: grid;
      place-items: center;
      border-radius: 999px;
      background: var(--brand-soft);
      color: var(--brand-ink);
      font-size: 11px;
      font-weight: 800;
    }

    .state-center h2 { margin: 4px 0 0; font-size: 24px; letter-spacing: -0.04em; }
    .state-center p { max-width: 330px; line-height: 1.55; }
    .empty-mark,
    .premium-mark {
      width: 52px;
      height: 52px;
      display: grid;
      place-items: center;
      border-radius: 18px;
      color: var(--brand-ink);
      background: var(--brand-soft);
      border: 1px solid var(--brand-border);
    }
    .empty-mark svg,
    .premium-mark svg { width: 25px; height: 25px; }

    .premium-gate {
      width: min(100%, 1040px);
      min-height: calc(100dvh - 62px - var(--mobile-bottombar-height));
      margin: 0 auto;
      padding: 12px 14px 20px;
      display: flex;
      align-items: center;
    }
    .teaser-stage {
      position: relative;
      width: 100%;
      min-height: min(570px, calc(100dvh - 142px));
      overflow: hidden;
      border: 1px solid var(--border-light);
      border-radius: 26px;
      background: var(--surface-2);
      box-shadow: var(--shadow-card);
    }
    .teaser-grid {
      position: absolute;
      inset: 0;
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      grid-template-rows: repeat(2, minmax(0, 1fr));
      gap: 12px;
      padding: 12px;
      filter: saturate(0.65);
      opacity: 0.66;
    }
    .teaser-card {
      position: relative;
      overflow: hidden;
      border: 1px solid var(--border-light);
      border-radius: 20px;
      filter: blur(5px);
      transform: scale(1.035);
    }
    .teaser-card::before {
      content: '';
      position: absolute;
      width: 34%;
      aspect-ratio: 1;
      top: 18%;
      left: 33%;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.38);
    }
    .teaser-card::after {
      content: '';
      position: absolute;
      width: 68%;
      height: 44%;
      left: 16%;
      bottom: 12%;
      border-radius: 50% 50% 20% 20%;
      background: rgba(255, 255, 255, 0.3);
    }
    .teaser-card span { position: absolute; z-index: 1; inset: auto 14px 14px; height: 10px; border-radius: 999px; background: rgba(255,255,255,0.5); }
    .gradient-0 { background: linear-gradient(145deg, #cfe892, #759443); }
    .gradient-1 { background: linear-gradient(145deg, #a6c9c4, #536d70); }
    .gradient-2 { background: linear-gradient(145deg, #d9b5a5, #8b5e52); }
    .gradient-3 { background: linear-gradient(145deg, #c6bddb, #6f6881); }
    .teaser-vignette {
      position: absolute;
      inset: 0;
      background: linear-gradient(180deg, color-mix(in srgb, var(--surface) 8%, transparent), color-mix(in srgb, var(--surface) 70%, transparent));
      backdrop-filter: blur(1px);
    }

    .upgrade-box {
      position: absolute;
      z-index: 2;
      left: 14px;
      right: 14px;
      top: 50%;
      transform: translateY(-50%);
      overflow: hidden;
      padding: 24px 22px 20px;
      border: 1px solid var(--brand-border);
      border-radius: 24px;
      background: color-mix(in srgb, var(--surface) 94%, transparent);
      box-shadow: 0 24px 64px var(--shadow-lg), 0 4px 16px var(--shadow-md);
      backdrop-filter: blur(20px);
      -webkit-backdrop-filter: blur(20px);
    }
    .upgrade-box::after {
      content: '';
      position: absolute;
      width: 180px; height: 180px;
      right: -80px; top: -90px;
      border-radius: 50%;
      background: var(--brand-soft);
      filter: blur(12px);
      pointer-events: none;
    }
    .upgrade-box .eyebrow { margin: 18px 0 8px; }
    .upgrade-box h2 { max-width: 350px; margin: 0; color: var(--text-primary); font-family: var(--font-editorial); font-size: 29px; font-weight: 600; line-height: 1.12; letter-spacing: -0.045em; }
    .upgrade-box p { max-width: 390px; margin: 10px 0 16px; color: var(--text-secondary); font-size: 13px; line-height: 1.5; }
    .premium-benefits {
      margin: 0 0 20px;
      padding: 0;
      display: grid;
      gap: 9px;
      list-style: none;
      color: var(--text-primary);
      font-size: 13px;
      font-weight: 600;
    }
    .premium-benefits span { margin-right: 8px; color: var(--brand-strong); }
    .btn-upgrade {
      width: 100%;
      min-height: 48px;
      padding: 0 16px;
      display: inline-flex;
      justify-content: center;
      align-items: center;
      gap: 8px;
      border-radius: 15px;
      background: var(--brand);
      color: #18200d;
      font-size: 14px;
      font-weight: 750;
      box-shadow: 0 10px 24px var(--brand-glow);
    }
    .btn-upgrade:disabled { cursor: wait; opacity: 0.72; }
    .btn-upgrade svg { width: 17px; height: 17px; }
    .checkout-note { display: block; margin-top: 10px; color: var(--text-muted); font-size: 10px; text-align: center; }
    .btn-retry {
      min-height: 44px;
      padding: 0 18px;
      border: 1px solid var(--brand-border);
      border-radius: 14px;
      color: #18200d;
      background: var(--brand);
      font-weight: 700;
    }

    .likes-grid {
      width: min(100%, 1080px);
      margin: 0 auto;
      padding: 16px 14px 28px;
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 22px 12px;
    }
    .liker-card {
      min-width: 0;
      aspect-ratio: auto;
      overflow: visible;
      border: 0;
      border-radius: 0;
      box-shadow: none;
      cursor: default;
    }
    .liker-card.super-like { outline: 0; }
    .profile-visual {
      position: relative;
      aspect-ratio: 4 / 5;
      overflow: hidden;
      border: 1px solid var(--card-border);
      border-radius: 20px;
      background: var(--surface-2);
      box-shadow: var(--shadow-card);
    }
    .super-like .profile-visual { border-color: var(--brand-border); }
    .liker-card .card-img,
    .liker-card .card-no-photo { position: absolute; inset: 0; width: 100%; height: 100%; }
    .liker-card .card-img { object-fit: cover; }
    .liker-card .card-no-photo { display: grid; place-items: center; background: linear-gradient(145deg, var(--surface-3), var(--brand-soft)); color: var(--brand-ink); }
    .liker-card .card-no-photo span { color: var(--brand-ink); font-family: var(--font-editorial); font-size: 58px; font-weight: 600; opacity: 0.72; }
    .visual-scrim { position: absolute; inset: 42% 0 0; background: linear-gradient(transparent, rgba(13,15,11,0.45)); pointer-events: none; }
    .priority-badge {
      position: absolute;
      top: 10px; left: 10px;
      padding: 6px 9px;
      display: flex;
      align-items: center;
      gap: 5px;
      border-radius: 999px;
      color: #1b230f;
      background: rgba(190, 230, 91, 0.92);
      font-size: 10px;
      font-weight: 800;
      backdrop-filter: blur(10px);
    }
    .priority-badge svg { width: 11px; height: 11px; }
    .liker-card .card-actions { position: absolute; right: 10px; bottom: 10px; display: flex; gap: 7px; }
    .liker-card .card-actions button {
      width: 38px; height: 38px;
      padding: 0;
      display: grid;
      place-items: center;
      border-radius: 50%;
      cursor: pointer;
      backdrop-filter: blur(14px);
    }
    .liker-card .card-actions svg { width: 18px; height: 18px; }
    .liker-card .card-actions .btn-pass { border: 1px solid rgba(255,255,255,0.26); background: rgba(23,23,23,0.46); color: #fff; }
    .liker-card .card-actions .btn-like { border: 0; background: var(--brand); color: #1b230f; }
    .profile-copy { padding: 10px 2px 0; }
    .profile-title { display: flex; align-items: center; gap: 7px; }
    .profile-title h2 { min-width: 0; margin: 0; overflow: hidden; color: var(--text-primary); font-size: 16px; font-weight: 700; letter-spacing: -0.02em; text-overflow: ellipsis; white-space: nowrap; }
    .profile-title h2 span { font-weight: 500; }
    .active-dot { width: 7px; height: 7px; flex: 0 0 auto; border-radius: 50%; background: var(--brand); box-shadow: 0 0 0 3px var(--brand-soft); }
    .profile-copy p { margin: 3px 0 0; overflow: hidden; color: var(--text-muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }

    @media (min-width: 768px) {
      .toast-msg { bottom: 24px; }
      .page-header {
        position: static;
        width: min(100%, 1080px);
        min-height: 0;
        margin: 0 auto;
        padding: 42px 32px 22px;
        background: transparent;
        border: 0;
        backdrop-filter: none;
      }
      .page-header .eyebrow { display: block; margin-bottom: 8px; }
      .page-header p { display: block; margin: 7px 0 0; color: var(--text-muted); font-size: 14px; }
      .title-row h1 { font-size: 40px; }
      .likes-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); padding: 10px 32px 48px; gap: 28px 18px; }
      .premium-gate { min-height: 660px; padding: 12px 32px 54px; }
      .teaser-stage { min-height: 590px; }
      .teaser-grid { gap: 16px; padding: 16px; }
      .upgrade-box { width: min(440px, calc(100% - 48px)); left: auto; right: 34px; padding: 32px; }
    }

    @media (min-width: 1120px) {
      .likes-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
    }
  `]
})
export class LikesComponent implements OnInit {
  private likesService = inject(LikesService);
  private profileService = inject(ProfileService);
  private swipeService = inject(SwipeService);
  private keycloak = inject(KeycloakService);
  private subscriptionService = inject(SubscriptionService);

  likers = signal<LikerCard[]>([]);
  loading = signal(true);
  forbidden = signal(false);
  entitlementError = signal(false);
  checkoutLoading = signal(false);
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
    this.entitlementError.set(false);

    if (this.keycloak.hasPremium()) {
      this.loadLikes();
      return;
    }

    // A user's token can still be stale immediately after Stripe Checkout.
    // Reconcile with Stripe before showing an upgrade prompt so a paid user is
    // never presented with the non-premium paywall.
    this.subscriptionService.syncEntitlement().subscribe({
      next: ({ premium }) => {
        if (!premium) {
          this.forbidden.set(true);
          this.loading.set(false);
          return;
        }
        void this.refreshPremiumAndLoad();
      },
      error: () => {
        this.entitlementError.set(true);
        this.loading.set(false);
      }
    });
  }

  private async refreshPremiumAndLoad(): Promise<void> {
    try {
      await this.keycloak.refreshRoles();
      this.loadLikes();
    } catch {
      this.entitlementError.set(true);
      this.loading.set(false);
    }
  }

  private loadLikes(): void {
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
        if (err.status === 403 || err.status === 401) {
          this.entitlementError.set(true);
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
        next: () => this.removeCard(card.likerProfileId),
        error: (err: HttpErrorResponse) => {
          if (err.status === 429) {
            this.showToast("You're moving fast. Take a moment before the next profile.");
            return;
          }
          this.removeCard(card.likerProfileId);
        }
      });
  }

  pass(card: LikerCard): void {
    if (!this.myProfileId) return;
    this.swipeService.swipe({ profile1Id: this.myProfileId, profile2Id: card.likerProfileId, decision: false })
      .subscribe({
        next: () => this.removeCard(card.likerProfileId),
        error: (err: HttpErrorResponse) => {
          if (err.status === 429) {
            this.showToast("You're moving fast. Take a moment before the next profile.");
            return;
          }
          this.removeCard(card.likerProfileId);
        }
      });
  }

  goUpgrade(): void {
    this.checkoutLoading.set(true);
    this.subscriptionService.createCheckoutSession().subscribe({
      next: (url) => { window.location.href = url; },
      error: () => {
        this.checkoutLoading.set(false);
        this.showToast('Checkout could not be opened. Please try again.');
      }
    });
  }

  onImgError(e: Event): void {
    (e.target as HTMLImageElement).style.display = 'none';
  }

  private removeCard(likerProfileId: string): void {
    this.likers.update(cards => cards.filter(c => c.likerProfileId !== likerProfileId));
  }
}
