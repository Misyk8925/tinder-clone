import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { KeycloakService } from '../../../core/services/keycloak.service';
import { filter } from 'rxjs/operators';
import { LucideAngularModule } from 'lucide-angular';

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive, LucideAngularModule],
  template: `
    @if (!hidden) {
    <nav class="navbar">

      <a routerLink="/discover" routerLinkActive="active" class="nav-item">
        <lucide-icon name="flame" [size]="26" class="nav-icon" strokeWidth="1.75"></lucide-icon>
        <span class="nav-label">Swipen</span>
      </a>

      <a routerLink="/likes" routerLinkActive="active" class="nav-item">
        <lucide-icon name="heart" [size]="26" class="nav-icon" strokeWidth="1.75"></lucide-icon>
        <span class="nav-label">Likes</span>
      </a>

      <a routerLink="/matches" routerLinkActive="active" class="nav-item">
        <lucide-icon name="message-circle" [size]="26" class="nav-icon" strokeWidth="1.75"></lucide-icon>
        <span class="nav-label">Chat</span>
      </a>

      <a routerLink="/profile" routerLinkActive="active" class="nav-item">
        <lucide-icon name="user" [size]="26" class="nav-icon" strokeWidth="1.75"></lucide-icon>
        <span class="nav-label">Profil</span>
      </a>

    </nav>
    }
  `,
  styles: [`
    :host {
      display: contents;
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
      padding: 8px 0 calc(env(safe-area-inset-bottom, 0px) + 29px);
      z-index: 100;
    }

    .nav-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      color: var(--custom);
      text-decoration: none;
      padding: 4px 14px;
      min-width: 56px;
      transition: color 0.15s;

      .nav-label {
        font-size: 10px;
        font-weight: 500;
        letter-spacing: 0.1px;
      }

      &.active {
        color: #fd267a;

        .nav-label {
          font-weight: 700;
          color: #fd267a;
        }
      }
    }

    [data-theme="dark"] .nav-item.active .nav-label {
      color: #f2f2f7;
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
