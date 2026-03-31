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

      <div class="nav-logo">
        <svg viewBox="0 0 24 24" fill="#fd267a" width="28" height="28">
          <path d="M17.66 11.2c-.23-.3-.51-.56-.77-.82-.67-.6-1.43-1.03-2.07-1.66C13.33 7.26 13 4.85 13.95 3c-.95.23-1.78.75-2.49 1.32-2.59 2.11-3.66 5.65-2.67 8.9.04.14.08.28.08.43 0 .28-.19.52-.45.57-.28.07-.53-.09-.63-.37-.04-.1-.06-.21-.09-.32C7.15 13 7 12.5 7 11.85c0-.58.16-1.2.44-1.7-1.16 1.27-1.86 2.97-1.86 4.77 0 3.31 2.69 6 6 6s6-2.69 6-6c0-1.88-.82-3.63-2.09-4.82z"/>
        </svg>
      </div>

      <a routerLink="/discover" routerLinkActive="active" class="nav-item">
        <lucide-icon name="flame" [size]="26" class="nav-icon" strokeWidth="1.75"></lucide-icon>
      </a>

      <a routerLink="/likes" routerLinkActive="active" class="nav-item">
        <lucide-icon name="heart" [size]="26" class="nav-icon" strokeWidth="1.75"></lucide-icon>
      </a>

      <a routerLink="/matches" routerLinkActive="active" class="nav-item">
        <lucide-icon name="message-circle" [size]="26" class="nav-icon" strokeWidth="1.75"></lucide-icon>
      </a>

      <a routerLink="/profile" routerLinkActive="active" class="nav-item">
        <lucide-icon name="user" [size]="26" class="nav-icon" strokeWidth="1.75"></lucide-icon>
      </a>

    </nav>
  `,
  styles: [`
    :host {
      display: contents;
    }

    /* ── Mobile: bottom bar ── */
    .nav-logo {
      display: none;
    }

    /* Hide on mobile when in chat; always show on desktop */
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

    .nav-item {
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--text-secondary);
      text-decoration: none;
      padding: 6px 20px;
      border-radius: 14px;
      transition: color 0.15s;

      &.active {
        color: #fd267a;
      }

      &:active {
        opacity: 0.7;
      }
    }

    /* ── Desktop: left sidebar ── */
    @media (min-width: 768px) {
      :host {
        display: block;
        flex-shrink: 0;
        width: 72px;
      }

      .navbar.chat-hidden {
        display: flex;
      }

      .navbar {
        position: fixed;
        left: 0;
        top: 0;
        bottom: 0;
        right: unset;
        width: 72px;
        height: 100dvh;
        flex-direction: column;
        justify-content: flex-start;
        align-items: stretch;
        border-top: none;
        border-right: 1px solid var(--border);
        padding: 0;
        gap: 0;
        overflow-y: auto;
        overflow-x: hidden;
      }

      .nav-logo {
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 20px 0 16px;
        border-bottom: 1px solid var(--border);
        margin-bottom: 8px;
        flex-shrink: 0;
      }

      .nav-item {
        padding: 16px 0;
        border-radius: 0;
        justify-content: center;
        color: var(--text-secondary);

        &.active {
          color: #fd267a;
          background: rgba(253, 38, 122, 0.06);
        }

        &:hover {
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
