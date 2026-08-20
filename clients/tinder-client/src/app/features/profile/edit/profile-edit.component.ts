import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NgClass } from '@angular/common';
import { ProfileService } from '../../../core/services/profile.service';
import { GeoLocationService } from '../../../core/services/geo-location.service';
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
        <button type="button" class="back-btn" (click)="goBack()" aria-label="Back to profile">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
        </button>
        <h1>{{ isNew() ? 'Create profile' : 'Edit profile' }}</h1>
      </header>

      <form [formGroup]="form" (ngSubmit)="save()" class="form-content">
        <section class="form-section">
          <div class="section-heading">
            <div>
              <h2>Basics</h2>
              <p>The details people see first.</p>
            </div>
          </div>

          <div class="form-group">
            <label for="profile-name">Name <span aria-hidden="true">*</span></label>
            <input id="profile-name" type="text" formControlName="name" placeholder="Your name" />
            @if (form.get('name')?.invalid && form.get('name')?.touched) {
              <span class="error">Name is required (2-50 chars)</span>
            }
          </div>

          <div class="form-row two-column">
            <div class="form-group">
              <label for="profile-age">Age <span aria-hidden="true">*</span></label>
              <input id="profile-age" type="number" formControlName="age" placeholder="18" min="18" max="130" />
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
              <label for="profile-gender">Gender <span aria-hidden="true">*</span></label>
              <select id="profile-gender" formControlName="gender">
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
            <label for="profile-city">City {{ hasGps() ? '(optional)' : '*' }}</label>
            <input id="profile-city" type="text" formControlName="city"
              [placeholder]="hasGps() ? 'Leave blank to use GPS location' : 'Your city'" />
            @if (form.get('city')?.invalid && form.get('city')?.touched) {
              <span class="error">City is required</span>
            }
            @if (hasGps()) {
              <span class="gps-hint">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                  <circle cx="12" cy="12" r="3"/><path d="M12 1v4M12 19v4M1 12h4M19 12h4"/>
                </svg>
                Using GPS location
              </span>
            }
          </div>

          <div class="form-group">
            <label for="profile-bio">Bio</label>
            <textarea id="profile-bio" formControlName="bio" placeholder="A few words about you" rows="3"></textarea>
            <span class="char-count">{{ form.get('bio')?.value?.length ?? 0 }}/1023</span>
          </div>
        </section>

        <section class="form-section" formGroupName="preferences">
          <div class="section-heading">
            <div>
              <h2>Discovery</h2>
              <p>Control who appears in your deck.</p>
            </div>
          </div>

          <div class="form-group">
            <label for="preference-gender">Interested in</label>
            <select id="preference-gender" formControlName="gender">
              <option value="all">Everyone</option>
              <option value="male">Men</option>
              <option value="female">Women</option>
              <option value="other">Other</option>
            </select>
          </div>

          <div class="form-row preferences-row">
            <div class="form-group">
              <label for="preference-min-age">Min age</label>
              <input id="preference-min-age" type="number" formControlName="minAge" min="18" max="130" />
            </div>
            <div class="form-group">
              <label for="preference-max-age">Max age</label>
              <input id="preference-max-age" type="number" formControlName="maxAge" min="18" max="130" />
            </div>
            <div class="form-group">
              <label for="preference-distance">Distance</label>
              <div class="input-suffix">
                <input id="preference-distance" type="number" formControlName="maxRange" min="1" max="500" />
                <span>km</span>
              </div>
            </div>
          </div>
        </section>

        <section class="form-section interests-section">
          <div class="section-heading">
            <div>
              <h2>Interests</h2>
              <p>Choose up to 10.</p>
            </div>
            <span class="selection-count">{{ selectedHobbies().size }}/10</span>
          </div>
          <div class="hobbies-grid">
            @for (hobby of allHobbies; track hobby) {
              <button
                type="button"
                class="hobby-chip"
                [ngClass]="{ selected: isHobbySelected(hobby) }"
                [attr.aria-pressed]="isHobbySelected(hobby)"
                (click)="toggleHobby(hobby)"
              >
                {{ formatHobby(hobby) }}
              </button>
            }
          </div>
        </section>

        <div class="form-actions">
          @if (saveError()) {
            <div class="save-error">{{ saveError() }}</div>
          }
          <button type="submit" class="btn-primary" [disabled]="form.invalid || saving()">
            {{ saving() ? 'Saving…' : (isNew() ? 'Create profile' : 'Save changes') }}
          </button>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .edit-page {
      display: flex;
      flex-direction: column;
      height: 100dvh;
      background: transparent;
      overflow-y: auto;
      padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 64px);
    }

    @media (min-width: 768px) {
      .edit-page {
        padding-bottom: 40px;
      }

      .form-body {
        max-width: 600px;
        margin: 0 auto;
        width: 100%;
      }
    }

    .header {
      display: flex;
      align-items: center;
      gap: 8px;
      min-height: var(--mobile-topbar-height);
      padding: 0 12px;
      background: var(--header-surface);
      position: sticky;
      top: 0;
      z-index: 10;
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);

      h1 { margin: 0; font-size: 20px; font-weight: 700; color: var(--text-primary); }
    }

    .back-btn {
      background: none;
      border: none;
      cursor: pointer;
      width: 40px;
      height: 40px;
      padding: 8px;
      color: var(--text-primary);
      border-radius: 50%;

      svg { width: 24px; height: 24px; display: block; }

      &:hover { background: var(--surface-2); }
      &:focus-visible { outline: 2px solid var(--brand); outline-offset: 1px; }
    }

    .form-content {
      padding: 16px 16px 28px;
      display: flex;
      flex-direction: column;
      gap: 18px;
      max-width: 600px;
      width: 100%;
      margin: 0 auto;
    }

    .form-section {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 18px;
      border: 1px solid var(--card-border);
      border-radius: 18px;
      background: var(--card-surface);
      box-shadow: 0 8px 28px var(--shadow-sm);
    }

    .section-heading {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      padding-bottom: 14px;
      border-bottom: 1px solid var(--border-light);

      h2 {
        margin: 0;
        color: var(--text-primary);
        font-size: 16px;
        line-height: 1.2;
        font-weight: 700;
        letter-spacing: -0.02em;
      }

      p {
        margin: 4px 0 0;
        color: var(--text-muted);
        font-size: 12px;
        line-height: 1.4;
      }
    }

    .selection-count {
      flex: 0 0 auto;
      color: var(--text-muted);
      font-size: 12px;
      font-weight: 600;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
      flex: 1;

      label {
        font-size: 12px;
        font-weight: 600;
        color: var(--text-muted);
        letter-spacing: 0.01em;

        span { color: var(--brand-strong); }
      }

      input, select, textarea {
        min-width: 0;
        width: 100%;
        border: 0;
        border-bottom: 1px solid var(--border);
        border-radius: 0;
        padding: 8px 0 10px;
        font-size: 15px;
        outline: none;
        background: transparent;
        color: var(--text-primary);
        transition: border-color 0.2s;
        font-family: inherit;

        &:focus {
          border-color: var(--brand);
          box-shadow: none;
        }
      }

      textarea {
        resize: vertical;
        min-height: 84px;
        line-height: 1.5;
      }
    }

    .gps-hint {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: #22c55e;

      svg { width: 12px; height: 12px; }
    }

    .form-row {
      display: flex;
      gap: 18px;
    }

    .two-column > .form-group { min-width: 0; }

    .preferences-row {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 14px;
    }

    .input-suffix {
      display: flex;
      align-items: center;
      border-bottom: 1px solid var(--border);

      input {
        border: 0;
        padding-right: 4px;
      }

      span {
        color: var(--text-muted);
        font-size: 12px;
      }

      &:focus-within { border-color: var(--brand); }
    }

    .char-count {
      font-size: 11px;
      color: var(--text-muted);
      text-align: right;
      margin-top: -2px;
    }

    .error {
      font-size: 12px;
      color: var(--brand);
    }

    .hobbies-grid {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .hobby-chip {
      appearance: none;
      padding: 6px 11px;
      border-radius: 20px;
      border: 1px solid var(--border);
      background: transparent;
      color: var(--text-secondary);
      font: inherit;
      font-size: 12px;
      font-weight: var(--ui-label-weight);
      cursor: pointer;
      transition: color 0.15s, border-color 0.15s, background 0.15s;

      &.selected {
        background: var(--brand);
        border-color: var(--brand);
        color: #182000;
        font-weight: 700;
      }

      &:focus-visible { outline: 1px solid var(--text-secondary); outline-offset: 2px; }
    }

    .form-actions {
      position: sticky;
      bottom: 0;
      z-index: 5;
      margin: 0 -4px;
      padding: 12px 4px 4px;
      background: linear-gradient(to bottom, transparent, var(--bg) 34%);
    }

    .save-error {
      background: rgba(156, 206, 43, 0.12);
      color: var(--brand);
      border: 1px solid rgba(109, 144, 55, 0.24);
      border-radius: 12px;
      padding: 12px 14px;
      font-size: 14px;
      font-weight: 500;
      margin-bottom: 12px;
      text-align: center;
    }

    .btn-primary {
      width: 100%;
      min-height: 48px;
      padding: 12px 18px;
      border-radius: 14px;
      border: none;
      background: var(--brand-gradient);
      color: #fff;
      font-size: 16px;
      font-weight: 700;
      cursor: pointer;
      box-shadow: 0 10px 24px rgba(109, 144, 55, 0.24);

      &:disabled { opacity: 0.5; cursor: not-allowed; }
    }

    @media (max-width: 380px) {
      .form-section { padding: 16px; }
      .form-row { gap: 12px; }
      .preferences-row { gap: 10px; }
      .preferences-row label { font-size: 10px; }
    }
  `]
})
export class ProfileEditComponent implements OnInit {
  private fb = inject(FormBuilder);
  private profileService = inject(ProfileService);
  private router = inject(Router);
  private geo = inject(GeoLocationService);

  allHobbies = ALL_HOBBIES;
  isNew = signal(true);
  saving = signal(false);
  saveError = signal<string | null>(null);
  selectedHobbies = signal<Set<Hobby>>(new Set());

  hasGps(): boolean {
    return this.geo.hasCoords();
  }

  form!: FormGroup;

  ngOnInit(): void {
    const cityValidators = this.hasGps()
      ? [Validators.maxLength(100)]
      : [Validators.required, Validators.maxLength(100)];

    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(50)]],
      age: [null, [Validators.required, Validators.min(18), Validators.max(130)]],
      gender: ['', Validators.required],
      bio: ['', Validators.maxLength(1023)],
      city: ['', cityValidators],
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
    this.saveError.set(null);
    this.saving.set(true);

    const coords = this.geo.getCoords();
    const rawCity: string = this.form.value.city ?? '';
    const value = {
      ...this.form.value,
      city: rawCity.trim() || null,
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
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 429) {
          this.saveError.set('Too many requests. Please wait a moment before trying again.');
        } else {
          this.saveError.set('Failed to save profile. Please try again.');
        }
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
