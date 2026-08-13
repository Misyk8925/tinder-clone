import {
  Component, EventEmitter, Input, OnDestroy, OnInit, Output, signal
} from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { DeckCard } from '../../../core/models/deck.model';

@Component({
  selector: 'app-swipe-card',
  imports: [NgClass, NgStyle, LucideAngularModule],
  template: `
    <article
      class="profile-card"
      [ngClass]="{ liked: swipeDir() === 'right', passed: swipeDir() === 'left', expanded: expanded() }"
      [ngStyle]="cardStyle()"
      (mousedown)="onDragStart($event)"
      (touchstart)="onTouchStart($event)"
    >
      <div class="photo-stage">
        @if (profile.photos.length) {
          <img
            [src]="profile.photos[currentPhoto()].url"
            [alt]="profile.name + ' profile photo ' + (currentPhoto() + 1)"
            (error)="onImgError($event)"
          />
        } @else {
          <div class="photo-placeholder" aria-hidden="true">{{ profile.name[0] }}</div>
        }

        @if (profile.photos.length > 1) {
          <div class="photo-progress" aria-label="Profile photos">
            @for (photo of profile.photos; track photo.photoId; let index = $index) {
              <button
                type="button"
                [class.active]="currentPhoto() === index"
                (click)="$event.stopPropagation(); setPhoto(index)"
                [attr.aria-label]="'Show photo ' + (index + 1)"
              ></button>
            }
          </div>
          <button type="button" class="photo-zone previous" (click)="$event.stopPropagation(); prevPhoto()" aria-label="Previous photo"></button>
          <button type="button" class="photo-zone next" (click)="$event.stopPropagation(); nextPhoto()" aria-label="Next photo"></button>
        }

        <button
          type="button"
          class="expand-control"
          (click)="$event.stopPropagation(); toggleExpanded()"
          [attr.aria-expanded]="expanded()"
          aria-label="Show more profile details"
        >
          <lucide-icon [name]="expanded() ? 'chevron-up' : 'chevron-down'" [size]="26" strokeWidth="2" />
        </button>

        <div class="decision-stamp like-stamp"><lucide-icon name="heart" [size]="22" fill="currentColor" /> Like</div>
        <div class="decision-stamp pass-stamp"><lucide-icon name="x" [size]="24" /> Pass</div>
      </div>

      <div class="profile-story">
        <div class="identity-row">
          <div>
            <div class="name-line">
              <h2>{{ profile.name }}, {{ profile.age }}</h2>
              @if (profile.isActive) {
                <span class="active-dot" title="Active profile" aria-label="Active profile"></span>
              }
            </div>
            @if (profile.city) {
              <p class="location"><lucide-icon name="map-pin" [size]="16" strokeWidth="1.8" /> {{ profile.city }}</p>
            }
          </div>
        </div>

        <div class="story-divider"></div>
        <p class="prompt-label">A perfect Saturday…</p>
        <p class="prompt-answer">{{ profile.bio || 'Coffee, a trail, then live music.' }}</p>

        @if (profile.hobbies.length) {
          <div class="hobbies" aria-label="Interests">
            @for (hobby of profile.hobbies.slice(0, expanded() ? 6 : 3); track hobby) {
              <span>{{ hobbyLabel(hobby) }}</span>
            }
          </div>
        }

        @if (expanded()) {
          <div class="more-details">
            <div><span>Age range</span><strong>{{ profile.preferences.minAge }}–{{ profile.preferences.maxAge }}</strong></div>
            <div><span>Distance</span><strong>Within {{ profile.preferences.maxDistanceKm }} km</strong></div>
          </div>
        }
      </div>
    </article>
  `,
  styles: [`
    :host { display: block; position: absolute; inset: 0; }

    .profile-card {
      position: absolute;
      inset: 0;
      width: 100%;
      max-width: 100%;
      display: grid;
      grid-template-rows: minmax(0, 1fr) auto;
      overflow: hidden;
      isolation: isolate;
      border-radius: 20px;
      background: var(--card-surface);
      border: 1px solid var(--card-border);
      box-shadow: 0 12px 34px var(--shadow-md);
      cursor: grab;
      user-select: none;
      touch-action: none;
      will-change: transform;
    }

    .profile-card:active { cursor: grabbing; }

    .photo-stage {
      position: relative;
      min-height: 0;
      overflow: hidden;
      background: var(--surface-3);
    }

    .photo-stage > img {
      width: 100%;
      height: 100%;
      display: block;
      object-fit: cover;
      object-position: center 40%;
      pointer-events: none;
    }

    .photo-placeholder {
      width: 100%;
      height: 100%;
      display: grid;
      place-items: center;
      color: var(--text-secondary);
      background: var(--surface-3);
      font-size: 86px;
      font-weight: 700;
    }

    .photo-progress {
      position: absolute;
      z-index: 4;
      top: 12px;
      left: 14px;
      right: 14px;
      display: flex;
      gap: 5px;
    }

    .photo-progress button {
      min-width: 0;
      flex: 1;
      height: 3px;
      padding: 0;
      border: 0;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.56);
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      cursor: pointer;
    }

    .photo-progress button.active { background: var(--brand); }

    .photo-zone {
      position: absolute;
      z-index: 2;
      top: 30px;
      bottom: 0;
      width: 34%;
      border: 0;
      background: transparent;
      cursor: pointer;
    }

    .photo-zone.previous { left: 0; }
    .photo-zone.next { right: 0; }

    .expand-control {
      position: absolute;
      z-index: 5;
      right: 12px;
      bottom: 12px;
      width: 38px;
      height: 38px;
      display: grid;
      place-items: center;
      border: 1px solid var(--card-border);
      border-radius: 50%;
      color: var(--text-primary);
      background: var(--surface-glass);
      box-shadow: 0 4px 14px var(--shadow-sm);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      cursor: pointer;
      transition: transform 180ms ease;
    }

    .expand-control:hover { transform: translateY(-2px); }

    .decision-stamp {
      position: absolute;
      z-index: 6;
      top: 38px;
      display: flex;
      align-items: center;
      gap: 7px;
      padding: 8px 14px;
      border: 2px solid currentColor;
      border-radius: 12px;
      background: var(--card-surface);
      font-size: 16px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      opacity: 0;
    }

    .like-stamp { left: 18px; color: var(--brand-strong); transform: rotate(-8deg); }
    .pass-stamp { right: 18px; color: var(--text-secondary); transform: rotate(8deg); }
    .profile-card.liked .like-stamp,
    .profile-card.passed .pass-stamp { opacity: 1; }

    .profile-story {
      position: relative;
      z-index: 3;
      width: 100%;
      min-height: 0;
      margin-top: 0;
      padding: 16px clamp(16px, 5vw, 22px);
      border-radius: 0;
      border-top: 1px solid var(--card-border);
      background: var(--card-surface);
      box-shadow: none;
      overflow: hidden;
      transform: none;
    }

    .identity-row {
      min-width: 0;
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
    }

    .identity-row > div { min-width: 0; max-width: 100%; }

    .name-line {
      min-width: 0;
      max-width: 100%;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    h2 {
      min-width: 0;
      margin: 0;
      color: var(--text-primary);
      font-size: clamp(22px, 5.8vw, 25px);
      line-height: 1.1;
      letter-spacing: -0.035em;
      font-weight: 700;
      overflow-wrap: anywhere;
    }

    .active-dot {
      width: 12px;
      height: 12px;
      flex: 0 0 auto;
      border-radius: 50%;
      background: var(--brand);
      box-shadow: 0 0 0 4px var(--brand-soft);
    }

    .location {
      min-width: 0;
      display: flex;
      align-items: center;
      gap: 5px;
      margin: 5px 0 0;
      color: var(--text-muted);
      font-size: 14px;
      font-weight: var(--ui-label-weight);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .story-divider { height: 1px; margin: 13px 0 11px; background: var(--border-light); }

    .prompt-label {
      margin: 0 0 4px;
      color: var(--text-secondary);
      font-size: 11px;
      font-weight: 500;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .prompt-answer {
      margin: 0;
      color: var(--text-primary);
      font-family: inherit;
      font-size: clamp(18px, 4.9vw, 22px);
      line-height: 1.3;
      letter-spacing: -0.02em;
      font-weight: 500;
    }

    .hobbies {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-top: 13px;
    }

    .hobbies span {
      display: inline-flex;
      align-items: center;
      padding: 5px 10px;
      border-radius: 999px;
      border: 1px solid var(--border);
      color: var(--text-secondary);
      background: transparent;
      font-size: 12px;
      font-weight: 600;
    }

    .more-details {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 10px;
      margin-top: 14px;
      padding-top: 12px;
      border-top: 1px solid var(--border);
    }

    .more-details div { display: flex; flex-direction: column; gap: 3px; }
    .more-details span { color: var(--text-muted); font-size: 11px; }
    .more-details strong { color: var(--text-primary); font-size: 13px; }

    .profile-card.expanded { grid-template-rows: minmax(0, 46%) minmax(0, 54%); }
    .profile-card.expanded .profile-story { overflow-y: auto; }

    @media (min-width: 768px) {
      .profile-card { grid-template-rows: minmax(0, 67%) minmax(0, 33%); }
      .profile-story { padding: 18px 22px; }
    }

    @media (max-height: 740px) {
      .profile-card { grid-template-rows: minmax(0, 1fr) auto; }
      .profile-story { padding: 14px 18px 16px; }
      .story-divider { margin: 10px 0 9px; }
      .hobbies { margin-top: 10px; }
      .hobbies span { padding: 5px 9px; }
    }
  `]
})
export class SwipeCardComponent implements OnInit, OnDestroy {
  @Input({ required: true }) profile!: DeckCard;
  @Output() swiped = new EventEmitter<'left' | 'right'>();

  currentPhoto = signal(0);
  swipeDir = signal<'left' | 'right' | null>(null);
  expanded = signal(false);
  cardStyle = signal<Record<string, string>>({});

  private startX = 0;
  private startY = 0;
  private isDragging = false;
  private isAnimating = false;
  private readonly SWIPE_THRESHOLD = 100;

  private mouseMoveHandler = this.onDragMove.bind(this);
  private mouseUpHandler = this.onDragEnd.bind(this);
  private touchMoveHandler = this.onTouchMove.bind(this);
  private touchEndHandler = this.onTouchEnd.bind(this);

  ngOnInit(): void {
    document.addEventListener('mousemove', this.mouseMoveHandler);
    document.addEventListener('mouseup', this.mouseUpHandler);
    document.addEventListener('touchmove', this.touchMoveHandler, { passive: false });
    document.addEventListener('touchend', this.touchEndHandler);
  }

  ngOnDestroy(): void {
    document.removeEventListener('mousemove', this.mouseMoveHandler);
    document.removeEventListener('mouseup', this.mouseUpHandler);
    document.removeEventListener('touchmove', this.touchMoveHandler);
    document.removeEventListener('touchend', this.touchEndHandler);
  }

  public triggerSwipe(direction: 'left' | 'right' | 'up'): void {
    if (this.isAnimating || this.isDragging) return;
    this.isAnimating = true;
    const emitDir: 'left' | 'right' = direction === 'left' ? 'left' : 'right';

    if (direction === 'up') {
      this.cardStyle.set({
        transform: 'translate(0, -1200px) scale(0.84)',
        transition: 'transform 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94)'
      });
    } else {
      const flyX = direction === 'right' ? 1200 : -1200;
      const rotation = direction === 'right' ? 24 : -24;
      this.swipeDir.set(direction);
      this.cardStyle.set({
        transform: `translate(${flyX}px, -120px) rotate(${rotation}deg)`,
        transition: 'transform 0.38s cubic-bezier(0.25, 0.46, 0.45, 0.94)'
      });
    }

    setTimeout(() => this.swiped.emit(emitDir), 360);
  }

  onDragStart(event: MouseEvent): void {
    if (this.isAnimating || this.isInteractiveTarget(event.target)) return;
    this.isDragging = true;
    this.startX = event.clientX;
    this.startY = event.clientY;
  }

  onTouchStart(event: TouchEvent): void {
    if (this.isAnimating || this.isInteractiveTarget(event.target)) return;
    this.isDragging = true;
    this.startX = event.touches[0].clientX;
    this.startY = event.touches[0].clientY;
  }

  onDragMove(event: MouseEvent): void {
    if (!this.isDragging) return;
    this.updateCardPosition(event.clientX - this.startX, event.clientY - this.startY);
  }

  onTouchMove(event: TouchEvent): void {
    if (!this.isDragging) return;
    event.preventDefault();
    this.updateCardPosition(event.touches[0].clientX - this.startX, event.touches[0].clientY - this.startY);
  }

  onDragEnd(event: MouseEvent): void {
    if (!this.isDragging) return;
    this.isDragging = false;
    this.finishSwipe(event.clientX - this.startX);
  }

  onTouchEnd(event: TouchEvent): void {
    if (!this.isDragging) return;
    this.isDragging = false;
    this.finishSwipe(event.changedTouches[0].clientX - this.startX);
  }

  private updateCardPosition(dx: number, dy: number): void {
    this.cardStyle.set({ transform: `translate(${dx}px, ${dy}px) rotate(${dx * 0.06}deg)`, transition: 'none' });
    if (dx > 30) this.swipeDir.set('right');
    else if (dx < -30) this.swipeDir.set('left');
    else this.swipeDir.set(null);
  }

  private finishSwipe(dx: number): void {
    if (Math.abs(dx) > this.SWIPE_THRESHOLD) {
      this.isAnimating = true;
      const direction = dx > 0 ? 'right' : 'left';
      const flyX = direction === 'right' ? 1200 : -1200;
      const rotation = direction === 'right' ? 24 : -24;
      this.cardStyle.set({
        transform: `translate(${flyX}px, -120px) rotate(${rotation}deg)`,
        transition: 'transform 0.38s cubic-bezier(0.25, 0.46, 0.45, 0.94)'
      });
      setTimeout(() => this.swiped.emit(direction), 360);
      return;
    }

    this.cardStyle.set({ transform: 'translate(0,0) rotate(0deg)', transition: 'transform 0.28s ease' });
    this.swipeDir.set(null);
  }

  private isInteractiveTarget(target: EventTarget | null): boolean {
    return target instanceof Element && Boolean(target.closest('button'));
  }

  toggleExpanded(): void { this.expanded.update(value => !value); }
  setPhoto(index: number): void { this.currentPhoto.set(index); }
  prevPhoto(): void { if (this.currentPhoto() > 0) this.currentPhoto.update(value => value - 1); }
  nextPhoto(): void {
    if (this.currentPhoto() < (this.profile.photos?.length ?? 0) - 1) this.currentPhoto.update(value => value + 1);
  }

  onImgError(event: Event): void { (event.target as HTMLImageElement).src = '/assets/profiles/mila-discover.png'; }

  hobbyLabel(hobby: string): string {
    return hobby.charAt(0) + hobby.slice(1).toLowerCase().replace(/_/g, ' ');
  }

  hobbyIcon(hobby: string): string {
    const icons: Record<string, string> = {
      HIKING: 'mountain',
      CYCLING: 'bike',
      MUSIC: 'music-2',
      COOKING: 'coffee',
      READING: 'book-open',
      GYM: 'dumbbell',
    };
    return icons[hobby] ?? 'sparkles';
  }
}
