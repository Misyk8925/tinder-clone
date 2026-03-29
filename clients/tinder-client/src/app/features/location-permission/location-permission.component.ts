import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { GeoLocationService } from '../../core/services/geo-location.service';

@Component({
  selector: 'app-location-permission',
  imports: [],
  template: `
    <div class="page">
      <div class="card">
        <div class="icon-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="3"/>
            <path d="M12 1v4M12 19v4M1 12h4M19 12h4"/>
            <path d="M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83"/>
          </svg>
        </div>

        @if (status() !== 'denied') {
          <h1>Find people near you</h1>
          <p>Allow location access so we can show you matches in your area. Your exact location is never shown to others.</p>
        } @else {
          <h1>Location access denied</h1>
          <p>To use GPS location, enable it in your device settings:</p>
          <ol class="steps">
            <li>Open <strong>Settings → Privacy → Location Services</strong></li>
            <li>Find your browser and set it to <strong>While Using</strong></li>
            <li>Come back and tap <strong>Try again</strong></li>
          </ol>
          <p class="hint">Or skip to enter your city manually.</p>
        }

        <div class="actions">
          <button class="btn-allow" [disabled]="status() === 'requesting'" (click)="allow()">
            {{ status() === 'requesting' ? 'Requesting…' : status() === 'denied' ? 'Try again' : 'Allow location access' }}
          </button>
          <button class="btn-skip" (click)="skip()">Skip for now</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--bg);
      padding: 24px;
    }

    .card {
      max-width: 380px;
      width: 100%;
      background: var(--surface);
      border-radius: 24px;
      padding: 40px 32px;
      text-align: center;
      box-shadow: 0 8px 32px var(--shadow-sm);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
    }

    .icon-wrap {
      width: 80px;
      height: 80px;
      border-radius: 50%;
      background: linear-gradient(135deg, rgba(253,85,100,0.12), rgba(255,138,0,0.12));
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 8px;

      svg {
        width: 40px;
        height: 40px;
        color: #fd5564;
      }
    }

    h1 {
      margin: 0;
      font-size: 24px;
      font-weight: 700;
      color: var(--text-primary);
    }

    p {
      margin: 0;
      font-size: 15px;
      color: var(--text-secondary);
      line-height: 1.5;
    }

    .steps {
      margin: 0;
      padding: 0 0 0 20px;
      text-align: left;
      font-size: 14px;
      color: var(--text-secondary);
      line-height: 1.8;
      width: 100%;

      li { padding-left: 4px; }
      strong { color: var(--text-primary); }
    }

    .hint {
      font-size: 13px;
      color: var(--text-muted);
    }

    .actions {
      display: flex;
      flex-direction: column;
      gap: 12px;
      width: 100%;
      margin-top: 8px;
    }

    .btn-allow {
      width: 100%;
      padding: 16px;
      border-radius: 16px;
      border: none;
      background: linear-gradient(135deg, #fd5564, #ff8a00);
      color: #fff;
      font-size: 16px;
      font-weight: 700;
      cursor: pointer;
      box-shadow: 0 4px 15px rgba(253,85,100,0.3);
      transition: opacity 0.2s;

      &:disabled { opacity: 0.6; cursor: not-allowed; }
    }

    .btn-skip {
      width: 100%;
      padding: 14px;
      border-radius: 16px;
      border: 1.5px solid var(--border);
      background: transparent;
      color: var(--text-secondary);
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;
      transition: border-color 0.2s, color 0.2s;

      &:hover { border-color: var(--text-secondary); color: var(--text-primary); }
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
