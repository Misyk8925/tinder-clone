import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { KeycloakService } from '../../core/services/keycloak.service';

@Component({
  selector: 'app-login',
  template: `
    <div class="login-page">
      <div class="content">
        <div class="logo">
          <svg viewBox="0 0 24 24" fill="#fd5564" class="heart-icon">
            <path d="M12 21.593c-5.63-5.539-11-10.297-11-14.402 0-3.791 3.068-5.191 5.281-5.191 1.312 0 4.151.501 5.719 4.457 1.59-3.968 4.464-4.447 5.726-4.447 2.54 0 5.274 1.621 5.274 5.181 0 4.069-5.136 8.625-11 14.402z"/>
          </svg>
          <h1>Tinder</h1>
        </div>
        <p class="tagline">Swipe Right™</p>
        <div class="features">
          <div class="feature">💘 Match with people near you</div>
          <div class="feature">💬 Chat with your matches</div>
          <div class="feature">⭐ Go premium for more swipes</div>
        </div>
        <button class="btn-login" (click)="login()">
          Sign in with Keycloak
        </button>
        <p class="terms">By clicking Sign in, you agree to our Terms.</p>
      </div>
    </div>
  `,
  styles: [`
    .login-page {
      min-height: 100dvh;
      background: linear-gradient(160deg, #fd5564 0%, #ff8a00 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
    }

    .content {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 24px;
      max-width: 360px;
      width: 100%;
    }

    .logo {
      display: flex;
      align-items: center;
      gap: 12px;

      .heart-icon {
        width: 52px;
        height: 52px;
        filter: drop-shadow(0 4px 12px rgba(0,0,0,0.2));
      }

      h1 {
        margin: 0;
        font-size: 48px;
        font-weight: 800;
        color: #fff;
        letter-spacing: -1px;
        text-shadow: 0 2px 12px rgba(0,0,0,0.15);
      }
    }

    .tagline {
      color: rgba(255,255,255,0.9);
      font-size: 18px;
      font-weight: 500;
      margin: -8px 0 0;
    }

    .features {
      display: flex;
      flex-direction: column;
      gap: 12px;
      width: 100%;
      background: rgba(255,255,255,0.15);
      backdrop-filter: blur(10px);
      border-radius: 20px;
      padding: 24px;
    }

    .feature {
      color: #fff;
      font-size: 16px;
      font-weight: 500;
    }

    .btn-login {
      width: 100%;
      padding: 16px;
      border-radius: 16px;
      border: none;
      background: #fff;
      color: #fd5564;
      font-size: 16px;
      font-weight: 700;
      cursor: pointer;
      box-shadow: 0 8px 24px rgba(0,0,0,0.15);
      transition: transform 0.15s;

      &:active { transform: scale(0.97); }
    }

    .terms {
      color: rgba(255,255,255,0.7);
      font-size: 12px;
      text-align: center;
      margin: 0;
    }
  `]
})
export class LoginComponent implements OnInit {
  private keycloak = inject(KeycloakService);
  private router = inject(Router);

  ngOnInit(): void {
    if (this.keycloak.isAuthenticated()) {
      this.router.navigate(['/discover']);
    }
  }

  login(): void {
    this.keycloak.login();
  }
}
