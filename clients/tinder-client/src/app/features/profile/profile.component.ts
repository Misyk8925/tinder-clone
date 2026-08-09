import { Component, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule } from 'lucide-angular';
import { ProfileService } from '../../core/services/profile.service';
import { KeycloakService } from '../../core/services/keycloak.service';
import { SubscriptionService } from '../../core/services/subscription.service';
import { ThemeService } from '../../core/services/theme.service';
import { Photo, Profile } from '../../core/models/profile.model';

@Component({
  selector: 'app-profile',
  imports: [LucideAngularModule],
  template: `
    <div class="profile-page">
      <header class="header">
        <h1>Profile</h1>
        <div class="header-actions">
          <button type="button" class="theme-toggle" (click)="theme.toggle()"
            [title]="theme.isDark() ? 'Switch to light mode' : 'Switch to dark mode'"
            [attr.aria-label]="theme.isDark() ? 'Switch to light mode' : 'Switch to dark mode'">
            <lucide-icon [name]="theme.isDark() ? 'sun' : 'moon'" [size]="18" strokeWidth="2.2" />
          </button>
          <button type="button" class="edit-btn" (click)="goEdit()">
            <lucide-icon name="pencil" [size]="16" strokeWidth="2.1" />
            Edit
          </button>
        </div>
      </header>

      @if (loading()) {
        <div class="loading"><div class="spinner"></div></div>
      } @else if (profile()) {
        <div class="profile-content">

          <!-- Photo Hero + Manager -->
          <div class="photo-hero">
            @if (profile()!.photos?.length) {
              <img [src]="profile()!.photos[0].url" alt="Profile photo" />
            } @else {
              <div class="photo-placeholder">
                <span>{{ profile()!.name?.[0] ?? '?' }}</span>
              </div>
            }
            <div class="photo-count">{{ profile()!.photos?.length ?? 0 }}/5</div>
            <div class="photo-hero-actions">
              <button
                type="button"
                class="btn-manage"
                [class.active]="managePhotos()"
                [attr.aria-pressed]="managePhotos()"
                [attr.aria-label]="managePhotos() ? 'Finish managing photos' : 'Manage photos'"
                (click)="toggleManagePhotos()">
                <lucide-icon [name]="managePhotos() ? 'check' : 'images'" [size]="16" strokeWidth="2.2" />
                {{ managePhotos() ? 'Done' : 'Manage photos' }}
              </button>
            </div>
          </div>

          @if (managePhotos()) {
            <div class="photo-manager">
              <div class="manager-header">
                <div>
                  <h3>Manage photos</h3>
                  <p>Your first photo is shown on your profile.</p>
                </div>
                <span class="manager-count">{{ profile()!.photos.length }}/5 added</span>
              </div>
              <div class="manager-list">
                @for (slot of photoSlots(); track $index) {
                  <div class="manager-row"
                       [class.filled]="!!slot"
                       [class.uploading]="uploadingSlot() === $index">
                    <div class="manager-thumb">
                      @if (slot) {
                        <img [src]="slot.url" [alt]="'Photo ' + ($index + 1)" />
                      } @else {
                        <div class="thumb-empty">{{ $index + 1 }}</div>
                      }
                    </div>
                    <div class="manager-meta">
                      <div class="manager-title">Photo {{ $index + 1 }}</div>
                      <div class="manager-sub">{{ $index === 0 ? 'Profile photo' : 'Optional' }}</div>
                    </div>
                    <div class="manager-actions">
                      @if (slot) {
                        <button type="button" class="btn-ghost" [attr.aria-label]="'Replace photo ' + ($index + 1)" (click)="triggerUploadAt($index)">
                          <lucide-icon name="refresh-cw" [size]="14" strokeWidth="2.2" />
                          Replace
                        </button>
                        <button type="button" class="btn-danger" [attr.aria-label]="'Remove photo ' + ($index + 1)" (click)="deletePhoto(slot.photoID)">
                          <lucide-icon name="trash-2" [size]="14" strokeWidth="2.2" />
                          Remove
                        </button>
                      } @else if ($index === (profile()!.photos?.length ?? 0)) {
                        <button type="button" class="btn-add" [attr.aria-label]="'Add photo ' + ($index + 1)" (click)="triggerUploadAt($index)">
                          <lucide-icon name="plus" [size]="15" strokeWidth="2.4" />
                          Add
                        </button>
                      } @else {
                        <span class="locked-state" [attr.aria-label]="'Photo ' + ($index + 1) + ' is locked'">
                          <lucide-icon name="lock-keyhole" [size]="13" strokeWidth="2.2" />
                          Locked
                        </span>
                      }
                    </div>

                    @if (uploadingSlot() === $index) {
                      <div class="upload-overlay">
                        <div class="upload-spinner"></div>
                      </div>
                    }
                  </div>
                }
              </div>
              <input type="file" accept="image/*" (change)="uploadPhoto($event)" hidden #fileInput />
            </div>
          }

          <!-- Info section -->
          <div class="info-section">
            <div class="name-row">
              <h2>{{ profile()!.name }}, {{ profile()!.age }}</h2>
              @if (profile()!.isActive) {
                <span class="badge active"><lucide-icon name="activity" [size]="12" strokeWidth="2" /> Active now</span>
              }
            </div>
            @if (profile()!.city && profile()!.city !== 'Unknown') {
              <p class="city">
                <lucide-icon name="map-pin" [size]="14" strokeWidth="1.8" />
                {{ profile()!.city }}
              </p>
            }

            @if (profile()!.bio) {
              <div class="section">
                <h4>About</h4>
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
                <h4>Interests</h4>
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
                  <lucide-icon name="crown" [size]="20" strokeWidth="1.8" />
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
              <button type="button" class="account-row" (click)="goEdit()">
                <span class="account-row-icon">
                  <lucide-icon name="pencil" [size]="16" strokeWidth="2.2" />
                </span>
                <span class="account-row-label">Edit profile</span>
                <span class="account-row-chevron"><lucide-icon name="chevron-right" [size]="18" strokeWidth="1.8" /></span>
              </button>
              <div class="account-divider"></div>
              @if (!isPremium()) {
                <button type="button" class="account-row" (click)="subscribe()" [disabled]="subLoading()">
                  <span class="account-row-icon premium-icon">
                    <lucide-icon name="crown" [size]="16" strokeWidth="2.2" />
                  </span>
                  <span class="account-row-label">{{ subLoading() ? 'Loading…' : 'Upgrade to premium' }}</span>
                  <span class="account-row-badge">€10/month</span>
                </button>
                <div class="account-divider"></div>
              }
              <button type="button" class="account-row" (click)="logout()">
                <span class="account-row-icon logout">
                  <lucide-icon name="log-out" [size]="16" strokeWidth="2.2" />
                </span>
                <span class="account-row-label">Log out</span>
                <span class="account-row-chevron"><lucide-icon name="chevron-right" [size]="18" strokeWidth="1.8" /></span>
              </button>
            </div>

            <p class="account-section-label danger-label">Danger Zone</p>
            <div class="account-list">
              <button type="button" class="account-row danger" (click)="deleteProfile()">
                <span class="account-row-icon danger">
                  <lucide-icon name="trash-2" [size]="16" strokeWidth="2.2" />
                </span>
                <span class="account-row-label">Delete account</span>
                <span class="account-row-chevron"><lucide-icon name="chevron-right" [size]="18" strokeWidth="1.8" /></span>
              </button>
            </div>
          </div>

        </div>
      } @else {
        <div class="no-profile">
          <div class="empty-icon">
            <lucide-icon name="user-round-plus" [size]="64" strokeWidth="1.5" />
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
      padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 64px);
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
      min-height: var(--mobile-topbar-height);
      padding: 0 12px;
      background: var(--header-surface);
      position: sticky;
      top: 0;
      z-index: 10;
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);

      h1 {
        margin: 0;
        font-size: 20px;
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
      appearance: none;
      padding: 0;
      background: transparent;
      border: 0;
      border-radius: 50%;
      width: 40px;
      height: 40px;
      cursor: pointer;
      display: grid;
      place-items: center;
      color: var(--text-primary);
      box-shadow: none;
      transition: color 160ms ease, background 160ms ease;

      lucide-icon {
        width: 18px;
        height: 18px;
        display: grid;
        place-items: center;
        line-height: 0;
      }

      &:hover { background: var(--surface-2); }
      &:active { background: var(--border-light); }
      &:focus-visible { outline: 2px solid var(--brand); outline-offset: 1px; }
    }

    [data-theme="dark"] .theme-toggle {
      background: transparent;
      color: var(--text-primary);

      &:hover { background: var(--surface-2); }
    }

    .edit-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 7px;
      background: var(--brand);
      color: var(--text-primary);
      border: none;
      border-radius: 20px;
      min-height: 40px;
      padding: 6px 16px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;

      box-shadow: 0 8px 18px rgba(109, 144, 55, 0.18);

      &:hover { background: var(--brand-2); }
      &:active { transform: scale(0.97); }
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
      padding: 16px 16px 28px;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    /* ── Info Section ── */
    .info-section {
      background: var(--card-surface);
      border-radius: 20px;
      padding: 20px 18px 18px;
      box-shadow: 0 10px 32px var(--shadow-sm);
      border: 1px solid var(--card-border);
    }

    .name-row {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 5px;

      h2 {
        margin: 0;
        font-size: 23px;
        font-weight: 700;
        color: var(--text-primary);
        letter-spacing: -0.3px;
      }
    }

    .badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
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
      margin: 0 0 16px;
      color: var(--text-secondary);
      font-size: 14px;
      font-weight: 500;
      display: flex;
      align-items: center;
      gap: 6px;

      lucide-icon { color: var(--text-muted); }
    }

    .section {
      margin: 0;
      padding: 16px 0;
      border-top: 1px solid var(--border-light);

      &.no-margin { padding-bottom: 0; }

      h4 {
        margin: 0 0 12px;
        font-size: 11px;
        letter-spacing: 0.06em;
        text-transform: uppercase;
        font-weight: 600;
        color: var(--text-muted);
      }

      p {
        margin: 0;
        color: var(--text-secondary);
        font-size: 15px;
        line-height: 1.55;
      }
    }

    .pref-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0;
      padding: 2px 0;
    }

    .pref-item {
      min-width: 0;
      padding: 2px 12px;
      border-left: 1px solid var(--border-light);

      &:first-child {
        padding-left: 0;
        border-left: 0;
      }

      &:last-child { padding-right: 0; }

      .pref-label {
        display: block;
        font-size: 9px;
        color: var(--text-muted);
        margin-bottom: 4px;
        text-transform: uppercase;
        font-weight: 600;
        letter-spacing: 0.05em;
        white-space: nowrap;
      }

      .pref-value {
        display: block;
        font-size: 15px;
        font-weight: 600;
        color: var(--text-primary);
        white-space: nowrap;
      }
    }

    .hobbies {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .hobby-tag {
      background: transparent;
      color: var(--text-secondary);
      border: 1px solid var(--border);
      padding: 5px 12px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 600;
    }

    /* ── Premium Banner (active subscriber) ── */
    .premium-banner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      background: #2f3031;
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 20px;
      padding: 14px 18px;
      box-shadow: var(--shadow-float);
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
      background: var(--brand);
      display: flex;
      align-items: center;
      justify-content: center;
      color: #2f3031;
      flex-shrink: 0;
    }

    .premium-banner-title {
      display: block;
      color: #fff;
      font-size: 15px;
      font-weight: 700;
      line-height: 1.2;
    }

    .premium-banner-sub {
      display: block;
      margin-top: 2px;
      color: rgba(255, 255, 255, 0.68);
      font-size: 12px;
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
      padding-bottom: 8px;
    }

    .account-section-label {
      margin: 0 0 8px 2px;
      font-size: 10px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.09em;
      color: var(--text-muted);

      &.danger-label { margin-top: 22px; color: var(--text-muted); }
    }

    .account-list {
      background: var(--card-surface);
      border: 1px solid var(--card-border);
      border-radius: 18px;
      overflow: hidden;
      box-shadow: 0 8px 28px var(--shadow-sm);
    }

    .account-row {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 13px;
      min-height: 54px;
      padding: 10px 14px;
      background: none;
      border: none;
      cursor: pointer;
      text-align: left;
      transition: background 0.12s;

      &:hover { background: var(--surface-2); }
      &:active { background: var(--border-light); }
      &:disabled { opacity: 0.6; cursor: default; }

      &.danger .account-row-label { color: #d34b4b; }
    }

    .account-row-icon {
      width: 20px;
      height: 20px;
      border-radius: 0;
      background: transparent;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      color: var(--text-secondary);

      lucide-icon { display: flex; }

      &.logout { background: transparent; color: var(--text-secondary); }
      &.danger { background: transparent; color: #d34b4b; }
      &.premium-icon { background: transparent; color: var(--gold-2); }
    }

    .account-row-label {
      flex: 1;
      font-size: 14px;
      font-weight: var(--ui-label-weight);
      color: var(--text-primary);
    }

    .account-row-chevron {
      display: flex;
      color: var(--text-muted);
      opacity: 0.72;
    }


    .account-row-badge {
      font-size: 11px;
      font-weight: 600;
      color: var(--gold-2);
      background: transparent;
      border: 0;
      padding: 0;
      border-radius: 0;
    }

    .account-divider {
      height: 1px;
      background: var(--border-light);
      margin-left: 47px;
    }

    @media (max-width: 360px) {
      .pref-item {
        padding-inline: 8px;

        .pref-label { font-size: 8px; }
        .pref-value { font-size: 14px; }
      }
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

    .empty-icon { color: var(--text-muted); }

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
  managePhotos = signal(false);
  private toastTimer: ReturnType<typeof setTimeout> | null = null;

  private uploadPosition = 0;

  toggleManagePhotos(): void {
    this.managePhotos.set(!this.managePhotos());
  }

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
