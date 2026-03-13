import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ProfileService } from '../../core/services/profile.service';
import { KeycloakService } from '../../core/services/keycloak.service';
import { Profile } from '../../core/models/profile.model';

@Component({
  selector: 'app-profile',
  template: `
    <div class="profile-page">
      <header class="header">
        <h1>My Profile</h1>
        <button class="edit-btn" (click)="goEdit()">Edit</button>
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
      background: #f5f5f5;
      padding-bottom: 70px;
      overflow-y: auto;
    }

    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 20px;
      background: #fff;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06);
      position: sticky;
      top: 0;
      z-index: 10;

      h1 { margin: 0; font-size: 24px; font-weight: 700; color: #333; }
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
      border: 3px solid #f0f0f0;
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
      background: #e8e8e8;
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
      background: #fff;
      border-radius: 20px;
      padding: 20px;
      margin-bottom: 16px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.05);
    }

    .name-row {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 4px;

      h2 { margin: 0; font-size: 24px; font-weight: 700; color: #333; }
    }

    .badge {
      padding: 3px 10px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;

      &.active { background: #e8fdf1; color: #00a84f; }
    }

    .city { margin: 0 0 16px; color: #666; font-size: 15px; }

    .section {
      margin-bottom: 16px;

      h4 { margin: 0 0 8px; font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px; color: #aaa; }
      p { margin: 0; color: #555; font-size: 15px; line-height: 1.5; }
    }

    .pref-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 10px;
    }

    .pref-item {
      background: #f8f8f8;
      border-radius: 12px;
      padding: 10px;
      text-align: center;

      .pref-label { display: block; font-size: 11px; color: #aaa; margin-bottom: 4px; }
      .pref-value { display: block; font-size: 14px; font-weight: 600; color: #333; }
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
      border: 2px solid #e8e8e8;
      background: #fff;
      color: #555;
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
      h3 { margin: 0; font-size: 22px; color: #333; }
      p { margin: 0; color: #888; }
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
  private router = inject(Router);

  profile = signal<Profile | null>(null);
  loading = signal(true);

  ngOnInit(): void {
    const info = this.keycloak.getUserInfo();
    if (!info) return;

    this.profileService.getProfile(info.id).subscribe({
      next: (p) => { this.profile.set(p); this.loading.set(false); },
      error: () => { this.profile.set(null); this.loading.set(false); }
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
    this.profileService.uploadPhoto(file).subscribe({
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
