import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { KeycloakService } from '../../../core/services/keycloak.service';
import { filter } from 'rxjs/operators';
import { LucideAngularModule } from 'lucide-angular';

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive, LucideAngularModule],
  template: `
    <nav class="navbar" [class.chat-hidden]="hidden">

      <!-- Desktop: logo + wordmark -->
      <div class="nav-logo">
        <svg viewBox="0 0 24 24" fill="#ff4458" width="26" height="26">
          <path d="M17.66 11.2c-.23-.3-.51-.56-.77-.82-.67-.6-1.43-1.03-2.07-1.66C13.33 7.26 13 4.85 13.95 3c-.95.23-1.78.75-2.49 1.32-2.59 2.11-3.66 5.65-2.67 8.9.04.14.08.28.08.43 0 .28-.19.52-.45.57-.28.07-.53-.09-.63-.37-.04-.1-.06-.21-.09-.32C7.15 13 7 12.5 7 11.85c0-.58.16-1.2.44-1.7-1.16 1.27-1.86 2.97-1.86 4.77 0 3.31 2.69 6 6 6s6-2.69 6-6c0-1.88-.82-3.63-2.09-4.82z"/>
        </svg>
        <span class="logo-text">tinder</span>
      </div>

      <div class="nav-section">
        <a routerLink="/discover" routerLinkActive="active" class="nav-item">
          <lucide-icon name="flame" [size]="22" strokeWidth="1.75"></lucide-icon>
          <span class="nav-label">Discover</span>
          <div class="nav-dot"></div>
        </a>

        <a routerLink="/likes" routerLinkActive="active" class="nav-item">
          <lucide-icon name="heart" [size]="22" strokeWidth="1.75"></lucide-icon>
          <span class="nav-label">Likes</span>
          <div class="nav-dot"></div>
        </a>

        <a routerLink="/matches" routerLinkActive="active" class="nav-item">
          <lucide-icon name="message-circle" [size]="22" strokeWidth="1.75"></lucide-icon>
          <span class="nav-label">Messages</span>
          <div class="nav-dot"></div>
        </a>

        <a routerLink="/profile" routerLinkActive="active" class="nav-item">
          <lucide-icon name="user" [size]="22" strokeWidth="1.75"></lucide-icon>
          <span class="nav-label">Profile</span>
          <div class="nav-dot"></div>
        </a>
      </div>

    </nav>
  `,
  styles: [`
    :host {
      display: contents;
    }

    /* ────────────────────────────
       Mobile: bottom tab bar
    ──────────────────────────── */
    .nav-logo,
    .nav-label,
    .nav-section {
      display: none;
    }

    .navbar.chat-hidden {
      display: none;
    }

    .navbar {
      position: fixed;
      bottom: calc(env(safe-area-inset-bottom, 0px) + 10px);
      left: 12px;
      right: 12px;
      display: flex;
      justify-content: space-around;
      align-items: center;
      background: var(--surface-glass);
      border: 1px solid var(--border);
      padding: 8px 8px;
      border-radius: 22px;
      z-index: 100;
      backdrop-filter: blur(18px);
      -webkit-backdrop-filter: blur(18px);
      box-shadow: 0 -12px 30px var(--shadow-md);
    }

    [data-theme="dark"] .navbar {
      background: rgba(24, 22, 30, 0.8);
      border-color: rgba(255,255,255,0.06);
    }

    /* On mobile render all nav-items as direct children of navbar */
    .nav-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      color: var(--text-muted);
      text-decoration: none;
      padding: 8px 18px;
      border-radius: 16px;
      transition: color 0.18s, transform 0.2s, background 0.2s, box-shadow 0.2s;
      position: relative;
      gap: 2px;

      &.active {
        color: #fff;
        background: var(--brand-gradient);
        box-shadow: 0 10px 22px rgba(255, 68, 88, 0.35);
        transform: translateY(-1px);
      }
      &:active { opacity: 0.75; }
    }

    .nav-dot { display: none; }

    .nav-item lucide-icon svg {
      stroke: var(--text-muted);
    }

    .nav-item.active lucide-icon svg {
      stroke: #ffffff;
    }

    /* ────────────────────────────
       Desktop: left sidebar 200px
    ──────────────────────────── */
    @media (min-width: 768px) {
      :host {
        display: block;
        flex-shrink: 0;
        width: 200px;
      }

      .navbar.chat-hidden {
        display: flex;
      }

      .navbar {
        position: fixed;
        left: 0;
        top: 0;
        bottom: 0;
        width: 200px;
        height: 100dvh;
        flex-direction: column;
        justify-content: flex-start;
        align-items: stretch;
        background: var(--surface);
        border-top: none;
        border-right: 1px solid var(--border);
        padding: 0;
        gap: 0;
        overflow-y: auto;
        overflow-x: hidden;
        right: unset;
        backdrop-filter: none;
        -webkit-backdrop-filter: none;
        border-radius: 0 24px 24px 0;
        box-shadow: 8px 0 30px var(--shadow-sm);
      }

      [data-theme="dark"] .navbar {
        background: var(--surface);
      }

      /* Logo row */
      .nav-logo {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 24px 20px 18px;
        border-bottom: 1px solid var(--border);
        flex-shrink: 0;
        background: linear-gradient(135deg, rgba(255, 68, 88, 0.08), rgba(255, 138, 61, 0.08));
      }

      .logo-text {
        font-size: 22px;
        font-weight: 800;
        color: var(--brand);
        letter-spacing: -0.6px;
        text-transform: lowercase;
      }

      /* Nav section wraps all items */
      .nav-section {
        display: flex;
        flex-direction: column;
        padding: 12px 8px;
        flex: 1;
        gap: 4px;
      }

      .nav-label {
        display: block;
        font-size: 14px;
        font-weight: 500;
      }

      .nav-dot { display: none; }

      .nav-item {
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 12px;
        padding: 12px 14px 12px 16px;
        border-radius: 14px;
        justify-content: flex-start;
        color: var(--text-secondary);
        margin: 4px 8px;
        transition: color 0.18s, background 0.18s, transform 0.18s, box-shadow 0.18s;
        transform: none;
        position: relative;

        &.active {
          color: var(--text-primary);
          background: linear-gradient(90deg, rgba(255, 68, 88, 0.16), rgba(255, 68, 88, 0.02));
          box-shadow: inset 0 0 0 1px rgba(255, 68, 88, 0.12);

          &::before {
            content: '';
            position: absolute;
            left: 8px;
            top: 50%;
            transform: translateY(-50%);
            width: 4px;
            height: 60%;
            border-radius: 4px;
            background: var(--brand-gradient);
          }

          .nav-label { font-weight: 700; }
        }

        &:active { transform: none; }

        &:hover:not(.active) {
          color: var(--text-primary);
          background: var(--surface-2);
        }
      }

      .nav-item lucide-icon svg {
        stroke: var(--text-secondary);
      }

      .nav-item.active lucide-icon svg {
        stroke: var(--brand);
      }
    }
  `]
})
export class NavbarComponent {
  private keycloak = inject(KeycloakService);
  private router = inject(Router);

  hidden = false;

  constructor() {
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd)
    ).subscribe((e: NavigationEnd) => {
      this.hidden = e.urlAfterRedirects.includes('/chat/');
    });
  }

  get isAuthenticated(): boolean {
    return this.keycloak.isAuthenticated();
  }

  get isPremiumOrAdmin(): boolean {
    return this.keycloak.hasRole('USER_PREMIUM') || this.keycloak.hasRole('ADMIN');
  }
}
