import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NgClass } from '@angular/common';
import { ProfileService } from '../../../core/services/profile.service';
import { Profile, Hobby } from '../../../core/models/profile.model';

const ALL_HOBBIES: Hobby[] = [
  'HIKING','CYCLING','RUNNING','GYM','YOGA','SWIMMING','FOOTBALL','BASKETBALL','TENNIS','VOLLEYBALL',
  'PHOTOGRAPHY','PAINTING','DRAWING','WRITING','MUSIC','SINGING','DANCING','COOKING','BAKING','CRAFTING',
  'GAMING','READING','MOVIES','TRAVELING','PODCASTS','VOLUNTEERING','PETS','GARDENING','MEDITATION','ASTROLOGY'
];

@Component({
  selector: 'app-profile-edit',
  imports: [ReactiveFormsModule, NgClass],
  template: `
    <div class="edit-page">
      <header class="header">
        <button class="back-btn" (click)="goBack()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
        </button>
        <h1>{{ isNew() ? 'Create Profile' : 'Edit Profile' }}</h1>
      </header>

      <form [formGroup]="form" (ngSubmit)="save()" class="form-content">
        <div class="form-group">
          <label>Name *</label>
          <input type="text" formControlName="name" placeholder="Your name" />
          @if (form.get('name')?.invalid && form.get('name')?.touched) {
            <span class="error">Name is required (2-50 chars)</span>
          }
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>Age *</label>
            <input type="number" formControlName="age" placeholder="18" min="18" max="130" />
            @if (form.get('age')?.invalid && form.get('age')?.touched) {
              @if (form.get('age')?.hasError('required')) {
                <span class="error">Age is required</span>
              } @else if (form.get('age')?.hasError('min')) {
                <span class="error">Must be at least 18</span>
              } @else if (form.get('age')?.hasError('max')) {
                <span class="error">Must be at most 130</span>
              }
            }
          </div>
          <div class="form-group">
            <label>Gender *</label>
            <select formControlName="gender">
              <option value="">Select</option>
              <option value="male">Male</option>
              <option value="female">Female</option>
              <option value="other">Other</option>
            </select>
            @if (form.get('gender')?.invalid && form.get('gender')?.touched) {
              <span class="error">Gender is required</span>
            }
          </div>
        </div>

        <div class="form-group">
          <label>City *</label>
          <div class="city-row">
            <input type="text" formControlName="city" placeholder="Your city" />
            <button type="button" class="location-btn"
              [class.granted]="locationStatus() === 'granted'"
              [class.denied]="locationStatus() === 'denied'"
              [disabled]="locationStatus() === 'requesting'"
              (click)="requestLocation()"
              [title]="locationBtnTitle()">
              @if (locationStatus() === 'requesting') {
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spin">
                  <circle cx="12" cy="12" r="10"/><path d="M12 2a10 10 0 0 1 10 10"/>
                </svg>
              } @else {
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="3"/><path d="M12 1v4M12 19v4M1 12h4M19 12h4"/>
                  <path d="M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83"/>
                </svg>
              }
            </button>
          </div>
          @if (form.get('city')?.invalid && form.get('city')?.touched) {
            <span class="error">City is required</span>
          }
          @if (locationStatus() === 'granted') {
            <span class="location-hint">GPS location captured</span>
          }
          @if (locationStatus() === 'denied') {
            <span class="error">Location access denied</span>
          }
        </div>

        <div class="form-group">
          <label>Bio</label>
          <textarea formControlName="bio" placeholder="Tell something about yourself..." rows="3"></textarea>
          <span class="char-count">{{ form.get('bio')?.value?.length ?? 0 }}/1023</span>
        </div>

        <div class="section-title">Preferences</div>

        <div class="form-group" formGroupName="preferences">
          <label>Interested in</label>
          <select formControlName="gender">
            <option value="all">Everyone</option>
            <option value="male">Men</option>
            <option value="female">Women</option>
            <option value="other">Other</option>
          </select>
        </div>

        <div class="form-row" formGroupName="preferences">
          <div class="form-group">
            <label>Min age</label>
            <input type="number" formControlName="minAge" min="18" max="130" />
          </div>
          <div class="form-group">
            <label>Max age</label>
            <input type="number" formControlName="maxAge" min="18" max="130" />
          </div>
          <div class="form-group">
            <label>Max distance (km)</label>
            <input type="number" formControlName="maxRange" min="1" max="500" />
          </div>
        </div>

        <div class="section-title">Hobbies (max 10)</div>
        <div class="hobbies-grid">
          @for (hobby of allHobbies; track hobby) {
            <div
              class="hobby-chip"
              [ngClass]="{ selected: isHobbySelected(hobby) }"
              (click)="toggleHobby(hobby)"
            >
              {{ formatHobby(hobby) }}
            </div>
          }
        </div>

        <div class="form-actions">
          <button type="submit" class="btn-primary" [disabled]="form.invalid || saving()">
            {{ saving() ? 'Saving...' : (isNew() ? 'Create Profile' : 'Save Changes') }}
          </button>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .edit-page {
      display: flex;
      flex-direction: column;
      height: 100vh;
      background: var(--bg);
      overflow-y: auto;
      padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 187px);
    }

    .header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 16px;
      background: var(--surface);
      box-shadow: 0 2px 8px var(--shadow-sm);
      position: sticky;
      top: 0;
      z-index: 10;

      h1 { margin: 0; font-size: 20px; font-weight: 700; color: var(--text-primary); }
    }

    .back-btn {
      background: none;
      border: none;
      cursor: pointer;
      padding: 4px;
      color: #fd5564;

      svg { width: 24px; height: 24px; display: block; }
    }

    .form-content {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
      flex: 1;

      label {
        font-size: 13px;
        font-weight: 600;
        color: var(--text-secondary);
        text-transform: uppercase;
        letter-spacing: 0.4px;
      }

      input, select, textarea {
        border: 1.5px solid var(--border);
        border-radius: 12px;
        padding: 12px 14px;
        font-size: 15px;
        outline: none;
        background: var(--surface);
        color: var(--text-primary);
        transition: border-color 0.2s;
        font-family: inherit;

        &:focus { border-color: #fd5564; }
      }

      textarea { resize: none; }
    }

    .city-row {
      display: flex;
      gap: 8px;
      align-items: stretch;

      input { flex: 1; }
    }

    .location-btn {
      flex-shrink: 0;
      width: 48px;
      border: 1.5px solid var(--border);
      border-radius: 12px;
      background: var(--surface);
      color: var(--text-secondary);
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;

      svg { width: 18px; height: 18px; }

      &:hover:not(:disabled) { border-color: #fd5564; color: #fd5564; }
      &.granted { border-color: #22c55e; color: #22c55e; background: #f0fdf4; }
      &.denied { border-color: #ef4444; color: #ef4444; }
      &:disabled { opacity: 0.5; cursor: not-allowed; }
    }

    @keyframes spin { to { transform: rotate(360deg); } }
    .spin { animation: spin 1s linear infinite; }

    .location-hint {
      font-size: 12px;
      color: #22c55e;
    }

    .form-row {
      display: flex;
      gap: 12px;
    }

    .char-count {
      font-size: 11px;
      color: var(--text-muted);
      text-align: right;
    }

    .error {
      font-size: 12px;
      color: #fd5564;
    }

    .section-title {
      font-size: 16px;
      font-weight: 700;
      color: var(--text-primary);
      border-bottom: 2px solid var(--border-light);
      padding-bottom: 8px;
    }

    .hobbies-grid {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .hobby-chip {
      padding: 7px 14px;
      border-radius: 20px;
      border: 1.5px solid var(--border);
      background: var(--surface);
      color: var(--text-secondary);
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s;

      &.selected {
        background: #fd5564;
        border-color: #fd5564;
        color: #fff;
      }
    }

    .form-actions {
      padding-top: 8px;
    }

    .btn-primary {
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

      &:disabled { opacity: 0.5; cursor: not-allowed; }
    }
  `]
})
export class ProfileEditComponent implements OnInit {
  private fb = inject(FormBuilder);
  private profileService = inject(ProfileService);
  private router = inject(Router);

  allHobbies = ALL_HOBBIES;
  isNew = signal(true);
  saving = signal(false);
  selectedHobbies = signal<Set<Hobby>>(new Set());
  locationStatus = signal<'idle' | 'requesting' | 'granted' | 'denied'>('idle');
  userCoords = signal<{ latitude: number; longitude: number } | null>(null);

  locationBtnTitle(): string {
    switch (this.locationStatus()) {
      case 'granted': return 'GPS location captured';
      case 'denied': return 'Location access denied';
      case 'requesting': return 'Requesting location...';
      default: return 'Use my location';
    }
  }

  requestLocation(): void {
    if (!navigator.geolocation) {
      this.locationStatus.set('denied');
      return;
    }
    this.locationStatus.set('requesting');
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        this.userCoords.set({ latitude: pos.coords.latitude, longitude: pos.coords.longitude });
        this.locationStatus.set('granted');
      },
      () => {
        this.locationStatus.set('denied');
      },
      { timeout: 10000 }
    );
  }

  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(50)]],
      age: [null, [Validators.required, Validators.min(18), Validators.max(130)]],
      gender: ['', Validators.required],
      bio: ['', Validators.maxLength(1023)],
      city: ['', [Validators.required, Validators.maxLength(100)]],
      preferences: this.fb.group({
        minAge: [18, [Validators.required, Validators.min(18), Validators.max(130)]],
        maxAge: [50, [Validators.required, Validators.min(18), Validators.max(130)]],
        gender: ['all', Validators.required],
        maxRange: [100, [Validators.required, Validators.min(1), Validators.max(500)]],
      })
    });

    this.profileService.getMe().subscribe({
      next: (profile: Profile) => {
        this.isNew.set(false);
        this.form.patchValue({
          name: profile.name,
          age: profile.age,
          gender: profile.gender,
          bio: profile.bio ?? '',
          city: profile.city,
          preferences: {
            minAge: profile.preferences?.minAge ?? 18,
            maxAge: profile.preferences?.maxAge ?? 50,
            gender: profile.preferences?.gender ?? 'all',
            maxRange: profile.preferences?.maxRange ?? 100,
          }
        });
        if (profile.hobbies) {
          this.selectedHobbies.set(new Set(profile.hobbies));
        }
      },
      error: () => {
        this.isNew.set(true);
      }
    });
  }

  isHobbySelected(hobby: Hobby): boolean {
    return this.selectedHobbies().has(hobby);
  }

  toggleHobby(hobby: Hobby): void {
    const current = new Set(this.selectedHobbies());
    if (current.has(hobby)) {
      current.delete(hobby);
    } else if (current.size < 10) {
      current.add(hobby);
    }
    this.selectedHobbies.set(current);
  }

  save(): void {
    if (this.form.invalid) return;
    this.saving.set(true);

    const coords = this.userCoords();
    const value = {
      ...this.form.value,
      hobbies: Array.from(this.selectedHobbies()),
      ...(coords ? { latitude: coords.latitude, longitude: coords.longitude } : {}),
    };

    const request$ = this.isNew()
      ? this.profileService.createProfile(value)
      : this.profileService.updateProfile(value);

    request$.subscribe({
      next: () => {
        this.saving.set(false);
        this.router.navigate(['/profile']);
      },
      error: () => {
        this.saving.set(false);
        alert('Failed to save profile');
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/profile']);
  }

  formatHobby(hobby: string): string {
    return hobby.charAt(0) + hobby.slice(1).toLowerCase().replace(/_/g, ' ');
  }
}
