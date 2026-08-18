import { Component, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { filter } from 'rxjs/operators';
import { KeycloakService } from '../../../core/services/keycloak.service';
import { ThemeService } from '../../../core/services/theme.service';

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive, LucideAngularModule],
  template: `
    @if (isAuthenticated) {
      <nav class="navbar" [class.chat-hidden]="hidden" aria-label="Primary navigation">
        <a routerLink="/discover" class="brand-lockup" aria-label="Connect home">
          <span class="brand-icon"><lucide-icon name="heart-handshake" [size]="22" strokeWidth="1.8" /></span>
          <span class="brand-text">
            <span class="brand-copy">connect</span>
            <span class="brand-tagline">HTL St. Pölten</span>
          </span>
        </a>

        <p class="nav-label">Explore</p>
        <div class="nav-section">
          <a routerLink="/discover" routerLinkActive="active" ariaCurrentWhenActive="page" class="nav-item" aria-label="Discover">
            <span class="nav-item-icon"><lucide-icon name="compass" [size]="22" strokeWidth="2.2" /></span>
            <span class="nav-item-label">Discover</span>
          </a>
          <a routerLink="/likes" routerLinkActive="active" ariaCurrentWhenActive="page" class="nav-item" aria-label="Likes">
            <span class="nav-item-icon"><lucide-icon name="heart" [size]="22" strokeWidth="2.2" /></span>
            <span class="nav-item-label">Likes</span>
          </a>
          <a routerLink="/matches" routerLinkActive="active" ariaCurrentWhenActive="page" class="nav-item" aria-label="Messages">
            <span class="nav-item-icon"><lucide-icon name="message-circle" [size]="22" strokeWidth="2.2" /></span>
            <span class="nav-item-label">Messages</span>
          </a>
          <a routerLink="/profile" routerLinkActive="active" ariaCurrentWhenActive="page" class="nav-item" aria-label="Profile">
            <span class="nav-item-icon"><lucide-icon name="user-round" [size]="22" strokeWidth="2.2" /></span>
            <span class="nav-item-label">Profile</span>
          </a>
        </div>

        <div class="nav-actions">
          <p class="nav-label account-label">Account</p>
          <button type="button" class="utility-action" (click)="theme.toggle()"
            [attr.aria-label]="theme.isDark() ? 'Switch to light mode' : 'Switch to dark mode'">
            <lucide-icon [name]="theme.isDark() ? 'sun' : 'moon'" [size]="18" strokeWidth="2.1" />
            <span>{{ theme.isDark() ? 'Light mode' : 'Dark mode' }}</span>
          </button>
          <a routerLink="/profile/edit" class="utility-action edit-action">
            <lucide-icon name="pencil" [size]="18" strokeWidth="2.1" />
            <span>Edit profile</span>
          </a>
        </div>
      </nav>
    }
  `,
  styles: [`
    :host { display: contents; }

    .brand-lockup,
    .nav-actions,
    .nav-label { display: none; }

    .navbar.chat-hidden { display: none; }

    .navbar {
      position: fixed;
      z-index: 100;
      left: 0;
      right: 0;
      bottom: 0;
      min-height: 56px;
      padding: 4px 12px calc(4px + env(safe-area-inset-bottom, 0px));
      background: var(--surface-glass);
      border-top: 1px solid var(--border-light);
      backdrop-filter: blur(18px);
      -webkit-backdrop-filter: blur(18px);
    }

    .nav-section {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      align-items: center;
      max-width: 520px;
      margin: 0 auto;
    }

    .nav-item {
      position: relative;
      min-height: 48px;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      color: var(--text-secondary);
      text-decoration: none;
      border-radius: 14px;
      transition: color 160ms ease, background 160ms ease;

      .nav-item-label { display: none; }

      .nav-item-icon {
        position: relative;
        width: 38px;
        height: 38px;
        display: grid;
        place-items: center;
        border-radius: 14px;
        transition: color 160ms ease, background 160ms ease, transform 160ms ease, box-shadow 160ms ease;

        lucide-icon {
          width: 22px;
          height: 22px;
          display: grid;
          place-items: center;
          line-height: 0;
        }
      }

      &.active {
        color: var(--brand);
        background: transparent;

        .nav-item-icon {
          color: var(--brand);
          background: transparent;
          box-shadow: none;
          transform: none;

          &::after {
            content: '';
            position: absolute;
            left: 50%;
            bottom: 0;
            width: 4px;
            height: 4px;
            border-radius: 999px;
            background: currentColor;
            transform: translateX(-50%);
          }
        }
      }

      &:focus-visible {
        outline: 2px solid var(--brand);
        outline-offset: 2px;
      }
    }

    @media (min-width: 768px) {
      :host {
        display: block;
        width: 260px;
        flex-shrink: 0;
        background: var(--bg);
      }

      .navbar.chat-hidden { display: flex; }

      .navbar {
        top: 14px;
        bottom: 14px;
        left: 14px;
        right: auto;
        width: 232px;
        min-height: 0;
        height: calc(100dvh - 28px);
        padding: 14px;
        display: flex;
        flex-direction: column;
        background: var(--surface-glass);
        border: 1px solid var(--border-light);
        border-radius: 26px;
        box-shadow: 0 24px 60px var(--shadow-sm);
        backdrop-filter: blur(24px);
        -webkit-backdrop-filter: blur(24px);
      }

      .brand-lockup {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 4px 4px 22px;
        text-decoration: none;
        color: var(--text-primary);
      }

      .brand-icon {
        width: 42px;
        height: 42px;
        display: grid;
        place-items: center;
        flex: 0 0 auto;
        border-radius: 14px;
        color: var(--text-primary);
        background: var(--brand);
        box-shadow: 0 10px 22px rgba(109, 144, 55, 0.2);
      }

      .brand-text {
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 1px;
      }

      .brand-copy {
        font-size: 20px;
        font-weight: 700;
        letter-spacing: -0.5px;
        line-height: 1.05;
      }

      .brand-tagline {
        color: var(--text-muted);
        font-size: 10px;
        font-weight: 600;
        letter-spacing: 0.02em;
      }

      .nav-label {
        display: block;
        margin: 0 10px 8px;
        color: var(--text-muted);
        font-size: 10px;
        font-weight: 700;
        letter-spacing: 0.1em;
        text-transform: uppercase;
      }

      .nav-section {
        width: 100%;
        display: flex;
        flex-direction: column;
        align-items: stretch;
        gap: 5px;
        margin: 0;
      }

      .nav-item {
        position: relative;
        min-height: 52px;
        flex-direction: row;
        justify-content: flex-start;
        gap: 12px;
        padding: 0 13px;
        border-radius: 16px;
        font-weight: 600;
        transition: color 160ms ease, background 160ms ease, transform 160ms ease, box-shadow 160ms ease;

        .nav-item-label { display: inline; font-size: 14px; }

        .nav-item-icon {
          width: 24px;
          height: 24px;
          border-radius: 0;
        }

        &:hover:not(.active) {
          color: var(--text-primary);
          background: var(--surface-2);
          transform: translateX(2px);
        }

        &.active {
          color: var(--text-primary);
          background: transparent;
          box-shadow: none;
          font-weight: 700;

          &::before {
            content: '';
            position: absolute;
            left: 0;
            top: 50%;
            width: 3px;
            height: 20px;
            border-radius: 999px;
            background: var(--brand);
            transform: translateY(-50%);
          }

          .nav-item-icon {
            color: var(--brand);
            background: transparent;
            box-shadow: none;
            transform: none;

            &::after { display: none; }
          }
        }
      }

      .nav-actions {
        display: flex;
        flex-direction: column;
        gap: 5px;
        margin-top: auto;
        padding: 8px;
        border: 1px solid var(--border-light);
        border-radius: 18px;
        background: var(--surface-2);
      }

      .account-label {
        margin: 2px 4px 4px;
      }

      .utility-action {
        min-height: 42px;
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 0 10px;
        border: 0;
        border-radius: 12px;
        background: transparent;
        color: var(--text-secondary);
        text-decoration: none;
        font-size: 13px;
        font-weight: 600;
        cursor: pointer;
        transition: color 160ms ease, background 160ms ease, transform 160ms ease;

        &:hover {
          color: var(--text-primary);
          background: var(--surface);
        }

        &:focus-visible {
          outline: 2px solid var(--brand);
          outline-offset: 2px;
        }
      }

      .edit-action {
        color: var(--text-primary);
        background: var(--brand);
        box-shadow: 0 8px 18px rgba(109, 144, 55, 0.18);

        &:hover {
          background: var(--brand-2);
          transform: translateY(-1px);
        }
      }
    }
  `]
})
export class NavbarComponent {
  private keycloak = inject(KeycloakService);
  private router = inject(Router);
  theme = inject(ThemeService);

  hidden = false;

  constructor() {
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: NavigationEnd) => {
      this.hidden = event.urlAfterRedirects.includes('/chat/');
    });
  }

  get isAuthenticated(): boolean {
    return this.keycloak.isAuthenticated();
  }
}
