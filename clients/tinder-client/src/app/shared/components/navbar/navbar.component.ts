import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { KeycloakService } from '../../../core/services/keycloak.service';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive],
  template: `
    @if (!hidden) {
    <nav class="navbar">
      <a routerLink="/discover" routerLinkActive="active" class="nav-item">
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 21.593c-5.63-5.539-11-10.297-11-14.402 0-3.791 3.068-5.191 5.281-5.191 1.312 0 4.151.501 5.719 4.457 1.59-3.968 4.464-4.447 5.726-4.447 2.54 0 5.274 1.621 5.274 5.181 0 4.069-5.136 8.625-11 14.402z"/>
        </svg>
        <span>Discover</span>
      </a>
      <a routerLink="/matches" routerLinkActive="active" class="nav-item">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span>Matches</span>
      </a>
      <a routerLink="/profile" routerLinkActive="active" class="nav-item">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
          <circle cx="12" cy="7" r="4"/>
        </svg>
        <span>Profile</span>
      </a>
    </nav>
    }
  `,
  styles: [`
    .navbar {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      display: flex;
      justify-content: space-around;
      align-items: center;
      background: #fff;
      border-top: 1px solid #e8e8e8;
      padding: 8px 0 env(safe-area-inset-bottom, 8px);
      z-index: 100;
      box-shadow: 0 -2px 10px rgba(0,0,0,0.08);
    }

    .nav-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
      color: #aaa;
      text-decoration: none;
      transition: color 0.2s;
      padding: 4px 20px;

      svg {
        width: 24px;
        height: 24px;
      }

      span {
        font-size: 10px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }

      &.active {
        color: #fd5564;
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
}
