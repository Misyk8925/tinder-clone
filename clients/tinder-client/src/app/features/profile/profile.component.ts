import { Component, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ProfileService } from '../../core/services/profile.service';
import { KeycloakService } from '../../core/services/keycloak.service';
import { SubscriptionService } from '../../core/services/subscription.service';
import { ThemeService } from '../../core/services/theme.service';
import { Photo, Profile } from '../../core/models/profile.model';

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

          <!-- Photo Gallery: mosaic layout -->
          <div class="photo-gallery">
            @for (slot of photoSlots(); track $index) {
              <div class="photo-slot"
                   [class.slot-hero]="$index === 0"
                   [class.slot-thumb]="$index > 0"
                   [class.thumb-1]="$index === 1"
                   [class.thumb-2]="$index === 2"
                   [class.thumb-3]="$index === 3"
                   [class.thumb-4]="$index === 4"
                   [class.uploading]="uploadingSlot() === $index">

                @if (slot) {
                  <img [src]="slot.url" class="slot-img" [alt]="'Photo ' + ($index + 1)" />
                  <button class="slot-delete" (click)="deletePhoto(slot.photoID)" title="Remove photo">
                    <svg viewBox="0 0 24 24" fill="currentColor" width="10" height="10"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
                  </button>
                } @else {
                  <button class="slot-add" (click)="triggerUploadAt($index)">
                    <svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>
                    <span>Add photo</span>
                  </button>
                }

                @if (uploadingSlot() === $index) {
                  <div class="upload-overlay">
                    <div class="upload-spinner"></div>
                  </div>
                }
              </div>
            }
            <input type="file" accept="image/*" (change)="uploadPhoto($event)" hidden #fileInput />
          </div>

          <!-- Info section -->
          <div class="info-section">
            <div class="name-row">
              <h2>{{ profile()!.name }}, {{ profile()!.age }}</h2>
              @if (profile()!.isActive) {
                <span class="badge active">● Active</span>
              }
            </div>
            @if (profile()!.city && profile()!.city !== 'Unknown') {
              <p class="city">
                <svg viewBox="0 0 24 24" fill="currentColor" width="13" height="13"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/></svg>
                {{ profile()!.city }}
              </p>
            }

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
                  <span class="pref-value">{{ profile()!.preferences?.maxRange }} km</span>
                </div>
              </div>
            </div>

            @if (profile()!.hobbies?.length) {
              <div class="section no-margin">
                <h4>Hobbies</h4>
                <div class="hobbies">
                  @for (hobby of profile()!.hobbies; track hobby) {
                    <span class="hobby-tag">{{ formatHobby(hobby) }}</span>
                  }
                </div>
              </div>
            }
          </div>

          <!-- Premium: show banner for subscribers, nothing for non-subscribers (no upsell here) -->
          @if (isPremium()) {
            <div class="premium-banner">
              <div class="premium-banner-left">
                <div class="premium-crown-wrap">
                  <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M5 16L3 5l5.5 5L12 4l3.5 6L21 5l-2 11H5zm2 2h10v2H7v-2z"/></svg>
                </div>
                <div>
                  <span class="premium-banner-title">Premium Active</span>
                  <span class="premium-banner-sub">Unlimited swipes & more</span>
                </div>
              </div>
              <button class="premium-banner-btn" (click)="manageSubscription()" [disabled]="subLoading()">
                {{ subLoading() ? '...' : 'Manage' }}
              </button>
            </div>
          }

          <!-- Account section -->
          <div class="account-section">
            <p class="account-section-label">Account</p>
            <div class="account-list">
              <button class="account-row" (click)="goEdit()">
                <span class="account-row-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                </span>
                <span class="account-row-label">Edit Profile</span>
                <span class="account-row-chevron">›</span>
              </button>
              <div class="account-divider"></div>
              @if (!isPremium()) {
                <button class="account-row" (click)="subscribe()" [disabled]="subLoading()">
                  <span class="account-row-icon premium-icon">
                    <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M5 16L3 5l5.5 5L12 4l3.5 6L21 5l-2 11H5zm2 2h10v2H7v-2z"/></svg>
                  </span>
                  <span class="account-row-label">{{ subLoading() ? 'Loading...' : 'Upgrade to Premium' }}</span>
                  <span class="account-row-badge">€10/mo</span>
                </button>
                <div class="account-divider"></div>
              }
              <button class="account-row" (click)="logout()">
                <span class="account-row-icon logout">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                </span>
                <span class="account-row-label">Log Out</span>
                <span class="account-row-chevron">›</span>
              </button>
            </div>

            <p class="account-section-label danger-label">Danger Zone</p>
            <div class="account-list">
              <button class="account-row danger" (click)="deleteProfile()">
                <span class="account-row-icon danger">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
                </span>
                <span class="account-row-label">Delete Account</span>
                <span class="account-row-chevron">›</span>
              </button>
            </div>
          </div>

        </div>
      } @else {
        <div class="no-profile">
          <div class="empty-icon">
            <svg viewBox="0 0 24 24" fill="currentColor" width="64" height="64" style="color: var(--text-muted)"><path d="M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v2.4h19.2v-2.4c0-3.2-6.4-4.8-9.6-4.8z"/></svg>
          </div>
          <h3>No profile yet</h3>
          <p>Create your profile to start swiping!</p>
          <button class="btn-primary" (click)="goEdit()">Create Profile</button>
          <button class="btn-logout-text" (click)="logout()">Logout</button>
        </div>
      }
    </div>

    @if (toast()) {
      <div class="toast-msg">{{ toast() }}</div>
    }
  `,
  styles: [`
    .profile-page {
      display: flex;
      flex-direction: column;
      height: 100dvh;
      background: transparent;
      padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 88px);
      overflow-y: auto;
    }

    @media (min-width: 768px) {
      .profile-page {
        padding-bottom: 32px;
      }

      .profile-content {
        max-width: 640px;
        margin: 0 auto;
        width: 100%;
      }

      .photo-gallery {
        grid-template-rows: 300px 148px;
      }
    }

    /* ── Header ── */
    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 18px 20px;
      background: var(--surface-glass);
      border-bottom: 1px solid var(--border);
      position: sticky;
      top: 0;
      z-index: 10;
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      box-shadow: 0 8px 20px var(--shadow-sm);

      h1 {
        margin: 0;
        font-size: 22px;
        font-weight: 700;
        color: var(--text-primary);
        letter-spacing: -0.3px;
      }
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .theme-toggle {
      background: rgba(255,255,255,0.75);
      border: 1px solid var(--border);
      border-radius: 50%;
      width: 36px;
      height: 36px;
      font-size: 16px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.2s;

      &:hover { background: var(--surface-2); }
    }

    [data-theme="dark"] .theme-toggle {
      background: rgba(36, 33, 45, 0.9);
      border-color: rgba(255,255,255,0.08);
      color: #fff;
    }

    .edit-btn {
      background: var(--brand-gradient);
      color: #fff;
      border: none;
      border-radius: 20px;
      padding: 8px 20px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;

      &:active { opacity: 0.85; }
    }

    /* ── Loading ── */
    .loading {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .spinner {
      width: 38px; height: 38px;
      border: 3px solid var(--border);
      border-top: 3px solid var(--brand);
      border-radius: 50%;
      animation: spin 0.75s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    /* ── Profile Content ── */
    .profile-content {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    /* ── Info Section ── */
    .info-section {
      background: var(--surface);
      border-radius: 20px;
      padding: 20px;
      box-shadow: 0 12px 30px var(--shadow-sm);
      border: 1px solid var(--border-light);
    }

    .name-row {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 6px;

      h2 {
        margin: 0;
        font-size: 24px;
        font-weight: 700;
        color: var(--text-primary);
        letter-spacing: -0.3px;
      }
    }

    .badge {
      padding: 3px 10px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;
      flex-shrink: 0;

      &.active {
        background: rgba(0, 168, 79, 0.12);
        color: #00a84f;
        border: 1px solid rgba(0, 168, 79, 0.2);
      }
    }

    .city {
      margin: 0 0 18px;
      color: var(--text-secondary);
      font-size: 14px;
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .section {
      margin-bottom: 18px;

      &.no-margin { margin-bottom: 0; }

      h4 {
        margin: 0 0 10px;
        font-size: 11px;
        text-transform: uppercase;
        letter-spacing: 0.8px;
        font-weight: 600;
        color: var(--text-muted);
      }

      p {
        margin: 0;
        color: var(--text-secondary);
        font-size: 15px;
        line-height: 1.6;
      }
    }

    .pref-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 8px;
    }

    .pref-item {
      background: var(--surface-2);
      border-radius: 14px;
      padding: 12px 10px;
      text-align: center;

      .pref-label {
        display: block;
        font-size: 10px;
        color: var(--text-muted);
        margin-bottom: 5px;
        text-transform: uppercase;
        font-weight: 500;
      }

      .pref-value {
        display: block;
        font-size: 14px;
        font-weight: 600;
        color: var(--text-primary);
      }
    }

    .hobbies {
      display: flex;
      flex-wrap: wrap;
      gap: 7px;
    }

    .hobby-tag {
      background: rgba(255, 68, 88, 0.12);
      color: var(--brand);
      border: 1px solid rgba(255, 68, 88, 0.22);
      padding: 5px 14px;
      border-radius: 20px;
      font-size: 13px;
      font-weight: 500;
    }

    /* ── Premium Banner (active subscriber) ── */
    .premium-banner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      background: linear-gradient(135deg, var(--gold), var(--gold-2));
      border-radius: 18px;
      padding: 14px 18px;
      box-shadow: 0 10px 24px rgba(246, 181, 63, 0.35);
    }

    .premium-banner-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .premium-crown-wrap {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: rgba(255,255,255,0.18);
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      flex-shrink: 0;
    }

    .premium-banner-title {
      display: block;
      color: #fff;
      font-size: 15px;
      font-weight: 700;
      line-height: 1.2;
    }


    .premium-banner-btn {
      background: rgba(255,255,255,0.2);
      border: 1px solid rgba(255,255,255,0.3);
      color: #fff;
      border-radius: 20px;
      padding: 7px 18px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      flex-shrink: 0;

      &:disabled { opacity: 0.5; cursor: default; }
    }

    /* ── Account Section ── */
    .account-section {
      padding-bottom: 4px;
    }

    .account-section-label {
      margin: 0 0 6px 4px;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.7px;
      color: var(--text-muted);

      &.danger-label { margin-top: 20px; color: var(--brand); }
    }

    .account-list {
      background: var(--surface);
      border-radius: 18px;
      overflow: hidden;
      box-shadow: 0 1px 4px var(--shadow-sm);
    }

    .account-row {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 16px;
      background: none;
      border: none;
      cursor: pointer;
      text-align: left;
      transition: background 0.12s;

      &:hover { background: var(--surface-2); }
      &:active { background: var(--border-light); }
      &:disabled { opacity: 0.6; cursor: default; }

      &.danger .account-row-label { color: var(--brand); }
    }

    .account-row-icon {
      width: 32px;
      height: 32px;
      border-radius: 9px;
      background: var(--surface-2);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      color: var(--text-secondary);

      svg { width: 16px; height: 16px; }

      &.logout { background: rgba(245, 158, 11, 0.12); color: #f59e0b; }
      &.danger { background: rgba(255, 68, 88, 0.12); color: var(--brand); }
      &.premium-icon { background: rgba(246, 181, 63, 0.16); color: var(--gold-2); }
    }

    .account-row-label {
      flex: 1;
      font-size: 15px;
      font-weight: 500;
      color: var(--text-primary);
    }


    .account-row-badge {
      font-size: 12px;
      font-weight: 600;
      color: var(--gold-2);
      background: rgba(246, 181, 63, 0.14);
      border: 1px solid rgba(246, 181, 63, 0.25);
      padding: 3px 9px;
      border-radius: 10px;
    }

    .account-divider {
      height: 1px;
      background: var(--border-light);
      margin-left: 60px;
    }

    /* ── No Profile Empty State ── */
    .no-profile {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 16px;
      text-align: center;
      padding: 40px;

      .empty-icon { opacity: 0.4; }
      h3 { margin: 0; font-size: 22px; font-weight: 700; color: var(--text-primary); }
      p { margin: 0; color: var(--text-muted); font-size: 15px; }

      .btn-logout-text {
        background: none;
        border: none;
        color: var(--text-muted);
        font-size: 14px;
        cursor: pointer;
        padding: 4px 8px;
        text-decoration: underline;
      }
    }

    .btn-primary {
      background: var(--brand-gradient);
      color: #fff;
      border: none;
      border-radius: 30px;
      padding: 13px 34px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;

      &:active { opacity: 0.85; }
    }

    /* ── Toast ── */
    .toast-msg {
      position: fixed;
      bottom: 90px;
      left: 50%;
      transform: translateX(-50%);
      background: rgba(20, 20, 20, 0.94);
      color: #fff;
      padding: 12px 20px;
      border-radius: 24px;
      font-size: 14px;
      font-weight: 500;
      z-index: 2000;
      white-space: nowrap;
      max-width: 90vw;
      text-align: center;
      animation: toastIn 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
      box-shadow: 0 4px 20px rgba(0,0,0,0.35);
      backdrop-filter: blur(12px);
    }

    @keyframes toastIn {
      from { opacity: 0; transform: translateX(-50%) translateY(10px) scale(0.95); }
      to { opacity: 1; transform: translateX(-50%) translateY(0) scale(1); }
    }
  `]
})
export class ProfileComponent implements OnInit {
  @ViewChild('fileInput') private fileInputRef!: ElementRef<HTMLInputElement>;

  private profileService = inject(ProfileService);
  private keycloak = inject(KeycloakService);
  private subscriptionService = inject(SubscriptionService);
  private router = inject(Router);
  theme = inject(ThemeService);

  profile = signal<Profile | null>(null);
  loading = signal(true);
  subLoading = signal(false);
  isPremium = signal(false);
  uploadingSlot = signal<number | null>(null);
  toast = signal<string | null>(null);
  private toastTimer: ReturnType<typeof setTimeout> | null = null;

  private uploadPosition = 0;

  private showToast(msg: string): void {
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toast.set(msg);
    this.toastTimer = setTimeout(() => this.toast.set(null), 4000);
  }

  photoSlots(): (Photo | null)[] {
    const photos = this.profile()?.photos ?? [];
    const slots: (Photo | null)[] = Array(5).fill(null);
    photos.forEach((p, i) => { if (i < 5) slots[i] = p; });
    return slots;
  }

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
      error: (err: HttpErrorResponse) => {
        this.subLoading.set(false);
        if (err.status === 429) {
          this.showToast('Too many requests. Please wait before trying again.');
        } else {
          this.showToast('Failed to start checkout. Please try again.');
        }
      }
    });
  }

  manageSubscription(): void {
    this.subLoading.set(true);
    this.subscriptionService.createPortalSession().subscribe({
      next: (url) => { window.location.href = url; },
      error: (err: HttpErrorResponse) => {
        this.subLoading.set(false);
        if (err.status === 429) {
          this.showToast('Too many requests. Please wait before trying again.');
        } else {
          this.showToast('Failed to open billing portal. Please try again.');
        }
      }
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
      error: (err: HttpErrorResponse) => {
        if (err.status === 429) {
          this.showToast('Too many requests. Please wait before trying again.');
        } else {
          this.showToast('Failed to delete profile. Please try again.');
        }
      }
    });
  }

  triggerUploadAt(position: number): void {
    this.uploadPosition = position;
    this.fileInputRef.nativeElement.value = '';
    this.fileInputRef.nativeElement.click();
  }

  async uploadPhoto(e: Event): Promise<void> {
    let file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;

    this.uploadingSlot.set(this.uploadPosition);

    if (file.type === 'image/heic' || file.type === 'image/heif' || /\.(heic|heif)$/i.test(file.name)) {
      const heic2any = (await import('heic2any')).default;
      const converted = await heic2any({ blob: file, toType: 'image/jpeg', quality: 0.9 });
      const blob = Array.isArray(converted) ? converted[0] : converted;
      file = new File([blob], file.name.replace(/\.(heic|heif)$/i, '.jpg'), { type: 'image/jpeg' });
    }

    this.profileService.uploadPhoto(file, this.uploadPosition).subscribe({
      next: () => {
        this.uploadingSlot.set(null);
        this.ngOnInit();
      },
      error: (err: HttpErrorResponse) => {
        this.uploadingSlot.set(null);
        if (err.status === 429) {
          this.showToast('Too many uploads. Please wait before uploading again.');
        } else {
          this.showToast('Photo upload failed. Please try again.');
        }
      }
    });
  }

  deletePhoto(photoId: string): void {
    if (!confirm('Remove this photo?')) return;
    this.profileService.deletePhoto(photoId).subscribe({
      next: () => this.ngOnInit(),
      error: (err: HttpErrorResponse) => {
        if (err.status === 429) {
          this.showToast('Too many requests. Please wait before trying again.');
        } else {
          this.showToast('Failed to delete photo. Please try again.');
        }
      }
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
