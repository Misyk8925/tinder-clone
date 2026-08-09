import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { KeycloakService } from '../../core/services/keycloak.service';

@Component({
  selector: 'app-login',
  imports: [LucideAngularModule],
  template: `
    <main class="login-page">
      <div class="portrait" role="img" aria-label="Two people meeting outdoors"></div>
      <section class="login-content">
        <div class="brand-mark"><lucide-icon name="heart-handshake" [size]="26" strokeWidth="1.8" /></div>
        <p class="eyebrow">Meet with intention</p>
        <h1>People worth getting to know.</h1>
        <p class="intro">Thoughtful profiles, people nearby, and conversations that start with something real.</p>

        <div class="trust-points">
          <div><lucide-icon name="map-pin" [size]="20" strokeWidth="1.8" /><span>Relevant people near you</span></div>
          <div><lucide-icon name="message-circle" [size]="20" strokeWidth="1.8" /><span>Personality before small talk</span></div>
          <div><lucide-icon name="shield-check" [size]="20" strokeWidth="1.8" /><span>Clear, respectful controls</span></div>
        </div>

        <button type="button" class="login-button" (click)="login()">Continue securely</button>
        <p class="terms">By continuing, you agree to the Terms and Privacy Policy.</p>
      </section>
    </main>
  `,
  styles: [`
    .login-page { min-height: 100dvh; display: grid; background: var(--surface); }

    .portrait {
      min-height: 42dvh;
      background: url('/assets/profiles/mila-discover.png') center 36% / cover no-repeat;
    }

    .login-content {
      display: flex;
      flex-direction: column;
      justify-content: center;
      padding: 30px 24px 36px;
      color: var(--text-primary);
      background: var(--surface);
    }

    .brand-mark {
      width: 50px;
      height: 50px;
      display: grid;
      place-items: center;
      margin-bottom: 24px;
      border-radius: 16px;
      color: var(--text-primary);
      background: var(--brand);
    }

    .eyebrow { margin: 0 0 8px; color: var(--brand-strong); font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
    h1 { margin: 0; max-width: 520px; font-size: clamp(38px, 10vw, 64px); line-height: 0.98; letter-spacing: -0.055em; }
    .intro { margin: 18px 0 0; max-width: 480px; color: var(--text-secondary); font-size: 16px; line-height: 1.55; }

    .trust-points { display: grid; gap: 11px; margin: 26px 0; }
    .trust-points div { display: flex; align-items: center; gap: 11px; color: var(--text-secondary); font-size: 14px; font-weight: 600; }
    .trust-points lucide-icon { color: var(--brand-strong); }

    .login-button {
      width: 100%;
      min-height: 54px;
      border: 0;
      border-radius: 16px;
      color: var(--text-primary);
      background: var(--brand);
      font: inherit;
      font-size: 15px;
      font-weight: 700;
      cursor: pointer;
      box-shadow: 0 12px 28px rgba(109, 144, 55, 0.22);
    }

    .terms { margin: 13px 0 0; color: var(--text-muted); font-size: 11px; line-height: 1.4; text-align: center; }

    @media (min-width: 760px) {
      .login-page { grid-template-columns: minmax(320px, 0.9fr) minmax(460px, 1.1fr); }
      .portrait { min-height: 100dvh; }
      .login-content { padding: clamp(48px, 8vw, 110px); }
      .login-button { max-width: 420px; }
      .terms { max-width: 420px; }
    }
  `]
})
export class LoginComponent implements OnInit {
  private keycloak = inject(KeycloakService);
  private router = inject(Router);

  ngOnInit(): void {
    if (this.keycloak.isAuthenticated()) this.router.navigate(['/discover']);
  }

  login(): void { this.keycloak.login(); }
}
