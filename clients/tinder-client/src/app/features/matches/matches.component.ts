import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, of, switchMap } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { MatchService, Match } from '../../core/services/match.service';
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
}

@Component({
  selector: 'app-matches',
  template: `
    <div class="matches-page">
      <header class="header">
        <h1>Matches</h1>
      </header>

      @if (loading()) {
        <div class="loading">
          <div class="spinner"></div>
        </div>
      } @else if (newMatches().length === 0 && conversations().length === 0) {
        <div class="empty">
          <div class="empty-icon">💌</div>
          <h3>No matches yet</h3>
          <p>Keep swiping to find your matches!</p>
          <button class="btn-primary" (click)="goDiscover()">Start Swiping</button>
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
                  <div class="conv-item" (click)="openChat(item.conversationId)">
                    <div class="conv-avatar">
                      @if (item.otherProfile?.photos?.length) {
                        <img [src]="item.otherProfile!.photos[0].url" [alt]="item.otherProfile!.name" />
                      } @else {
                        <span>{{ item.otherProfile?.name?.[0] ?? '?' }}</span>
                      }
                    </div>
                    <div class="conv-info">
                      <h3>{{ item.otherProfile?.name ?? 'User' }}</h3>
                      <p>Tap to open chat</p>
                    </div>
                    <div class="conv-arrow">›</div>
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
      height: 100vh;
      background: #f5f5f5;
      padding-bottom: 70px;
      overflow: hidden;
    }

    .header {
      padding: 20px 20px 16px;
      background: #fff;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06);
      flex-shrink: 0;

      h1 {
        margin: 0;
        font-size: 24px;
        font-weight: 700;
        color: #333;
      }
    }

    .loading {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 3px solid #f0f0f0;
      border-top: 3px solid #fd5564;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .empty {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 14px;
      text-align: center;
      padding: 40px;

      .empty-icon { font-size: 64px; }
      h3 { margin: 0; font-size: 20px; color: #333; }
      p { margin: 0; color: #888; }
    }

    .btn-primary {
      background: linear-gradient(135deg, #fd5564, #ff8a00);
      color: #fff;
      border: none;
      border-radius: 30px;
      padding: 12px 32px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
    }

    .content {
      flex: 1;
      overflow-y: auto;
    }

    .section {
      background: #fff;
      margin-bottom: 8px;
    }

    .section-title {
      margin: 0;
      padding: 16px 20px 12px;
      font-size: 13px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: #999;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .badge {
      background: linear-gradient(135deg, #fd5564, #ff8a00);
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
      gap: 16px;
      padding: 4px 20px 20px;
      overflow-x: auto;
      scrollbar-width: none;
      -ms-overflow-style: none;

      &::-webkit-scrollbar { display: none; }
    }

    .match-bubble {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      background: none;
      border: none;
      padding: 0;
      cursor: pointer;
      flex-shrink: 0;
      opacity: 1;
      transition: opacity 0.2s;

      &.starting { opacity: 0.6; }
      &:active { transform: scale(0.95); }
    }

    .bubble-ring {
      padding: 3px;
      border-radius: 50%;
      background: linear-gradient(135deg, #fd5564, #ff8a00);
    }

    .bubble-avatar {
      position: relative;
      width: 70px;
      height: 70px;
      border-radius: 50%;
      overflow: hidden;
      background: #e0e0e0;
      border: 2px solid #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      font-weight: 700;
      color: #666;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .bubble-spinner {
      position: absolute;
      inset: 0;
      border-radius: 50%;
      background: rgba(0,0,0,0.3);
      display: flex;
      align-items: center;
      justify-content: center;

      &::after {
        content: '';
        width: 22px;
        height: 22px;
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
      color: #333;
      max-width: 76px;
      text-align: center;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    /* ── Conversations list ── */
    .conversations-list {
      padding: 0 0 8px;
    }

    .conv-item {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 12px 20px;
      cursor: pointer;
      transition: background 0.15s;

      &:active { background: #f9f9f9; }
    }

    .conv-avatar {
      width: 56px;
      height: 56px;
      border-radius: 50%;
      overflow: hidden;
      background: linear-gradient(135deg, #fd5564, #ff8a00);
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      font-weight: 700;
      color: #fff;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .conv-info {
      flex: 1;
      min-width: 0;

      h3 {
        margin: 0 0 3px;
        font-size: 16px;
        font-weight: 600;
        color: #222;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      p {
        margin: 0;
        font-size: 14px;
        color: #aaa;
      }
    }

    .conv-arrow {
      color: #ddd;
      font-size: 22px;
      font-weight: 300;
      flex-shrink: 0;
    }
  `]
})
export class MatchesComponent implements OnInit {
  private matchService = inject(MatchService);
  private profileService = inject(ProfileService);
  private router = inject(Router);

  newMatches = signal<NewMatch[]>([]);
  conversations = signal<ConversationWithProfile[]>([]);
  loading = signal(true);
  startingChat = signal<string | null>(null);

  ngOnInit(): void {
    this.profileService.getMe().pipe(
      switchMap(me =>
        forkJoin({
          matches: this.matchService.getMatches(me.profileId).pipe(catchError(() => of([] as Match[]))),
          chats: this.matchService.getMyChats(me.profileId).pipe(catchError(() => of([])))
        }).pipe(
          switchMap(({ matches, chats }) => {
            // Build a set of participant pairs that already have conversations
            const chattedPairs = new Set(
              chats.map(c => [c.participant1Id, c.participant2Id].sort().join('|'))
            );

            const unmatchedMatches = matches.filter(m => {
              const key = [m.profile1Id, m.profile2Id].sort().join('|');
              return !chattedPairs.has(key);
            });

            const otherMatchId = (m: Match) =>
              m.profile1Id === me.profileId ? m.profile2Id : m.profile1Id;

            const otherChatId = (c: { participant1Id: string; participant2Id: string }) =>
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
                    map(profile => ({ conversationId: conv.conversationId, otherProfile: profile })),
                    catchError(() => of({ conversationId: conv.conversationId, otherProfile: null }))
                  )
                ))
              : of([] as ConversationWithProfile[]);

            return forkJoin({ newMatches: newMatchObs, conversations: chatObs });
          })
        )
      )
    ).subscribe({
      next: ({ newMatches, conversations }) => {
        this.newMatches.set(newMatches);
        this.conversations.set(conversations);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
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
    this.router.navigate(['/chat', conversationId]);
  }

  goDiscover(): void {
    this.router.navigate(['/discover']);
  }

  firstName(name: string | undefined): string {
    return name?.split(' ')[0] ?? 'User';
  }
}
