import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, of, switchMap } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { MatchService, Match, ConversationDto } from '../../core/services/match.service';
import { ProfileService } from '../../core/services/profile.service';
import { Profile } from '../../core/models/profile.model';

interface NewMatch {
  matchId: string;
  myId: string;
  otherId: string;
  otherProfile: Profile | null;
}

interface ConversationWithProfile {
  conversationId: string;
  otherProfile: Profile | null;
  lastMessageText: string | null;
  lastMessageAt: string | null;
  unread: boolean;
}

const LAST_READ_PREFIX = 'lastRead_';

function getLastRead(conversationId: string): string | null {
  return localStorage.getItem(LAST_READ_PREFIX + conversationId);
}

export function markConversationRead(conversationId: string): void {
  localStorage.setItem(LAST_READ_PREFIX + conversationId, new Date().toISOString());
}

@Component({
  selector: 'app-matches',
  template: `
    <div class="matches-page">
      <header class="header">
        <div class="header-left"></div>
        <div class="logo">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="#ff4458">
            <path d="M17.66 11.2c-.23-.3-.51-.56-.77-.82-.67-.6-1.43-1.03-2.07-1.66C13.33 7.26 13 4.85 13.95 3c-.95.23-1.78.75-2.49 1.32-2.59 2.11-3.66 5.65-2.67 8.9.04.14.08.28.08.43 0 .28-.19.52-.45.57-.28.07-.53-.09-.63-.37-.04-.1-.06-.21-.09-.32C7.15 13 7 12.5 7 11.85c0-.58.16-1.2.44-1.7-1.16 1.27-1.86 2.97-1.86 4.77 0 3.31 2.69 6 6 6s6-2.69 6-6c0-1.88-.82-3.63-2.09-4.82z"/>
          </svg>
          <span class="logo-text">tinder</span>
        </div>
        <button class="header-icon-btn">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="24" height="24">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          </svg>
        </button>
      </header>

      @if (loading()) {
        <div class="loading">
          <div class="spinner"></div>
        </div>
      } @else if (newMatches().length === 0 && conversations().length === 0) {
        <div class="empty">
          <!-- Tinder card stack illustration -->
          <div class="card-illustration">
            <div class="card card-back-2"></div>
            <div class="card card-back-1"></div>
            <div class="card card-front">
              <div class="like-stamp">LIKE</div>
            </div>
          </div>

          <h2 class="empty-title">Start Swiping</h2>
          <p class="empty-text">
            Your confirmed matches will appear here.<br>
            You can message a match directly.
          </p>
        </div>
      } @else {
        <div class="content">

          @if (newMatches().length > 0) {
            <section class="section">
              <h2 class="section-title">
                New Matches
                <span class="badge">{{ newMatches().length }}</span>
              </h2>
              <div class="new-matches-scroll">
                @for (match of newMatches(); track match.matchId) {
                  <button
                    class="match-bubble"
                    [class.starting]="startingChat() === match.matchId"
                    (click)="startChat(match)"
                  >
                    <div class="bubble-ring">
                      <div class="bubble-avatar">
                        @if (match.otherProfile?.photos?.length) {
                          <img [src]="match.otherProfile!.photos[0].url" [alt]="match.otherProfile!.name" />
                        } @else {
                          <span>{{ match.otherProfile?.name?.[0] ?? '?' }}</span>
                        }
                        @if (startingChat() === match.matchId) {
                          <div class="bubble-spinner"></div>
                        }
                      </div>
                    </div>
                    <p class="bubble-name">{{ firstName(match.otherProfile?.name) }}</p>
                  </button>
                }
              </div>
            </section>
          }

          @if (conversations().length > 0) {
            <section class="section">
              <h2 class="section-title">Messages</h2>
              <div class="conversations-list">
                @for (item of conversations(); track item.conversationId) {
                  <div class="conv-item" [class.unread]="item.unread" (click)="openChat(item.conversationId)">
                    <div class="conv-avatar">
                      @if (item.otherProfile?.photos?.length) {
                        <img [src]="item.otherProfile!.photos[0].url" [alt]="item.otherProfile!.name" />
                      } @else {
                        <span>{{ item.otherProfile?.name?.[0] ?? '?' }}</span>
                      }
                    </div>
                    <div class="conv-info">
                      <div class="conv-name-row">
                        <h3>{{ item.otherProfile?.name ?? 'User' }}</h3>
                        @if (item.lastMessageAt) {
                          <span class="conv-time">{{ formatTime(item.lastMessageAt) }}</span>
                        }
                      </div>
                      <div class="conv-preview-row">
                        <p [class.unread-text]="item.unread">
                          {{ item.lastMessageText ?? 'Tap to open chat' }}
                        </p>
                        @if (item.unread) {
                          <span class="unread-dot"></span>
                        }
                      </div>
                    </div>
                  </div>
                }
              </div>
            </section>
          }

        </div>
      }
    </div>

  `,
  styles: [`
    .matches-page {
      display: flex;
      flex-direction: column;
      height: 100dvh;
      background: transparent;
      padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 88px);
      overflow: hidden;
    }

    /* ── Header ── */
    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 14px 18px 12px;
      background: var(--surface-glass);
      border-bottom: 1px solid var(--border);
      flex-shrink: 0;
      position: sticky;
      top: 0;
      z-index: 10;
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      box-shadow: 0 8px 20px var(--shadow-sm);
    }

    @media (min-width: 768px) {
      .matches-page {
        padding-bottom: 0;
        height: 100dvh;
      }

      .header {
        display: none;
      }

      .content {
        max-width: 680px;
        margin: 0 auto;
        width: 100%;
      }

      .new-matches-scroll {
        flex-wrap: wrap;
        overflow-x: visible;
        gap: 16px;
        padding-bottom: 12px;
      }

      .conv-item {
        &:hover {
          background: var(--surface-2);
          cursor: pointer;
        }
      }
    }

    .header-left {
      width: 36px;
    }

    .logo {
      display: flex;
      align-items: center;
      gap: 5px;

      .logo-text {
        font-size: 22px;
        font-weight: 800;
        color: var(--brand);
        letter-spacing: -0.5px;
      }
    }

    .header-icon-btn {
      background: none;
      border: none;
      cursor: pointer;
      color: #9e9ea0;
      padding: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      width: 36px;
      height: 36px;
      transition: background 0.15s;

      &:active { background: var(--surface-2); }
    }

    /* ── Loading ── */
    .loading {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .spinner {
      width: 36px;
      height: 36px;
      border: 3px solid var(--border);
      border-top: 3px solid var(--brand);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    /* ── Empty state ── */
    .empty {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0;
      padding: 40px 40px 60px;
      text-align: center;
    }

    /* Card stack illustration matching the Tinder screenshot */
    .card-illustration {
      position: relative;
      width: 160px;
      height: 180px;
      margin-bottom: 32px;
    }

    .card {
      position: absolute;
      border-radius: 14px;
      background: var(--surface);
    }

    .card-back-2 {
      width: 110px;
      height: 145px;
      bottom: 0;
      left: 50%;
      transform: translateX(-50%) rotate(-6deg) translateY(4px);
      background: var(--bg);
      border: 2px solid var(--border);
    }

    .card-back-1 {
      width: 115px;
      height: 150px;
      bottom: 0;
      left: 50%;
      transform: translateX(-50%) rotate(-2deg) translateY(2px);
      background: var(--bg);
      border: 2px solid var(--border);
    }

    .card-front {
      width: 120px;
      height: 155px;
      bottom: 0;
      left: 50%;
      transform: translateX(-50%) rotate(8deg);
      background: rgba(39, 209, 162, 0.14);
      border: 2.5px solid var(--like);
      display: flex;
      align-items: center;
      justify-content: center;
    }

    [data-theme="dark"] .card-front {
      background: #0d2a1a;
    }

    .like-stamp {
      font-size: 22px;
      font-weight: 800;
      color: var(--like);
      border: 3px solid var(--like);
      border-radius: 6px;
      padding: 4px 12px;
      letter-spacing: 2px;
      transform: rotate(-8deg);
    }

    .empty-title {
      margin: 0 0 12px;
      font-size: 22px;
      font-weight: 800;
      color: var(--text-primary);
      letter-spacing: -0.3px;
    }

    .empty-text {
      margin: 0;
      font-size: 15px;
      color: var(--text-muted);
      line-height: 1.55;
      max-width: 280px;
    }

    /* ── Content ── */
    .content {
      flex: 1;
      overflow-y: auto;
      padding: 8px 12px 16px;
    }

    .section {
      background: var(--surface);
      border-radius: 20px;
      margin: 12px 4px;
      overflow: hidden;
      border: 1px solid var(--border);
      box-shadow: 0 14px 30px var(--shadow-sm);
    }

    .section-title {
      margin: 0;
      padding: 16px 20px 12px;
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 1px;
      color: var(--text-muted);
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .badge {
      background: var(--brand);
      color: #fff;
      font-size: 11px;
      font-weight: 700;
      border-radius: 10px;
      padding: 2px 7px;
      letter-spacing: 0;
      text-transform: none;
    }

    /* ── New Matches horizontal scroll ── */
    .new-matches-scroll {
      display: flex;
      gap: 14px;
      padding: 0 18px 18px;
      overflow-x: auto;
      scrollbar-width: none;
      -ms-overflow-style: none;

      &::-webkit-scrollbar { display: none; }
    }

    .match-bubble {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
      background: none;
      border: none;
      padding: 0;
      cursor: pointer;
      flex-shrink: 0;
      transition: opacity 0.2s, transform 0.2s;

      &.starting { opacity: 0.6; }
      &:active { transform: scale(0.95); }
    }

    .bubble-ring {
      padding: 2.5px;
      border-radius: 50%;
      background: var(--brand-gradient);
      box-shadow: 0 6px 16px rgba(255, 68, 88, 0.28);
    }

    .bubble-avatar {
      position: relative;
      width: 68px;
      height: 68px;
      border-radius: 50%;
      overflow: hidden;
      background: var(--border);
      border: 2.5px solid var(--surface);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      font-weight: 700;
      color: var(--text-secondary);

      img { width: 100%; height: 100%; object-fit: cover; }
    }

    .bubble-spinner {
      position: absolute;
      inset: 0;
      border-radius: 50%;
      background: rgba(0,0,0,0.3);

      &::after {
        content: '';
        position: absolute;
        top: 50%; left: 50%;
        transform: translate(-50%, -50%);
        width: 20px; height: 20px;
        border: 2px solid rgba(255,255,255,0.4);
        border-top: 2px solid #fff;
        border-radius: 50%;
        animation: spin 0.7s linear infinite;
      }
    }

    .bubble-name {
      margin: 0;
      font-size: 12px;
      font-weight: 600;
      color: var(--text-primary);
      max-width: 72px;
      text-align: center;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    /* ── Conversations ── */
    .conversations-list {
      border-top: 1px solid var(--border);
    }

    .conv-item {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 12px 20px;
      cursor: pointer;
      transition: background 0.15s;
      border-bottom: 1px solid var(--border-light);

      &:last-child { border-bottom: none; }
      &:active { background: var(--surface-2); }
    }

    .conv-avatar {
      width: 56px;
      height: 56px;
      border-radius: 50%;
      overflow: hidden;
      background: var(--brand-gradient);
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      font-weight: 700;
      color: #fff;

      img { width: 100%; height: 100%; object-fit: cover; }
    }

    .conv-info { flex: 1; min-width: 0; }

    .conv-name-row {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 3px;

      h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 600;
        color: var(--text-primary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }

    .conv-time {
      font-size: 12px;
      color: var(--text-muted);
      flex-shrink: 0;
    }

    .conv-preview-row {
      display: flex;
      align-items: center;
      gap: 6px;

      p {
        margin: 0;
        font-size: 14px;
        color: var(--text-muted);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;

        &.unread-text {
          color: var(--text-primary);
          font-weight: 600;
        }
      }
    }

    .unread-dot {
      width: 9px;
      height: 9px;
      border-radius: 50%;
      background: var(--brand);
      flex-shrink: 0;
      margin-left: auto;
    }

  `]
})
export class MatchesComponent implements OnInit, OnDestroy {
  private matchService = inject(MatchService);
  private profileService = inject(ProfileService);
  private router = inject(Router);

  newMatches = signal<NewMatch[]>([]);
  conversations = signal<ConversationWithProfile[]>([]);
  loading = signal(true);
  startingChat = signal<string | null>(null);

  private myId: string | null = null;
  private pollInterval: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.loadData(true);
    this.pollInterval = setInterval(() => this.loadData(false), 8000);
  }

  ngOnDestroy(): void {
    if (this.pollInterval !== null) {
      clearInterval(this.pollInterval);
    }
  }

  private loadData(showSpinner: boolean): void {
    if (showSpinner) this.loading.set(true);
    this.profileService.getMe().pipe(
      switchMap(me => {
        this.myId = me.profileId;
        return forkJoin({
          matches: this.matchService.getMatches(me.profileId).pipe(catchError(() => of([] as Match[]))),
          chats: this.matchService.getMyChats(me.profileId).pipe(catchError(() => of([] as ConversationDto[])))
        }).pipe(
          switchMap(({ matches, chats }) => {
            const chattedPairs = new Set(
              chats.map(c => [c.participant1Id, c.participant2Id].sort().join('|'))
            );

            const unmatchedMatches = matches.filter(m => {
              const key = [m.profile1Id, m.profile2Id].sort().join('|');
              return !chattedPairs.has(key);
            });

            const otherMatchId = (m: Match) =>
              m.profile1Id === me.profileId ? m.profile2Id : m.profile1Id;

            const otherChatId = (c: ConversationDto) =>
              c.participant1Id === me.profileId ? c.participant2Id : c.participant1Id;

            const newMatchObs = unmatchedMatches.length
              ? forkJoin(unmatchedMatches.map(m =>
                  this.profileService.getProfile(otherMatchId(m)).pipe(
                    map(profile => ({
                      matchId: m.id,
                      myId: me.profileId,
                      otherId: otherMatchId(m),
                      otherProfile: profile
                    } as NewMatch)),
                    catchError(() => of({
                      matchId: m.id,
                      myId: me.profileId,
                      otherId: otherMatchId(m),
                      otherProfile: null
                    } as NewMatch))
                  )
                ))
              : of([] as NewMatch[]);

            const chatObs = chats.length
              ? forkJoin(chats.map(conv =>
                  this.profileService.getProfile(otherChatId(conv)).pipe(
                    map(profile => this.buildConversationItem(conv, profile, me.profileId)),
                    catchError(() => of(this.buildConversationItem(conv, null, me.profileId)))
                  )
                ))
              : of([] as ConversationWithProfile[]);

            return forkJoin({ newMatches: newMatchObs, conversations: chatObs });
          })
        );
      })
    ).subscribe({
      next: ({ newMatches, conversations }) => {
        this.newMatches.set(newMatches);
        this.conversations.set(conversations);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  private buildConversationItem(
    conv: ConversationDto,
    profile: Profile | null,
    myProfileId: string
  ): ConversationWithProfile {
    const lm = conv.lastMessage;
    let lastMessageText: string | null = null;
    let unread = false;

    if (lm) {
      lastMessageText = lm.messageType === 'IMAGE' ? '📷 Photo' : (lm.text ?? null);

      if (lm.senderId !== myProfileId) {
        const lastRead = getLastRead(conv.conversationId);
        unread = !lastRead || new Date(lm.createdAt) > new Date(lastRead);
      }
    }

    return {
      conversationId: conv.conversationId,
      otherProfile: profile,
      lastMessageText,
      lastMessageAt: lm?.createdAt ?? null,
      unread
    };
  }

  startChat(match: NewMatch): void {
    if (this.startingChat()) return;
    this.startingChat.set(match.matchId);
    this.matchService.createConversation(match.myId, match.otherId).subscribe({
      next: conv => this.router.navigate(['/chat', conv.id]),
      error: () => this.startingChat.set(null)
    });
  }

  openChat(conversationId: string): void {
    markConversationRead(conversationId);
    this.router.navigate(['/chat', conversationId]);
  }

  goDiscover(): void {
    this.router.navigate(['/discover']);
  }

  firstName(name: string | undefined): string {
    return name?.split(' ')[0] ?? 'User';
  }

  formatTime(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const now = new Date();
    const isToday = date.toDateString() === now.toDateString();
    return isToday
      ? date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      : date.toLocaleDateString([], { month: 'short', day: 'numeric' });
  }
}
