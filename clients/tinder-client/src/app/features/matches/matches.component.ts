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
      <header class="page-header">
        <div class="header-copy">
          <span class="eyebrow">Connections</span>
          <div class="title-row">
            <h1>Messages</h1>
            @if (newMatches().length + conversations().length > 0) {
              <span class="total-count">{{ newMatches().length + conversations().length }}</span>
            }
          </div>
          <p>New connections and ongoing conversations, together.</p>
        </div>
      </header>

      @if (loading()) {
        <div class="loading">
          <div class="spinner" role="status" aria-label="Loading conversations"></div>
        </div>
      } @else if (newMatches().length === 0 && conversations().length === 0) {
        <div class="empty">
          <div class="empty-visual" aria-hidden="true">
            <span class="person person-one"></span>
            <span class="person person-two"></span>
            <div class="message-mark">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z"/>
                <path d="M8 10h8M8 14h5"/>
              </svg>
            </div>
          </div>
          <span class="eyebrow">Your circle starts here</span>
          <h2 class="empty-title">No conversations yet</h2>
          <p class="empty-text">When the interest is mutual, your new connection will appear here.</p>
          <button class="discover-btn" type="button" (click)="goDiscover()">Discover people</button>
        </div>
      } @else {
        <div class="content">
          @if (newMatches().length > 0) {
            <section class="section new-match-section">
              <div class="section-heading">
                <div>
                  <span class="eyebrow">Just matched</span>
                  <h2>New connections</h2>
                </div>
                <span class="section-count">{{ newMatches().length }}</span>
              </div>
              <div class="new-matches-scroll">
                @for (match of newMatches(); track match.matchId) {
                  <button
                    type="button"
                    class="match-bubble"
                    [class.starting]="startingChat() === match.matchId"
                    [attr.aria-busy]="startingChat() === match.matchId"
                    [attr.aria-label]="'Start a conversation with ' + firstName(match.otherProfile?.name)"
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
                          <div class="bubble-spinner" role="status" aria-label="Starting conversation"></div>
                        }
                      </div>
                      <span class="new-dot"></span>
                    </div>
                    <p class="bubble-name">{{ firstName(match.otherProfile?.name) }}</p>
                    <span class="bubble-hint">Say hello</span>
                  </button>
                }
              </div>
            </section>
          }

          @if (conversations().length > 0) {
            <section class="section message-section">
              <div class="section-heading">
                <div>
                  <span class="eyebrow">In touch</span>
                  <h2>Conversations</h2>
                </div>
              </div>
              <div class="conversations-list">
                @for (item of conversations(); track item.conversationId) {
                  <button
                    type="button"
                    class="conv-item"
                    [class.unread]="item.unread"
                    [attr.aria-label]="'Open conversation with ' + (item.otherProfile?.name ?? 'user')"
                    (click)="openChat(item.conversationId)"
                  >
                    <div class="conv-avatar">
                      @if (item.otherProfile?.photos?.length) {
                        <img [src]="item.otherProfile!.photos[0].url" [alt]="item.otherProfile!.name" />
                      } @else {
                        <span>{{ item.otherProfile?.name?.[0] ?? '?' }}</span>
                      }
                      @if (item.otherProfile?.isActive) { <span class="presence-dot" aria-label="Active now"></span> }
                    </div>
                    <div class="conv-info">
                      <div class="conv-name-row">
                        <h3>{{ item.otherProfile?.name ?? 'User' }}</h3>
                        @if (item.lastMessageAt) {
                          <span class="conv-time">{{ formatTime(item.lastMessageAt) }}</span>
                        }
                      </div>
                      <div class="conv-preview-row">
                        <p [class.unread-text]="item.unread">{{ item.lastMessageText ?? 'Start the conversation' }}</p>
                      </div>
                    </div>
                    @if (item.unread) { <span class="unread-dot" aria-label="Unread message"></span> }
                    <svg class="conv-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m9 18 6-6-6-6"/></svg>
                  </button>
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
      padding-bottom: calc(env(safe-area-inset-bottom, 0px) + var(--mobile-bottombar-height));
      overflow: hidden;
    }

    /* ── Header ── */
    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      min-height: var(--mobile-topbar-height);
      padding: 0 10px;
      background: var(--header-surface);
      flex-shrink: 0;
      position: sticky;
      top: 0;
      z-index: 10;
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
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
        font-size: 19px;
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
      box-shadow: 0 6px 16px rgba(109, 144, 55, 0.24);
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
      display: grid;
      place-items: center;
      overflow: hidden;
      pointer-events: none;

      &::after {
        content: '';
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

    /* Fresh connections layout */
    .page-header {
      min-height: 62px;
      padding: 8px 18px;
      display: flex;
      align-items: center;
      flex-shrink: 0;
      position: relative;
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
    .total-count,
    .section-count {
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

    .empty {
      gap: 10px;
      padding: 44px 30px 76px;
    }
    .empty-visual {
      position: relative;
      width: 150px;
      height: 114px;
      margin-bottom: 16px;
    }
    .person {
      position: absolute;
      top: 10px;
      width: 76px;
      height: 76px;
      border-radius: 50%;
      border: 6px solid var(--bg);
      background: linear-gradient(145deg, var(--surface-3), var(--brand-soft));
      box-shadow: var(--shadow-float);
    }
    .person-one { left: 10px; }
    .person-two { right: 10px; background: linear-gradient(145deg, #d9b5a5, #8b5e52); }
    .message-mark {
      position: absolute;
      z-index: 2;
      left: 50%;
      bottom: 0;
      width: 48px;
      height: 48px;
      display: grid;
      place-items: center;
      border: 1px solid var(--brand-border);
      border-radius: 17px;
      background: var(--brand);
      color: #1b230f;
      box-shadow: 0 10px 24px var(--brand-glow);
      transform: translateX(-50%);
    }
    .message-mark svg { width: 23px; height: 23px; }
    .empty-title { margin: 2px 0 0; font-family: var(--font-editorial); font-size: 28px; font-weight: 600; letter-spacing: -0.04em; }
    .empty-text { max-width: 320px; margin: 0; }
    .discover-btn {
      min-height: 44px;
      margin-top: 10px;
      padding: 0 17px;
      border: 1px solid var(--brand-border);
      border-radius: 14px;
      background: var(--brand-soft);
      color: var(--brand-ink);
      font-size: 13px;
      font-weight: 800;
      cursor: pointer;
    }

    .content {
      width: min(100%, 920px);
      margin: 0 auto;
      padding: 14px 0 30px;
    }
    .section {
      margin: 0;
      overflow: visible;
      border: 0;
      border-radius: 0;
      background: transparent;
      box-shadow: none;
    }
    .section + .section { margin-top: 18px; }
    .section-heading {
      padding: 14px 18px 12px;
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 12px;
    }
    .section-heading .eyebrow { margin-bottom: 5px; }
    .section-heading h2 { margin: 0; color: var(--text-primary); font-size: 19px; font-weight: 700; letter-spacing: -0.03em; }

    .new-matches-scroll {
      gap: 18px;
      padding: 2px 18px 18px;
    }
    .match-bubble { gap: 5px; opacity: 1; }
    .match-bubble.starting { opacity: 1; }
    .bubble-ring {
      position: relative;
      padding: 3px;
      border: 1px solid var(--brand-border);
      background: var(--surface);
      box-shadow: var(--shadow-float);
    }
    .bubble-avatar {
      width: 72px;
      height: 72px;
      border: 2px solid var(--bg);
      background: linear-gradient(145deg, var(--surface-3), var(--brand-soft));
      color: var(--brand-ink);
      font-family: var(--font-editorial);
      font-size: 26px;
      font-weight: 600;
    }
    .new-dot {
      position: absolute;
      right: 1px;
      bottom: 7px;
      width: 13px;
      height: 13px;
      border: 3px solid var(--bg);
      border-radius: 50%;
      background: var(--brand);
    }
    .bubble-name { max-width: 82px; margin-top: 2px; font-size: 13px; font-weight: 700; }
    .bubble-hint { color: var(--text-muted); font-size: 10px; }

    .message-section { padding: 0 14px; }
    .message-section .section-heading { padding-left: 4px; padding-right: 4px; }
    .conversations-list {
      overflow: hidden;
      border: 1px solid var(--card-border);
      border-radius: 20px;
      background: var(--card-surface);
      box-shadow: var(--shadow-card);
    }
    .conv-item {
      width: 100%;
      min-height: 78px;
      padding: 11px 13px;
      gap: 12px;
      border: 0;
      border-bottom: 1px solid var(--border-light);
      border-radius: 0;
      background: transparent;
      color: inherit;
      font: inherit;
      text-align: left;
    }
    .conv-item.unread { background: linear-gradient(90deg, var(--brand-soft), transparent 42%); }
    .conv-avatar {
      position: relative;
      width: 54px;
      height: 54px;
      overflow: visible;
      background: linear-gradient(145deg, var(--surface-3), var(--brand-soft));
      color: var(--brand-ink);
      font-family: var(--font-editorial);
      font-weight: 600;
    }
    .conv-avatar img { border-radius: 50%; }
    .presence-dot {
      position: absolute;
      right: -1px;
      bottom: 2px;
      width: 12px;
      height: 12px;
      border: 3px solid var(--card-surface);
      border-radius: 50%;
      background: var(--brand);
    }
    .conv-name-row { margin-bottom: 4px; }
    .conv-name-row h3 { font-size: 15px; font-weight: 700; }
    .conv-time { font-size: 10px; }
    .conv-preview-row p { max-width: 100%; font-size: 13px; }
    .unread-dot { width: 7px; height: 7px; margin: 0; box-shadow: 0 0 0 3px var(--brand-soft); }
    .conv-chevron { width: 17px; height: 17px; flex: 0 0 auto; color: var(--text-muted); }

    @media (min-width: 768px) {
      .page-header {
        width: min(100%, 920px);
        min-height: 0;
        margin: 0 auto;
        padding: 42px 24px 22px;
        background: transparent;
        border: 0;
        backdrop-filter: none;
      }
      .page-header .eyebrow { display: block; margin-bottom: 8px; }
      .page-header p { display: block; margin: 7px 0 0; color: var(--text-muted); font-size: 14px; }
      .title-row h1 { font-size: 40px; }
      .content { max-width: 920px; padding: 4px 24px 50px; }
      .new-matches-scroll { flex-wrap: nowrap; overflow-x: auto; gap: 22px; padding-left: 4px; }
      .message-section { padding: 0; }
      .conv-item { min-height: 84px; padding: 13px 17px; }
      .conv-item:hover { background: var(--surface-2); }
      .conv-avatar { width: 58px; height: 58px; }
      .empty { padding-bottom: 40px; }
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
      next: conv => void this.navigateToCreatedChat(match.matchId, conv.id),
      error: () => this.startingChat.set(null)
    });
  }

  private async navigateToCreatedChat(matchId: string, conversationId: string): Promise<void> {
    try {
      await this.router.navigate(['/chat', conversationId]);
    } finally {
      if (this.startingChat() === matchId) {
        this.startingChat.set(null);
      }
    }
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
