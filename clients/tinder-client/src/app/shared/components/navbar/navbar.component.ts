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
        <svg viewBox="0 0 24 24" fill="#fd267a" width="26" height="26">
          <path d="M17.66 11.2c-.23-.3-.51-.56-.77-.82-.67-.6-1.43-1.03-2.07-1.66C13.33 7.26 13 4.85 13.95 3c-.95.23-1.78.75-2.49 1.32-2.59 2.11-3.66 5.65-2.67 8.9.04.14.08.28.08.43 0 .28-.19.52-.45.57-.28.07-.53-.09-.63-.37-.04-.1-.06-.21-.09-.32C7.15 13 7 12.5 7 11.85c0-.58.16-1.2.44-1.7-1.16 1.27-1.86 2.97-1.86 4.77 0 3.31 2.69 6 6 6s6-2.69 6-6c0-1.88-.82-3.63-2.09-4.82z"/>
        </svg>
        <span class="logo-text">tinder</span>
      </div>

      <div class="nav-section">
        <a routerLink="/discover" routerLinkActive="active" class="nav-item">
          <lucide-icon name="flame" [size]="20" strokeWidth="1.75"></lucide-icon>
          <span class="nav-label">Discover</span>
        </a>

        <a routerLink="/likes" routerLinkActive="active" class="nav-item">
          <lucide-icon name="heart" [size]="20" strokeWidth="1.75"></lucide-icon>
          <span class="nav-label">Likes</span>
        </a>

        <a routerLink="/matches" routerLinkActive="active" class="nav-item">
          <lucide-icon name="message-circle" [size]="20" strokeWidth="1.75"></lucide-icon>
          <span class="nav-label">Messages</span>
        </a>

        <a routerLink="/profile" routerLinkActive="active" class="nav-item">
          <lucide-icon name="user" [size]="20" strokeWidth="1.75"></lucide-icon>
          <span class="nav-label">Profile</span>
        </a>
      </div>

      <!-- Mobile: icons only (no labels shown via CSS) -->

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
      bottom: 0;
      left: 0;
      right: 0;
      display: flex;
      justify-content: space-around;
      align-items: center;
      background: var(--surface);
      border-top: 1px solid var(--border);
      padding: 10px 0 calc(env(safe-area-inset-bottom, 0px) + 10px);
      z-index: 100;
    }

    /* On mobile render all nav-items as direct children of navbar */
    .nav-item {
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--text-secondary);
      text-decoration: none;
      padding: 6px 20px;
      border-radius: 14px;
      transition: color 0.15s;

      &.active { color: #fd267a; }
      &:active { opacity: 0.7; }
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
      }

      /* Logo row */
      .nav-logo {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 22px 20px 18px;
        border-bottom: 1px solid var(--border);
        flex-shrink: 0;
      }

      .logo-text {
        font-size: 22px;
        font-weight: 800;
        color: #fd267a;
        letter-spacing: -0.5px;
      }

      /* Nav section wraps all items */
      .nav-section {
        display: flex;
        flex-direction: column;
        padding: 10px 0;
        flex: 1;
      }

      .nav-label {
        display: block;
        font-size: 14px;
        font-weight: 500;
      }

      .nav-item {
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 14px;
        padding: 13px 20px;
        border-radius: 0;
        justify-content: flex-start;
        color: var(--text-secondary);
        margin: 1px 10px;
        border-radius: 12px;
        transition: color 0.15s, background 0.15s;

        &.active {
          color: #fd267a;
          background: rgba(253, 38, 122, 0.08);
          font-weight: 600;

          .nav-label { font-weight: 600; }
        }

        &:hover:not(.active) {
          color: var(--text-primary);
          background: var(--surface-2);
        }
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
