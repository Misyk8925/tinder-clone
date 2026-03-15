import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ProfileService } from '../../core/services/profile.service';
import { KeycloakService } from '../../core/services/keycloak.service';
import { SubscriptionService } from '../../core/services/subscription.service';
import { ThemeService } from '../../core/services/theme.service';
import { Profile } from '../../core/models/profile.model';

@Component({
  selector: 'app-profile',
  template: `
    <div class="profile-page">
      <header class="header">
        <h1>My Profile</h1>
        <div class="header-actions">
          <button class="theme-toggle" (click)="theme.toggle()" [title]="theme.isDark() ? 'Switch to light mode' : 'Switch to dark mode'">
            {{ theme.isDark() ? '☀️' : '🌙' }}
          </button>
          <button class="edit-btn" (click)="goEdit()">Edit</button>
        </div>
      </header>

      @if (loading()) {
        <div class="loading"><div class="spinner"></div></div>
      } @else if (profile()) {
        <div class="profile-content">
          <div class="photo-section">
            @if (profile()!.photos?.length) {
              <img [src]="profile()!.photos[0].url" class="main-photo" [alt]="profile()!.name" />
            } @else {
              <div class="no-photo-placeholder">
                <span>{{ profile()!.name[0] }}</span>
              </div>
            }
            <button class="upload-btn" (click)="triggerUpload()">
              <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 9a3 3 0 0 0-3 3 3 3 0 0 0 3 3 3 3 0 0 0 3-3 3 3 0 0 0-3-3m0 8a5 5 0 0 1-5-5 5 5 0 0 1 5-5 5 5 0 0 1 5 5 5 5 0 0 1-5 5M3 5h2.5L7 3h10l1.5 2H21c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H3c-1.1 0-2-.9-2-2V7c0-1.1.9-2 2-2z"/></svg>
              Add Photo
            </button>
            <input type="file" accept="image/*" (change)="uploadPhoto($event)" hidden #fileInput />
          </div>

          <div class="info-section">
            <div class="name-row">
              <h2>{{ profile()!.name }}, {{ profile()!.age }}</h2>
              @if (profile()!.isActive) {
                <span class="badge active">Active</span>
              }
            </div>
            <p class="city">📍 {{ profile()!.city }}</p>

            @if (profile()!.bio) {
              <div class="section">
                <h4>About me</h4>
                <p>{{ profile()!.bio }}</p>
              </div>
            }

            <div class="section">
              <h4>Preferences</h4>
              <div class="pref-grid">
                <div class="pref-item">
                  <span class="pref-label">Looking for</span>
                  <span class="pref-value">{{ capitalize(profile()!.preferences?.gender) }}</span>
                </div>
                <div class="pref-item">
                  <span class="pref-label">Age range</span>
                  <span class="pref-value">{{ profile()!.preferences?.minAge }}–{{ profile()!.preferences?.maxAge }}</span>
                </div>
                <div class="pref-item">
                  <span class="pref-label">Distance</span>
                  <span class="pref-value">Up to {{ profile()!.preferences?.maxRange }} km</span>
                </div>
              </div>
            </div>

            @if (profile()!.hobbies?.length) {
              <div class="section">
                <h4>Hobbies</h4>
                <div class="hobbies">
                  @for (hobby of profile()!.hobbies; track hobby) {
                    <span class="hobby-tag">{{ formatHobby(hobby) }}</span>
                  }
                </div>
              </div>
            }
          </div>

          <div class="subscription-section">
            @if (isPremium()) {
              <div class="premium-badge-row">
                <span class="premium-badge">⭐ Premium</span>
                <span class="premium-label">You're a Premium member</span>
              </div>
              <button class="btn-manage-sub" (click)="manageSubscription()" [disabled]="subLoading()">
                {{ subLoading() ? 'Loading...' : 'Manage Subscription' }}
              </button>
            } @else {
              <div class="premium-card">
                <div class="premium-card-header">
                  <span class="crown">👑</span>
                  <div>
                    <h3>Get Premium</h3>
                    <p>Unlock unlimited swipes & more</p>
                  </div>
                  <span class="price">€10<small>/mo</small></span>
                </div>
                <ul class="perks">
                  <li>Unlimited swipes per day</li>
                  <li>See who liked you</li>
                  <li>Priority in discovery</li>
                </ul>
                <button class="btn-subscribe" (click)="subscribe()" [disabled]="subLoading()">
                  {{ subLoading() ? 'Loading...' : 'Subscribe Now' }}
                </button>
              </div>
            }
          </div>

          <div class="danger-section">
            <button class="btn-logout" (click)="logout()">Logout</button>
            <button class="btn-delete" (click)="deleteProfile()">Delete Account</button>
          </div>
        </div>
      } @else {
        <div class="no-profile">
          <div class="empty-icon">👤</div>
          <h3>No profile yet</h3>
          <p>Create your profile to start swiping!</p>
          <button class="btn-primary" (click)="goEdit()">Create Profile</button>
        </div>
      }
    </div>
  `,
  styles: [`
    .profile-page {
      display: flex;
      flex-direction: column;
      height: 100vh;
      background: var(--bg);
      padding-bottom: 70px;
      overflow-y: auto;
    }

    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 20px;
      background: var(--surface);
      box-shadow: 0 2px 8px var(--shadow-sm);
      position: sticky;
      top: 0;
      z-index: 10;

      h1 { margin: 0; font-size: 24px; font-weight: 700; color: var(--text-primary); }
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .theme-toggle {
      background: var(--surface-2);
      border: 1px solid var(--border);
      border-radius: 50%;
      width: 38px;
      height: 38px;
      font-size: 18px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.2s;

      &:hover { background: var(--border); }
    }

    .edit-btn {
      background: linear-gradient(135deg, #fd5564, #ff8a00);
      color: #fff;
      border: none;
      border-radius: 20px;
      padding: 8px 20px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
    }

    .loading {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .spinner {
      width: 40px; height: 40px;
      border: 3px solid var(--border-light);
      border-top: 3px solid #fd5564;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .profile-content {
      padding: 16px;
    }

    .photo-section {
      position: relative;
      width: 100%;
      height: 300px;
      border-radius: 20px;
      overflow: hidden;
      margin-bottom: 16px;
      background: var(--border);
    }

    .main-photo {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .no-photo-placeholder {
      width: 100%;
      height: 100%;
      background: linear-gradient(135deg, #fd5564, #ff8a00);
      display: flex;
      align-items: center;
      justify-content: center;

      span { font-size: 100px; color: rgba(255,255,255,0.5); font-weight: 700; }
    }

    .upload-btn {
      position: absolute;
      bottom: 12px;
      right: 12px;
      background: rgba(0,0,0,0.6);
      color: #fff;
      border: none;
      border-radius: 20px;
      padding: 8px 14px;
      font-size: 13px;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 6px;
      backdrop-filter: blur(4px);

      svg { width: 18px; height: 18px; }
    }

    .info-section {
      background: var(--surface);
      border-radius: 20px;
      padding: 20px;
      margin-bottom: 16px;
      box-shadow: 0 2px 8px var(--shadow-sm);
    }

    .name-row {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 4px;

      h2 { margin: 0; font-size: 24px; font-weight: 700; color: var(--text-primary); }
    }

    .badge {
      padding: 3px 10px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;

      &.active { background: #e8fdf1; color: #00a84f; }
    }

    .city { margin: 0 0 16px; color: var(--text-secondary); font-size: 15px; }

    .section {
      margin-bottom: 16px;

      h4 { margin: 0 0 8px; font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px; color: var(--text-muted); }
      p { margin: 0; color: var(--text-secondary); font-size: 15px; line-height: 1.5; }
    }

    .pref-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 10px;
    }

    .pref-item {
      background: var(--surface-2);
      border-radius: 12px;
      padding: 10px;
      text-align: center;

      .pref-label { display: block; font-size: 11px; color: var(--text-muted); margin-bottom: 4px; }
      .pref-value { display: block; font-size: 14px; font-weight: 600; color: var(--text-primary); }
    }

    .hobbies {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .hobby-tag {
      background: #fff0f1;
      color: #fd5564;
      border: 1px solid #ffd0d4;
      padding: 5px 14px;
      border-radius: 20px;
      font-size: 13px;
      font-weight: 500;
    }

    .subscription-section {
      padding: 0 0 16px;
    }

    .premium-badge-row {
      display: flex;
      align-items: center;
      gap: 10px;
      background: linear-gradient(135deg, #7b2ff7, #f107a3);
      border-radius: 16px;
      padding: 14px 18px;
      margin-bottom: 10px;
    }

    .premium-badge {
      font-size: 20px;
    }

    .premium-label {
      flex: 1;
      color: #fff;
      font-weight: 600;
      font-size: 15px;
    }

    .btn-manage-sub {
      width: 100%;
      padding: 12px;
      border-radius: 14px;
      border: 2px solid #7b2ff7;
      background: var(--surface);
      color: #7b2ff7;
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;

      &:disabled { opacity: 0.6; cursor: default; }
    }

    .premium-card {
      background: var(--surface);
      border-radius: 20px;
      padding: 20px;
      box-shadow: 0 2px 12px rgba(123,47,247,0.12);
      border: 1.5px solid #ede5ff;
    }

    .premium-card-header {
      display: flex;
      align-items: center;
      gap: 14px;
      margin-bottom: 16px;

      .crown { font-size: 32px; }

      div { flex: 1; }
      h3 { margin: 0; font-size: 18px; font-weight: 700; color: var(--text-primary); }
      p { margin: 2px 0 0; font-size: 13px; color: var(--text-muted); }
    }

    .price {
      font-size: 24px;
      font-weight: 800;
      color: #7b2ff7;

      small { font-size: 13px; font-weight: 500; color: var(--text-muted); }
    }

    .perks {
      list-style: none;
      padding: 0;
      margin: 0 0 16px;
      display: flex;
      flex-direction: column;
      gap: 8px;

      li {
        font-size: 14px;
        color: var(--text-secondary);
        padding-left: 20px;
        position: relative;

        &::before {
          content: '✓';
          position: absolute;
          left: 0;
          color: #7b2ff7;
          font-weight: 700;
        }
      }
    }

    .btn-subscribe {
      width: 100%;
      padding: 14px;
      border-radius: 14px;
      border: none;
      background: linear-gradient(135deg, #7b2ff7, #f107a3);
      color: #fff;
      font-size: 16px;
      font-weight: 700;
      cursor: pointer;
      letter-spacing: 0.3px;

      &:disabled { opacity: 0.6; cursor: default; }
    }

    .danger-section {
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding: 16px;
    }

    .btn-logout {
      width: 100%;
      padding: 14px;
      border-radius: 14px;
      border: 2px solid var(--border);
      background: var(--surface);
      color: var(--text-secondary);
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
    }

    .btn-delete {
      width: 100%;
      padding: 14px;
      border-radius: 14px;
      border: none;
      background: #fff0f1;
      color: #fd5564;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
    }

    .no-profile {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 16px;
      text-align: center;
      padding: 40px;

      .empty-icon { font-size: 64px; }
      h3 { margin: 0; font-size: 22px; color: var(--text-primary); }
      p { margin: 0; color: var(--text-muted); }
    }

    .btn-primary {
      background: linear-gradient(135deg, #fd5564, #ff8a00);
      color: #fff;
      border: none;
      border-radius: 30px;
      padding: 12px 32px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
    }
  `]
})
export class ProfileComponent implements OnInit {
  private profileService = inject(ProfileService);
  private keycloak = inject(KeycloakService);
  private subscriptionService = inject(SubscriptionService);
  private router = inject(Router);
  theme = inject(ThemeService);

  profile = signal<Profile | null>(null);
  loading = signal(true);
  subLoading = signal(false);
  isPremium = signal(false);

  ngOnInit(): void {
    this.isPremium.set(this.keycloak.hasRole('premium'));
    this.profileService.getMe().subscribe({
      next: (p) => { this.profile.set(p); this.loading.set(false); },
      error: () => { this.profile.set(null); this.loading.set(false); }
    });
  }

  subscribe(): void {
    this.subLoading.set(true);
    this.subscriptionService.createCheckoutSession().subscribe({
      next: (url) => { window.location.href = url; },
      error: () => { alert('Failed to start checkout. Please try again.'); this.subLoading.set(false); }
    });
  }

  manageSubscription(): void {
    this.subLoading.set(true);
    this.subscriptionService.createPortalSession().subscribe({
      next: (url) => { window.location.href = url; },
      error: () => { alert('Failed to open billing portal. Please try again.'); this.subLoading.set(false); }
    });
  }

  goEdit(): void {
    this.router.navigate(['/profile/edit']);
  }

  logout(): void {
    this.keycloak.logout();
  }

  deleteProfile(): void {
    if (!confirm('Are you sure? This cannot be undone.')) return;
    this.profileService.deleteProfile().subscribe({
      next: () => this.keycloak.logout(),
      error: () => alert('Failed to delete profile')
    });
  }

  triggerUpload(): void {
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    input?.click();
  }

  uploadPhoto(e: Event): void {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const position = this.profile()?.photos?.length ?? 0;
    this.profileService.uploadPhoto(file, position).subscribe({
      next: () => this.ngOnInit(),
      error: () => alert('Photo upload failed')
    });
  }

  capitalize(s?: string): string {
    if (!s) return '';
    return s.charAt(0).toUpperCase() + s.slice(1);
  }

  formatHobby(hobby: string): string {
    return hobby.charAt(0) + hobby.slice(1).toLowerCase().replace(/_/g, ' ');
  }
}
