import {
  Component, Input, Output, EventEmitter,
  ElementRef, OnInit, OnDestroy, signal
} from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { Profile } from '../../../core/models/profile.model';

@Component({
  selector: 'app-swipe-card',
  imports: [NgClass, NgStyle],
  template: `
    <div
      class="card"
      [ngClass]="{ 'liked': swipeDir() === 'right', 'noped': swipeDir() === 'left' }"
      [ngStyle]="cardStyle()"
      (mousedown)="onDragStart($event)"
      (touchstart)="onTouchStart($event)"
    >
      <div class="card-photo">
        @if (profile.photos?.length) {
          <img [src]="profile.photos[currentPhoto()].url" [alt]="profile.name" (error)="onImgError($event)" />
          @if (profile.photos.length > 1) {
            <div class="photo-segments">
              @for (photo of profile.photos; track $index) {
                <div class="segment" [ngClass]="{ active: currentPhoto() === $index }" (click)="setPhoto($index)"></div>
              }
            </div>
            <div class="photo-prev" (click)="prevPhoto()"></div>
            <div class="photo-next" (click)="nextPhoto()"></div>
          }
        } @else {
          <div class="no-photo">
            <span>{{ profile.name[0] }}</span>
          </div>
        }

        <div class="like-badge">
          <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.593c-5.63-5.539-11-10.297-11-14.402 0-3.791 3.068-5.191 5.281-5.191 1.312 0 4.151.501 5.719 4.457 1.59-3.968 4.464-4.447 5.726-4.447 2.54 0 5.274 1.621 5.274 5.181 0 4.069-5.136 8.625-11 14.402z"/></svg>
          LIKE
        </div>
        <div class="nope-badge">
          <svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
          NOPE
        </div>
        <div class="super-badge">
          <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>
          SUPER
        </div>
      </div>

      <div class="card-gradient"></div>

      <div class="card-info">
        <div class="card-name-row">
          <div class="name-age">
            <h2>{{ profile.name }}<span class="age">, {{ profile.age }}</span></h2>
          </div>
          <button class="info-btn" (click)="$event.stopPropagation()">
            <svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>
          </button>
        </div>
        @if (profile.city) {
          <div class="location-row">
            <svg viewBox="0 0 24 24" fill="currentColor" class="loc-icon"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/></svg>
            <span>{{ profile.city }}</span>
          </div>
        }
        @if (profile.bio) {
          <p class="bio">{{ profile.bio }}</p>
        }
        @if (profile.hobbies?.length) {
          <div class="hobbies">
            @for (hobby of profile.hobbies.slice(0, 4); track hobby) {
              <span class="hobby-tag">{{ hobbyLabel(hobby) }}</span>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .card {
      position: absolute;
      width: 100%;
      height: 100%;
      border-radius: 26px;
      overflow: hidden;
      background: var(--surface);
      border: 1px solid rgba(255,255,255,0.5);
      box-shadow: 0 20px 40px rgba(15,23,42,0.24), 0 2px 8px rgba(15,23,42,0.08);
      cursor: grab;
      user-select: none;
      touch-action: none;
      will-change: transform;

      &:active { cursor: grabbing; }

      &::after {
        content: '';
        position: absolute;
        inset: 0;
        border-radius: inherit;
        border: 1px solid rgba(255,255,255,0.25);
        pointer-events: none;
      }
    }

    [data-theme="dark"] .card {
      border-color: rgba(255,255,255,0.12);
      box-shadow: 0 20px 46px rgba(0,0,0,0.6);
    }

    .card-photo {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        pointer-events: none;
      }
    }

    .no-photo {
      width: 100%;
      height: 100%;
      background: var(--brand-gradient);
      display: flex;
      align-items: center;
      justify-content: center;

      span {
        font-size: 100px;
        color: rgba(255,255,255,0.4);
        font-weight: 800;
        text-transform: uppercase;
      }
    }

    .photo-segments {
      position: absolute;
      top: 12px;
      left: 12px;
      right: 12px;
      display: flex;
      gap: 4px;
      z-index: 3;
      pointer-events: none;
    }

    .segment {
      flex: 1;
      height: 3px;
      border-radius: 2px;
      background: rgba(255,255,255,0.35);
      cursor: pointer;
      pointer-events: all;
      transition: background 0.2s;

      &.active {
        background: rgba(255,255,255,0.98);
      }
    }

    .photo-prev, .photo-next {
      position: absolute;
      top: 0;
      bottom: 0;
      width: 38%;
      z-index: 2;
      cursor: pointer;
    }

    .photo-prev { left: 0; }
    .photo-next { right: 0; }

    /* LIKE / NOPE / SUPER badges */
    .like-badge, .nope-badge, .super-badge {
      position: absolute;
      top: 34px;
      padding: 6px 14px;
      border-radius: 10px;
      font-size: 20px;
      font-weight: 800;
      letter-spacing: 2px;
      opacity: 0;
      transition: opacity 0.1s;
      z-index: 10;
      border: 3px solid;
      display: flex;
      align-items: center;
      gap: 6px;
      background: rgba(0,0,0,0.25);
      backdrop-filter: blur(6px);

      svg {
        width: 20px;
        height: 20px;
      }
    }

    .like-badge {
      left: 20px;
      color: var(--like);
      border-color: var(--like);
      transform: rotate(-20deg);
    }

    .nope-badge {
      right: 20px;
      color: var(--nope);
      border-color: var(--nope);
      transform: rotate(20deg);
    }

    .super-badge {
      left: 50%;
      transform: translateX(-50%);
      color: var(--super);
      border-color: var(--super);
    }

    .card.liked .like-badge { opacity: 1; }
    .card.noped .nope-badge { opacity: 1; }
    .card.super .super-badge { opacity: 1; }

    /* Bottom gradient overlay */
    .card-gradient {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 68%;
      background: linear-gradient(to top,
        rgba(0,0,0,0.9) 0%,
        rgba(0,0,0,0.6) 35%,
        rgba(0,0,0,0.2) 65%,
        transparent 100%);
      pointer-events: none;
      z-index: 1;
    }

    .card-info {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      padding: 16px 18px 22px;
      color: #fff;
      z-index: 2;
    }

    .card-name-row {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 4px;
    }

    .name-age h2 {
      margin: 0;
      font-size: 28px;
      font-weight: 700;
      line-height: 1.1;
      text-shadow: 0 1px 10px rgba(0,0,0,0.35);

      .age {
        font-size: 26px;
        font-weight: 500;
      }
    }

    .info-btn {
      background: rgba(255,255,255,0.12);
      border: 1.5px solid rgba(255,255,255,0.7);
      border-radius: 50%;
      width: 36px;
      height: 36px;
      min-width: 36px;
      color: #fff;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0;
      flex-shrink: 0;
      transition: background 0.15s, transform 0.15s;

      svg { width: 20px; height: 20px; }
      &:active { background: rgba(255,255,255,0.22); transform: scale(0.96); }
    }

    .location-row {
      display: flex;
      align-items: center;
      gap: 4px;
      margin-bottom: 6px;
      font-size: 14px;
      opacity: 0.9;

      .loc-icon { width: 14px; height: 14px; flex-shrink: 0; }
    }

    .bio {
      margin: 0 0 8px;
      font-size: 14px;
      opacity: 0.9;
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .hobbies {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }

    .hobby-tag {
      background: rgba(255,255,255,0.2);
      border: 1px solid rgba(255,255,255,0.45);
      backdrop-filter: blur(6px);
      padding: 4px 12px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 500;
    }
  `]
})
export class SwipeCardComponent implements OnInit, OnDestroy {
  @Input({ required: true }) profile!: Profile;
  @Output() swiped = new EventEmitter<'left' | 'right'>();

  currentPhoto = signal(0);
  swipeDir = signal<'left' | 'right' | null>(null);
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

  constructor(private el: ElementRef) {}

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

  /** Called by parent to animate programmatic swipe (button click) */
  public triggerSwipe(direction: 'left' | 'right' | 'up'): void {
    if (this.isAnimating || this.isDragging) return;
    this.isAnimating = true;

    const emitDir: 'left' | 'right' = direction === 'left' ? 'left' : 'right';

    if (direction === 'up') {
      this.cardStyle.set({
        transform: 'translate(0, -1400px) scale(0.8)',
        transition: 'transform 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94)'
      });
    } else {
      const flyX = direction === 'right' ? 1300 : -1300;
      const rotation = direction === 'right' ? 30 : -30;
      this.swipeDir.set(direction);
      this.cardStyle.set({
        transform: `translate(${flyX}px, -150px) rotate(${rotation}deg)`,
        transition: 'transform 0.38s cubic-bezier(0.25, 0.46, 0.45, 0.94)'
      });
    }

    setTimeout(() => this.swiped.emit(emitDir), 360);
  }

  onDragStart(e: MouseEvent): void {
    if (this.isAnimating) return;
    this.isDragging = true;
    this.startX = e.clientX;
    this.startY = e.clientY;
  }

  onTouchStart(e: TouchEvent): void {
    if (this.isAnimating) return;
    this.isDragging = true;
    this.startX = e.touches[0].clientX;
    this.startY = e.touches[0].clientY;
  }

  onDragMove(e: MouseEvent): void {
    if (!this.isDragging) return;
    this.updateCardPosition(e.clientX - this.startX, e.clientY - this.startY);
  }

  onTouchMove(e: TouchEvent): void {
    if (!this.isDragging) return;
    e.preventDefault();
    this.updateCardPosition(e.touches[0].clientX - this.startX, e.touches[0].clientY - this.startY);
  }

  private updateCardPosition(dx: number, dy: number): void {
    const rotation = dx * 0.08;
    this.cardStyle.set({
      transform: `translate(${dx}px, ${dy}px) rotate(${rotation}deg)`,
      transition: 'none'
    });

    if (dx > 30) this.swipeDir.set('right');
    else if (dx < -30) this.swipeDir.set('left');
    else this.swipeDir.set(null);
  }

  onDragEnd(e: MouseEvent): void {
    if (!this.isDragging) return;
    this.isDragging = false;
    this.finishSwipe(e.clientX - this.startX);
  }

  onTouchEnd(e: TouchEvent): void {
    if (!this.isDragging) return;
    this.isDragging = false;
    const dx = e.changedTouches[0].clientX - this.startX;
    this.finishSwipe(dx);
  }

  private finishSwipe(dx: number): void {
    if (Math.abs(dx) > this.SWIPE_THRESHOLD) {
      this.isAnimating = true;
      const direction = dx > 0 ? 'right' : 'left';
      const flyX = direction === 'right' ? 1300 : -1300;
      const rotation = direction === 'right' ? 30 : -30;
      this.cardStyle.set({
        transform: `translate(${flyX}px, -150px) rotate(${rotation}deg)`,
        transition: 'transform 0.38s cubic-bezier(0.25, 0.46, 0.45, 0.94)'
      });
      setTimeout(() => this.swiped.emit(direction), 360);
    } else {
      this.cardStyle.set({ transform: 'translate(0,0) rotate(0deg)', transition: 'transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1)' });
      this.swipeDir.set(null);
    }
  }

  setPhoto(index: number): void {
    this.currentPhoto.set(index);
  }

  prevPhoto(): void {
    if (this.currentPhoto() > 0) this.currentPhoto.update(v => v - 1);
  }

  nextPhoto(): void {
    if (this.currentPhoto() < (this.profile.photos?.length ?? 0) - 1) {
      this.currentPhoto.update(v => v + 1);
    }
  }

  onImgError(e: Event): void {
    (e.target as HTMLImageElement).style.display = 'none';
  }

  hobbyLabel(hobby: string): string {
    return hobby.charAt(0) + hobby.slice(1).toLowerCase().replace(/_/g, ' ');
  }
}
