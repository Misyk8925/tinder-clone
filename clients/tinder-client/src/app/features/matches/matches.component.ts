import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatchService, Match } from '../../core/services/match.service';

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
      } @else if (matches().length === 0) {
        <div class="empty">
          <div class="empty-icon">💌</div>
          <h3>No matches yet</h3>
          <p>Keep swiping to find your matches!</p>
          <button class="btn-primary" (click)="goDiscover()">Start Swiping</button>
        </div>
      } @else {
        <div class="matches-list">
          @for (match of matches(); track match.id) {
            <div class="match-item" (click)="openChat(match)">
              <div class="avatar">
                @if (match.profile?.photos?.length) {
                  <img [src]="match.profile!.photos[0].url" [alt]="match.profile!.name" />
                } @else {
                  <span>{{ match.profile?.name?.[0] ?? '?' }}</span>
                }
                <div class="online-dot"></div>
              </div>
              <div class="match-info">
                <h3>{{ match.profile?.name ?? 'User' }}</h3>
                <p>{{ match.profile?.age }} years old</p>
                <span class="match-date">Matched {{ formatDate(match.matchedAt) }}</span>
              </div>
              <div class="chat-arrow">›</div>
            </div>
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
    }

    .header {
      padding: 20px;
      background: #fff;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06);

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
      width: 40px; height: 40px;
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

    .matches-list {
      padding: 12px;
      overflow-y: auto;
      flex: 1;
    }

    .match-item {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 14px 16px;
      background: #fff;
      border-radius: 16px;
      margin-bottom: 10px;
      cursor: pointer;
      box-shadow: 0 2px 8px rgba(0,0,0,0.05);
      transition: transform 0.15s;

      &:active { transform: scale(0.98); }
    }

    .avatar {
      position: relative;
      width: 60px;
      height: 60px;
      border-radius: 50%;
      overflow: hidden;
      background: linear-gradient(135deg, #fd5564, #ff8a00);
      flex-shrink: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      font-weight: 700;
      color: #fff;

      img { width: 100%; height: 100%; object-fit: cover; }
    }

    .online-dot {
      position: absolute;
      bottom: 2px;
      right: 2px;
      width: 14px;
      height: 14px;
      background: #00d26a;
      border: 2px solid #fff;
      border-radius: 50%;
    }

    .match-info {
      flex: 1;

      h3 { margin: 0 0 2px; font-size: 16px; font-weight: 600; color: #333; }
      p { margin: 0 0 4px; font-size: 14px; color: #666; }
    }

    .match-date {
      font-size: 12px;
      color: #aaa;
    }

    .chat-arrow {
      color: #ccc;
      font-size: 24px;
      font-weight: 300;
    }
  `]
})
export class MatchesComponent implements OnInit {
  private matchService = inject(MatchService);
  private router = inject(Router);

  matches = signal<Match[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.matchService.getMatches().subscribe({
      next: (data) => {
        this.matches.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.matches.set([]);
        this.loading.set(false);
      }
    });
  }

  openChat(match: Match): void {
    const myId = match.profile1Id;
    const otherId = match.profile2Id;
    this.matchService.createConversation(myId, otherId).subscribe({
      next: (conv) => this.router.navigate(['/chat', conv.id]),
      error: () => {}
    });
  }

  goDiscover(): void {
    this.router.navigate(['/discover']);
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
    if (diffDays === 0) return 'today';
    if (diffDays === 1) return 'yesterday';
    return `${diffDays} days ago`;
  }
}
