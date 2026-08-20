import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { GeoLocationService } from '../../core/services/geo-location.service';

@Component({
  selector: 'app-location-permission',
  imports: [LucideAngularModule],
  template: `
    <div class="page">
      <section class="permission-shell" aria-labelledby="location-title">
        <div class="intro">
          <span class="eyebrow">Location</span>

          @if (status() !== 'denied') {
            <h1 id="location-title">Meet people nearby.</h1>
            <p class="lead">Use your location to see relevant profiles around you.</p>
            <div class="privacy-note">
              <lucide-icon name="shield-check" [size]="16" strokeWidth="2.2" aria-hidden="true" />
              <span>Your exact location stays private.</span>
            </div>
          } @else {
            <h1 id="location-title">Location is turned off.</h1>
            <p class="lead">Enable access in your device settings, then try again.</p>
            <ol class="steps">
              <li>Open <strong>Settings → Privacy → Location Services</strong></li>
              <li>Allow location while using this browser</li>
            </ol>
          }
        </div>

        <div class="actions">
          <button type="button" class="btn-allow" [disabled]="status() === 'requesting'" (click)="allow()">
            {{ status() === 'requesting' ? 'Requesting…' : status() === 'denied' ? 'Try again' : 'Allow location access' }}
          </button>
          <button type="button" class="btn-skip" (click)="skip()">Continue without location</button>
          <p class="action-note">You can change this later in your profile.</p>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .page {
      min-height: 100dvh;
      background: transparent;
      display: grid;
      place-items: center;
      padding: 28px 22px;
    }

    .permission-shell {
      width: 100%;
      max-width: 390px;
      min-height: min(620px, calc(100dvh - 56px));
      display: flex;
      flex-direction: column;
      justify-content: center;
      gap: 42px;
    }

    .intro {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
    }

    .eyebrow {
      margin-bottom: 18px;
      color: var(--text-muted);
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }

    h1 {
      margin: 0 0 12px;
      max-width: 340px;
      font-size: clamp(28px, 8vw, 34px);
      font-weight: 700;
      line-height: 1.08;
      letter-spacing: -0.04em;
      color: var(--text-primary);
    }

    .lead {
      margin: 0;
      max-width: 340px;
      font-size: 15px;
      color: var(--text-secondary);
      line-height: 1.55;
    }

    .privacy-note {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      margin-top: 20px;
      color: var(--text-muted);
      font-size: 12px;
      font-weight: 600;

      lucide-icon {
        flex: 0 0 auto;
        color: var(--brand-strong);
        line-height: 0;
      }
    }

    .steps {
      width: 100%;
      margin: 22px 0 0;
      padding: 0;
      list-style: none;
      counter-reset: location-step;
      color: var(--text-secondary);
      font-size: 13px;
      line-height: 1.45;

      li {
        counter-increment: location-step;
        display: grid;
        grid-template-columns: 26px 1fr;
        gap: 10px;
        align-items: start;
        padding: 10px 0;
        border-top: 1px solid var(--border-light);
      }

      li::before {
        content: counter(location-step);
        width: 24px;
        height: 24px;
        display: grid;
        place-items: center;
        border-radius: 50%;
        background: var(--surface-2);
        color: var(--text-muted);
        font-size: 11px;
        font-weight: 700;
      }

      strong { color: var(--text-primary); }
    }

    .actions {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
      width: 100%;
    }

    .btn-allow {
      width: 100%;
      min-height: 50px;
      padding: 12px 18px;
      border-radius: 14px;
      border: none;
      background: var(--brand);
      color: #182000;
      font-size: 14px;
      font-weight: 700;
      cursor: pointer;
      box-shadow: none;
      transition: background-color 160ms ease, opacity 160ms ease;

      &:disabled { opacity: 0.6; cursor: not-allowed; }
      &:hover:not(:disabled) { background: var(--brand-2); }
      &:focus-visible { outline: 2px solid var(--text-primary); outline-offset: 2px; }
    }

    .btn-skip {
      min-height: 42px;
      padding: 8px 12px;
      border-radius: 12px;
      border: 0;
      background: transparent;
      color: var(--text-muted);
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      transition: color 160ms ease, background-color 160ms ease;

      &:hover { background: var(--surface-2); color: var(--text-primary); }
      &:focus-visible { outline: 2px solid var(--brand); outline-offset: 1px; }
    }

    .action-note {
      margin: 0;
      color: var(--text-muted);
      font-size: 11px;
      line-height: 1.4;
      text-align: center;
    }

    @media (max-height: 620px) {
      .page { padding-block: 18px; }
      .permission-shell { min-height: auto; gap: 26px; }
      .eyebrow { margin-bottom: 14px; }
      .privacy-note { margin-top: 14px; }
    }

    @media (min-width: 768px) {
      .permission-shell {
        min-height: min(680px, calc(100dvh - 56px));
      }
    }
  `]
})
export class LocationPermissionComponent implements OnInit {
  private router = inject(Router);
  private geo = inject(GeoLocationService);

  status = signal<'idle' | 'requesting' | 'denied'>('idle');

  ngOnInit(): void {
    // If the user already has coords or explicitly skipped, no need to be here
    if (this.geo.hasCoords() || this.geo.hasSkipped()) {
      this.router.navigate(['/discover']);
    }
  }

  async allow(): Promise<void> {
    this.status.set('requesting');
    const coords = await this.geo.requestPermission();
    if (coords) {
      this.router.navigate(['/discover']);
    } else {
      this.status.set('denied');
    }
  }

  skip(): void {
    // Only permanently skip when the user chooses to proceed without ever trying.
    // If the browser denied permission, do NOT mark as skipped — the user will be
    // re-prompted on next login so they're reminded to enable location in settings.
    if (this.status() !== 'denied') {
      this.geo.markSkipped();
    }
    this.router.navigate(['/discover']);
  }
}
