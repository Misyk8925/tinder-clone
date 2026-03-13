import {
  Component, Input, Output, EventEmitter,
  ElementRef, OnInit, OnDestroy, signal, computed
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
            <div class="photo-dots">
              @for (photo of profile.photos; track $index) {
                <span class="dot" [ngClass]="{ active: currentPhoto() === $index }" (click)="setPhoto($index)"></span>
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
      </div>

      <div class="like-badge">LIKE</div>
      <div class="nope-badge">NOPE</div>

      <div class="card-info">
        <div class="card-name-row">
          <h2>{{ profile.name }}, {{ profile.age }}</h2>
          <span class="city">📍 {{ profile.city }}</span>
        </div>
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
      border-radius: 16px;
      overflow: hidden;
      background: #fff;
      box-shadow: 0 8px 32px rgba(0,0,0,0.12);
      cursor: grab;
      user-select: none;
      touch-action: none;
      will-change: transform;

      &:active { cursor: grabbing; }
    }

    .card-photo {
      position: relative;
      width: 100%;
      height: 100%;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .no-photo {
      width: 100%;
      height: 100%;
      background: linear-gradient(135deg, #fd5564, #ff8a00);
      display: flex;
      align-items: center;
      justify-content: center;

      span {
        font-size: 120px;
        color: rgba(255,255,255,0.5);
        font-weight: 700;
        text-transform: uppercase;
      }
    }

    .photo-dots {
      position: absolute;
      top: 10px;
      left: 0;
      right: 0;
      display: flex;
      gap: 4px;
      justify-content: center;
      z-index: 3;
    }

    .dot {
      width: 28px;
      height: 3px;
      border-radius: 2px;
      background: rgba(255,255,255,0.5);
      cursor: pointer;
      transition: background 0.2s;

      &.active { background: #fff; }
    }

    .photo-prev, .photo-next {
      position: absolute;
      top: 0;
      bottom: 0;
      width: 40%;
      z-index: 2;
      cursor: pointer;
    }

    .photo-prev { left: 0; }
    .photo-next { right: 0; }

    .like-badge, .nope-badge {
      position: absolute;
      top: 40px;
      padding: 6px 14px;
      border-radius: 6px;
      font-size: 28px;
      font-weight: 800;
      letter-spacing: 2px;
      opacity: 0;
      transition: opacity 0.1s;
      z-index: 10;
      border: 4px solid;
    }

    .like-badge {
      left: 24px;
      color: #00d26a;
      border-color: #00d26a;
      transform: rotate(-20deg);
    }

    .nope-badge {
      right: 24px;
      color: #fd5564;
      border-color: #fd5564;
      transform: rotate(20deg);
    }

    .card.liked .like-badge { opacity: 1; }
    .card.noped .nope-badge { opacity: 1; }

    .card-info {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      padding: 20px 20px 28px;
      background: linear-gradient(to top, rgba(0,0,0,0.75) 0%, transparent 100%);
      color: #fff;
    }

    .card-name-row {
      display: flex;
      align-items: baseline;
      gap: 10px;
      flex-wrap: wrap;

      h2 {
        margin: 0;
        font-size: 26px;
        font-weight: 700;
      }
    }

    .city {
      font-size: 14px;
      opacity: 0.9;
    }

    .bio {
      margin: 6px 0 10px;
      font-size: 14px;
      opacity: 0.85;
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
      background: rgba(255,255,255,0.25);
      border: 1px solid rgba(255,255,255,0.5);
      padding: 3px 10px;
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

  onDragStart(e: MouseEvent): void {
    this.isDragging = true;
    this.startX = e.clientX;
    this.startY = e.clientY;
  }

  onTouchStart(e: TouchEvent): void {
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
      const direction = dx > 0 ? 'right' : 'left';
      const flyX = direction === 'right' ? 1200 : -1200;
      const rotation = direction === 'right' ? 30 : -30;
      this.cardStyle.set({
        transform: `translate(${flyX}px, -100px) rotate(${rotation}deg)`,
        transition: 'transform 0.4s ease'
      });
      setTimeout(() => this.swiped.emit(direction), 380);
    } else {
      this.cardStyle.set({ transform: 'translate(0,0) rotate(0deg)', transition: 'transform 0.3s ease' });
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
    return hobby.charAt(0) + hobby.slice(1).toLowerCase();
  }
}
