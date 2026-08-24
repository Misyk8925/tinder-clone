import { Component, HostBinding, inject } from '@angular/core';
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
      <nav class="navbar" [class.route-hidden]="hidden" aria-label="Primary navigation">
        <a routerLink="/discover" class="brand-lockup" aria-label="Lunari home">
          <span class="brand-icon" aria-hidden="true">
            <svg class="lunari-mark" viewBox="0 0 48 48" fill="none">
              <path d="M31.8 10.7a14.8 14.8 0 1 0 6 25.6 13.2 13.2 0 0 1-6-25.6Z" fill="currentColor" />
              <path d="M36.5 8.8c.35 2.4 2.2 4.25 4.6 4.6-2.4.35-4.25 2.2-4.6 4.6-.35-2.4-2.2-4.25-4.6-4.6 2.4-.35 4.25-2.2 4.6-4.6Z" fill="currentColor" />
            </svg>
          </span>
          <span class="brand-text">
            <span class="brand-copy">Lunari</span>
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

    .navbar.route-hidden { display: none; }

    .navbar {
      position: fixed;
      z-index: 100;
      left: 0;
      right: 0;
      bottom: 0;
      min-height: var(--mobile-bottombar-height);
      padding: 1px 10px calc(1px + env(safe-area-inset-bottom, 0px));
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
      min-height: 44px;
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
        width: 34px;
        height: 34px;
        display: grid;
        place-items: center;
        border-radius: 14px;
        transition: color 160ms ease, background 160ms ease, transform 160ms ease, box-shadow 160ms ease;

        lucide-icon {
          width: 20px;
          height: 20px;
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
            width: 3px;
            height: 3px;
            border-radius: 999px;
            background: currentColor;
            transform: translateX(-50%);
          }
        }
      }

      &:focus-visible {
        outline: 1px solid var(--text-secondary);
        outline-offset: 2px;
      }
    }

    @media (min-width: 768px) {
      :host {
        display: block;
        width: 244px;
        flex-shrink: 0;
        background: var(--bg);
      }

      .navbar.route-hidden { display: none; }

      .navbar {
        top: 14px;
        bottom: 14px;
        left: 14px;
        right: auto;
        width: 216px;
        min-height: 0;
        height: calc(100dvh - 28px);
        padding: 12px;
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
        padding: 4px 4px 20px;
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
        box-shadow: 0 10px 22px var(--brand-glow);
      }

      .lunari-mark {
        width: 25px;
        height: 25px;
        display: block;
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
        min-height: 50px;
        flex-direction: row;
        justify-content: flex-start;
        gap: 12px;
        padding: 0 11px;
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
          color: var(--brand);
          background: transparent;
          box-shadow: none;
          font-weight: 700;

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
        gap: 2px;
        margin-top: auto;
        padding: 12px 0 0;
        border: 0;
        border-top: 1px solid var(--border-light);
        border-radius: 0;
        background: transparent;
      }

      .account-label {
        margin: 0 12px 5px;
      }

      .utility-action {
        min-height: 42px;
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 0 12px;
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
        color: var(--brand);
        background: transparent;
        box-shadow: none;

        &:hover {
          color: var(--brand);
          background: var(--brand-soft);
          transform: none;
        }
      }
    }

    :host.route-hidden-host {
      display: contents;
      width: auto;
      flex-shrink: 1;
      background: transparent;
    }
  `]
})
export class NavbarComponent {
  private keycloak = inject(KeycloakService);
  private router = inject(Router);
  theme = inject(ThemeService);

  hidden = false;

  @HostBinding('class.route-hidden-host')
  get routeHiddenHost(): boolean {
    return this.hidden;
  }

  constructor() {
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: NavigationEnd) => {
      this.hidden = event.urlAfterRedirects.includes('/chat/') || event.urlAfterRedirects.includes('/location-permission');
    });
  }

  get isAuthenticated(): boolean {
    return this.keycloak.isAuthenticated();
  }
}
