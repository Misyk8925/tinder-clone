import { Component, inject, OnDestroy, OnInit, QueryList, signal, ViewChildren } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgClass } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { Router } from '@angular/router';
import { DeckCard, DeckPage, isBuildingDeck } from '../../core/models/deck.model';
import { ProfileService } from '../../core/services/profile.service';
import { SwipeService } from '../../core/services/swipe.service';
import { SwipeCardComponent } from '../../shared/components/swipe-card/swipe-card.component';
import { ReportService } from '../../core/services/report.service';

@Component({
  selector: 'app-discover',
  imports: [SwipeCardComponent, NgClass, LucideAngularModule],
  template: `
    <div class="discover">
      <header class="discover-header">
        <h1>Discover</h1>
        <div class="header-actions">
          <button type="button" class="icon-button" (click)="goToFilters()" aria-label="Edit discovery filters">
            <lucide-icon name="sliders-horizontal" [size]="24" strokeWidth="1.8" />
          </button>
          <span class="availability" title="Discovery is active" aria-label="Discovery is active"></span>
        </div>
      </header>

      <main class="deck-area">
        @if (loading()) {
          <div class="state-panel" aria-live="polite">
            <div class="spinner"></div>
            <p>Finding thoughtful matches nearby…</p>
          </div>
        } @else if (retrying() && currentIndex() >= profiles().length && !leavingId()) {
          <div class="state-panel" aria-live="polite">
            <span class="state-icon"><lucide-icon name="refresh-cw" [size]="36" strokeWidth="1.6" /></span>
            <h2>Still preparing your deck</h2>
            <p>It is taking longer than usual. We will keep trying in the background.</p>
            <button type="button" class="primary-button" (click)="retryNow()">
              <lucide-icon name="refresh-cw" [size]="18" strokeWidth="2" /> Try again
            </button>
          </div>
        } @else if (currentIndex() >= profiles().length && !leavingId()) {
          <div class="state-panel">
            <span class="state-icon"><lucide-icon name="user-round-search" [size]="36" strokeWidth="1.6" /></span>
            <h2>You’re all caught up</h2>
            <p>New people appear as the community grows. Check again in a little while.</p>
            <button type="button" class="primary-button" (click)="refresh()">
              <lucide-icon name="refresh-cw" [size]="18" strokeWidth="2" /> Refresh
            </button>
          </div>
        } @else {
          <div class="cards-stack" [class.departing]="!!leavingId()">
            <div class="stack-plate plate-back" aria-hidden="true"></div>
            <div class="stack-plate plate-mid" aria-hidden="true"></div>
            @for (profile of frontCards(); track profile.profileId; let index = $index) {
              <div class="card-wrapper" [ngClass]="wrapperClass(profile, index)">
                <app-swipe-card
                  [profile]="profile"
                  (swipeCommitted)="onSwipeCommitted(profile)"
                  (swiped)="onSwipe($event, profile)"
                  (reportRequested)="reportProfile(profile)"
                />
              </div>
            }
          </div>

          <div class="action-buttons" aria-label="Profile actions">
            <button type="button" class="action-button pass" (click)="swipeLeft()" aria-label="Pass">
              <lucide-icon name="x" [size]="30" strokeWidth="1.8" />
            </button>
            <button type="button" class="action-button standout" (click)="superLike()" aria-label="Stand out">
              <lucide-icon name="star" [size]="29" fill="#ffffff" strokeWidth="1.7" />
            </button>
            <button type="button" class="action-button like" (click)="swipeRight()" aria-label="Like">
              <lucide-icon name="heart" [size]="30" strokeWidth="1.8" />
            </button>
          </div>
        }
      </main>

      @if (showPremiumModal()) {
        <div class="overlay" (click)="dismissPremiumModal()">
          <section class="dialog" (click)="$event.stopPropagation()" aria-modal="true" role="dialog" aria-labelledby="premium-title">
            <span class="dialog-icon premium"><lucide-icon name="star" [size]="34" fill="currentColor" /></span>
            <h2 id="premium-title">Stand out with Premium</h2>
            <p>Send a priority like when you want someone to notice you first.</p>
            <button type="button" class="primary-button wide" (click)="goToPremium()">View Premium</button>
            <button type="button" class="secondary-button" (click)="dismissPremiumModal()">Maybe later</button>
          </section>
        </div>
      }

      @if (toast()) {
        <div class="toast" role="status">{{ toast() }}</div>
      }

      @if (matchedProfile()) {
        <div class="overlay" (click)="dismissMatch()">
          <section class="dialog match-dialog" (click)="$event.stopPropagation()" aria-modal="true" role="dialog" aria-labelledby="match-title">
            <span class="dialog-icon"><lucide-icon name="heart-handshake" [size]="34" strokeWidth="1.7" /></span>
            <p class="eyebrow">A mutual connection</p>
            <h2 id="match-title">You matched with {{ matchedProfile()!.name }}</h2>
            <p>Start with something you noticed in their profile.</p>
            <div class="match-photo">
              @if (matchedProfile()!.photos.length) {
                <img [src]="matchedProfile()!.photos[0].url" [alt]="matchedProfile()!.name" />
              } @else {
                <span>{{ matchedProfile()!.name[0] }}</span>
              }
            </div>
            <button type="button" class="primary-button wide" (click)="goToMatches()">Send a message</button>
            <button type="button" class="secondary-button" (click)="dismissMatch()">Keep discovering</button>
          </section>
        </div>
      }
    </div>
  `,
  styles: [`
    .discover {
      height: 100dvh;
      display: flex;
      flex-direction: column;
      padding-bottom: calc(var(--mobile-bottombar-height) + env(safe-area-inset-bottom, 0px));
      background: var(--bg);
      overflow: hidden;
    }

    .discover-header {
      min-height: var(--mobile-topbar-height);
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 4px 12px;
      flex: 0 0 auto;
      background: var(--header-surface);
    }

    h1 {
      margin: 0;
      color: var(--text-primary);
      font-size: 26px;
      line-height: 1;
      letter-spacing: -0.055em;
      font-weight: 700;
    }

    .header-actions { display: flex; align-items: center; gap: 8px; }

    .icon-button {
      width: 36px;
      height: 36px;
      display: grid;
      place-items: center;
      border: 0;
      border-radius: 50%;
      color: var(--text-primary);
      background: transparent;
      cursor: pointer;
      transition: background 160ms ease, transform 160ms ease;
    }

    .icon-button:hover { background: var(--brand-soft); transform: translateY(-1px); }
    .icon-button:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }

    .availability {
      width: 18px;
      height: 18px;
      display: block;
      border-radius: 50%;
      background: var(--brand);
      box-shadow: 0 0 0 5px var(--brand-soft);
    }

    .deck-area {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
      padding: 0 0 8px;
      overflow: hidden;
    }

    .cards-stack {
      position: relative;
      width: 100%;
      flex: 1;
      min-height: 0;
      overflow: hidden;
      isolation: isolate;
    }

    .stack-plate {
      position: absolute;
      inset: 0;
      border-radius: 20px;
      border: 1px solid var(--card-border);
      background: var(--surface);
      pointer-events: none;
    }
    .stack-plate.plate-back { z-index: 1; transform: scale(0.96) translateY(12px); }
    .stack-plate.plate-mid { z-index: 2; transform: scale(0.98) translateY(6px); }
    .card-wrapper {
      position: absolute;
      inset: 0;
      overflow: hidden;
      border-radius: 20px;
      background: transparent;
    }
    .card-wrapper.z3 { z-index: 3; }
    .cards-stack.departing .card-wrapper.z3 { pointer-events: none; }
    .card-wrapper.leaving {
      z-index: 10;
      overflow: visible;
      pointer-events: none;
    }

    .action-buttons {
      min-height: 96px;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: clamp(20px, 8vw, 34px);
      flex: 0 0 auto;
      width: 100%;
    }

    .action-button {
      width: clamp(72px, 19vw, 82px);
      height: clamp(72px, 19vw, 82px);
      display: grid;
      place-items: center;
      border-radius: 50%;
      border: 1px solid var(--card-border);
      color: var(--text-secondary);
      background: var(--card-surface);
      box-shadow: var(--shadow-float);
      cursor: pointer;
      transition: transform 160ms ease, box-shadow 160ms ease, background 160ms ease;
    }

    .action-button:hover { transform: translateY(-2px); box-shadow: var(--shadow-card); }
    .action-button:active { transform: scale(0.94); }
    .action-button:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }

    .action-button.standout {
      width: clamp(78px, 19.5vw, 84px);
      height: clamp(78px, 19.5vw, 84px);
      color: white;
      border-color: var(--brand);
      background: var(--brand);
      box-shadow: 0 14px 32px rgba(109, 144, 55, 0.22), 0 3px 10px rgba(109, 144, 55, 0.12);
    }

    .action-button.like { color: var(--brand-strong); border-color: rgba(109, 144, 55, 0.35); }

    .state-panel {
      flex: 1;
      width: min(100%, 420px);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 14px;
      padding: 30px;
      text-align: center;
    }

    .state-icon,
    .dialog-icon {
      width: 68px;
      height: 68px;
      display: grid;
      place-items: center;
      border-radius: 22px;
      color: var(--text-primary);
      background: var(--brand);
    }

    .state-panel h2,
    .dialog h2 { margin: 0; color: var(--text-primary); font-size: 26px; letter-spacing: -0.035em; }
    .state-panel p,
    .dialog p { margin: 0; max-width: 300px; color: var(--text-secondary); font-size: 15px; line-height: 1.55; }

    .spinner {
      width: 42px;
      height: 42px;
      border: 3px solid var(--surface-3);
      border-top-color: var(--brand);
      border-radius: 50%;
      animation: spin 800ms linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .primary-button,
    .secondary-button {
      min-height: 48px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 0 22px;
      border-radius: 14px;
      font: inherit;
      font-size: 14px;
      font-weight: 700;
      cursor: pointer;
    }

    .primary-button { border: 0; color: var(--text-primary); background: var(--brand); }
    .primary-button.wide { width: 100%; }
    .secondary-button { width: 100%; border: 0; color: var(--text-secondary); background: transparent; }

    .overlay {
      position: fixed;
      z-index: 1000;
      inset: 0;
      display: grid;
      place-items: center;
      padding: 22px;
      background: rgba(31, 33, 30, 0.72);
      backdrop-filter: blur(10px);
    }

    .dialog {
      width: min(100%, 370px);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 14px;
      padding: 26px;
      border-radius: 26px;
      color: var(--text-primary);
      background: var(--surface);
      box-shadow: 0 24px 70px rgba(0, 0, 0, 0.28);
      text-align: center;
    }

    .dialog-icon.premium { color: var(--text-primary); }
    .eyebrow { color: var(--brand-strong) !important; font-size: 12px !important; font-weight: 700; text-transform: uppercase; letter-spacing: 0.1em; }

    .match-photo {
      width: 112px;
      height: 112px;
      display: grid;
      place-items: center;
      overflow: hidden;
      border-radius: 50%;
      border: 4px solid var(--brand);
      color: var(--text-primary);
      background: var(--surface-2);
      font-size: 38px;
      font-weight: 700;
    }

    .match-photo img { width: 100%; height: 100%; object-fit: cover; }

    .toast {
      position: fixed;
      z-index: 2000;
      left: 50%;
      bottom: calc(env(safe-area-inset-bottom, 0px) + var(--mobile-bottombar-height) + 16px);
      transform: translateX(-50%);
      max-width: min(88vw, 420px);
      padding: 12px 18px;
      border-radius: 14px;
      color: white;
      background: rgba(47, 48, 49, 0.94);
      box-shadow: 0 12px 32px var(--shadow-md);
      font-size: 13px;
      text-align: center;
    }

    @media (min-width: 768px) {
      .discover { padding-bottom: 0; }
      .discover-header { width: min(100%, 560px); margin: 0 auto; padding: 12px 18px; background: transparent; }
      h1 { font-size: 30px; }
      .deck-area { padding-bottom: 18px; }
      .cards-stack { width: min(100%, 420px); max-height: 720px; }
      .action-buttons { min-height: 96px; }
      .toast { bottom: 24px; }
    }

    @media (max-height: 740px) {
      .discover-header { min-height: var(--mobile-topbar-height); }
      h1 { font-size: 26px; }
      .icon-button { width: 36px; height: 36px; }
      .deck-area { padding-top: 4px; gap: 5px; }
      .action-buttons { min-height: 66px; }
      .action-button { width: 52px; height: 52px; }
      .action-button.standout { width: 60px; height: 60px; }
    }
  `]
})
export class DiscoverComponent implements OnInit, OnDestroy {
  @ViewChildren(SwipeCardComponent) swipeCards!: QueryList<SwipeCardComponent>;

  private profileService = inject(ProfileService);
  private reports = inject(ReportService);
  private swipeService = inject(SwipeService);
  private router = inject(Router);

  profiles = signal<DeckCard[]>([]);
  currentIndex = signal(0);
  leavingId = signal<string | null>(null);
  loading = signal(true);
  matchedProfile = signal<DeckCard | null>(null);
  retrying = signal(false);
  showPremiumModal = signal(false);
  toast = signal<string | null>(null);
  private toastTimer: ReturnType<typeof setTimeout> | null = null;
  private nextSuperLike = false;
  private myProfileId: string | null = null;
  private generation: number | null = null;
  private nextCursor: string | null = null;
  private pollStartedAt = 0;
  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private requestInFlight = false;

  visibleProfiles(): DeckCard[] {
    const upcoming = this.profiles().slice(this.currentIndex(), this.currentIndex() + 3);
    const leavingId = this.leavingId();
    if (!leavingId) return upcoming;
    const leaving = this.profiles().find(profile => profile.profileId === leavingId);
    if (!leaving || upcoming.some(profile => profile.profileId === leavingId)) return upcoming;
    return [leaving, ...upcoming];
  }

  wrapperClass(profile: DeckCard, index: number): string {
    if (this.leavingId() === profile.profileId) return 'leaving';
    const stackIndex = this.leavingId() ? index - 1 : index;
    return 'z' + (3 - stackIndex);
  }

  isFrontCard(profile: DeckCard, index: number): boolean {
    const role = this.wrapperClass(profile, index);
    return role === 'z3' || role === 'leaving';
  }

  frontCards(): DeckCard[] {
    return this.visibleProfiles().filter((profile, index) => this.isFrontCard(profile, index));
  }

  ngOnInit(): void {
    this.profileService.getMe().subscribe({
      next: profile => { this.myProfileId = profile.profileId; },
      error: (error: HttpErrorResponse) => {
        if (error.status === 429) this.showToast('Too many requests. Please wait a moment.');
        else this.router.navigate(['/profile/edit']);
      }
    });
    this.loadDeck();
  }

  ngOnDestroy(): void {
    if (this.pollTimer) clearTimeout(this.pollTimer);
    if (this.toastTimer) clearTimeout(this.toastTimer);
  }

  loadDeck(refresh = false): void {
    this.pollStartedAt = Date.now();
    this.retrying.set(false);
    this.loading.set(this.profiles().length === 0);
    this.requestPage(undefined, true, refresh);
  }

  reportProfile(profile: DeckCard): void {
    const details = window.prompt(`Why are you reporting ${profile.name}?`);
    if (!details?.trim()) return;
    this.reports.reportProfile(profile.profileId, 'other', details.trim()).subscribe({
      next: () => this.showToast('Thanks. We will review this profile.'),
      error: (error: HttpErrorResponse) => {
        if (error.status === 429) this.showToast('Too many requests. Please wait a moment.');
        else this.showToast('Could not send the report. Please try again.');
      }
    });
  }

  onSwipeCommitted(profile: DeckCard): void {
    if (this.leavingId() || this.profiles()[this.currentIndex()]?.profileId !== profile.profileId) {
      return;
    }
    this.leavingId.set(profile.profileId);
    this.currentIndex.update(index => index + 1);
  }

  onSwipe(direction: 'left' | 'right', profile: DeckCard): void {
    const isSuper = this.nextSuperLike;
    this.nextSuperLike = false;
    if (!this.myProfileId) {
      this.finishDeparture(profile);
      return;
    }

    this.swipeService.swipe({
      profile1Id: this.myProfileId,
      profile2Id: profile.profileId,
      decision: direction === 'right',
      isSuper
    }).subscribe({
      next: () => this.finishDeparture(profile),
      error: (error: HttpErrorResponse) => {
        if (isSuper && error.status === 403) {
          this.restoreRejectedSwipe(profile);
          this.showPremiumModal.set(true);
          return;
        }
        if (error.status === 429) {
          this.restoreRejectedSwipe(profile);
          const retryAfter = Number(error.headers?.get('X-RateLimit-Retry-After-Seconds') || 0);
          this.showToast(retryAfter > 0
            ? `You’re moving fast. Try again in about ${Math.ceil(retryAfter / 60)} min.`
            : 'You’re moving fast. Take a moment before the next profile.');
          return;
        }
        this.finishDeparture(profile);
      }
    });
  }

  swipeLeft(): void {
    if (this.leavingId()) return;
    this.topCard()?.triggerSwipe('left');
  }
  swipeRight(): void {
    if (this.leavingId()) return;
    this.topCard()?.triggerSwipe('right');
  }

  private topCard(): SwipeCardComponent | undefined {
    const leaving = this.leavingId();
    return this.swipeCards?.find(card => card.profile.profileId !== leaving)
      ?? this.swipeCards?.first;
  }

  private cardFor(profileId: string): SwipeCardComponent | undefined {
    return this.swipeCards?.find(card => card.profile.profileId === profileId);
  }

  private resetCurrentCard(): void {
    this.topCard()?.resetSwipe();
  }

  superLike(): void {
    if (this.leavingId() || !this.profiles()[this.currentIndex()]) return;
    this.nextSuperLike = true;
    const card = this.topCard();
    if (card) card.triggerSwipe('up');
    else this.nextSuperLike = false;
  }

  refresh(): void { this.loadDeck(true); }
  retryNow(): void {
    this.pollStartedAt = Date.now();
    this.retrying.set(false);
    this.requestPage(undefined, true);
  }
  dismissPremiumModal(): void { this.showPremiumModal.set(false); }
  dismissMatch(): void { this.matchedProfile.set(null); }
  goToFilters(): void { this.router.navigate(['/profile/edit']); }
  goToPremium(): void { this.dismissPremiumModal(); this.router.navigate(['/profile']); }
  goToMatches(): void { this.dismissMatch(); this.router.navigate(['/matches']); }

  private showToast(message: string): void {
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toast.set(message);
    this.toastTimer = setTimeout(() => this.toast.set(null), 4000);
  }

  private requestPage(cursor?: string, reset = false, refresh = false): void {
    if (this.requestInFlight) return;
    this.requestInFlight = true;
    this.profileService.getMyDeck(cursor, 20, refresh).subscribe({
      next: response => {
        this.requestInFlight = false;
        if (isBuildingDeck(response)) {
          this.schedulePoll();
          return;
        }
        this.applyPage(response, reset);
      },
      error: (error: HttpErrorResponse) => {
        this.requestInFlight = false;
        this.loading.set(false);
        if (error.status === 429) {
          this.showToast('Too many requests. Please wait before refreshing.');
          return;
        }
        if (error.status === 404) {
          this.router.navigate(['/profile/edit']);
          return;
        }
        this.retrying.set(true);
        this.schedulePoll();
      }
    });
  }

  private applyPage(page: DeckPage, reset: boolean): void {
    const existing = this.profiles();
    const leaving = this.leavingId()
      ? existing.find(profile => profile.profileId === this.leavingId()) ?? null
      : null;
    const current = existing[this.currentIndex()] ?? null;
    const generationChanged = this.generation !== null && this.generation !== page.generation;
    const shouldReset = reset || generationChanged || page.cursorReset;

    if (shouldReset) {
      const head = [leaving, current].filter((profile): profile is DeckCard => profile != null);
      const next = this.uniqueByProfileId([...head, ...page.items]);
      this.profiles.set(next);
      const currentId = current?.profileId;
      this.currentIndex.set(currentId
        ? Math.max(0, next.findIndex(profile => profile.profileId === currentId))
        : 0);
    } else {
      this.profiles.set(this.uniqueByProfileId([...existing, ...page.items]));
    }

    this.generation = page.generation;
    this.nextCursor = page.nextCursor;
    this.loading.set(false);
    this.retrying.set(false);

    // REFRESHING is intentionally not rendered as degraded; the current card
    // stays in place while a newer generation is polled in the background.
    if (page.state === 'REFRESHING') this.schedulePoll();
  }

  private schedulePoll(): void {
    if (this.pollTimer) clearTimeout(this.pollTimer);
    const elapsed = Date.now() - this.pollStartedAt;
    const initialWindow = elapsed < 30_000;
    this.retrying.set(!initialWindow);
    this.loading.set(initialWindow && this.profiles().length === 0);
    this.pollTimer = setTimeout(
      () => this.requestPage(undefined, true),
      initialWindow ? 2_000 : 10_000
    );
  }

  private advanceCard(): void {
    const remaining = this.profiles().length - this.currentIndex();
    if (remaining <= 3 && this.nextCursor) {
      const cursor = this.nextCursor;
      this.nextCursor = null;
      this.requestPage(cursor, false);
    }
  }

  private finishDeparture(profile: DeckCard): void {
    if (this.leavingId() === profile.profileId) {
      this.leavingId.set(null);
    }
    this.advanceCard();
  }

  private restoreRejectedSwipe(profile: DeckCard): void {
    if (this.leavingId() === profile.profileId) {
      this.currentIndex.update(index => Math.max(0, index - 1));
      this.leavingId.set(null);
    }
    this.cardFor(profile.profileId)?.resetSwipe();
  }

  private uniqueByProfileId(cards: DeckCard[]): DeckCard[] {
    const unique = new Map<string, DeckCard>();
    cards.forEach(card => {
      if (!unique.has(card.profileId)) unique.set(card.profileId, card);
    });
    return [...unique.values()];
  }
}
