import {
  Component, inject, OnInit, OnDestroy, signal,
  ViewChild, ViewChildren, QueryList, ElementRef, AfterViewChecked, HostListener
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { NgClass } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { MatchService, Message } from '../../core/services/match.service';
import { ReportService } from '../../core/services/report.service';
import { moderationFailureMessage } from '../../core/utils/moderation-errors';
import { previewTextBlocked } from '../../core/preview/preview-moderation';
import { ChatHistoryCache } from '../../core/services/chat-history.cache';
import { markConversationRead } from '../matches/matches.component';
import { KeycloakService } from '../../core/services/keycloak.service';
import { ProfileService } from '../../core/services/profile.service';
import { Profile } from '../../core/models/profile.model';
import { MinimalStompClient } from '../../core/stomp-client';
import { environment } from '../../../environments/environment';
import { mergeServerChatMessages } from '../../core/utils/chat-history-merge';
import {
  PHOTO_UPLOAD_MAX_BYTES,
  isHeicPhoto,
  photoUploadFailureMessage,
  preparePhotoForUpload
} from '../../core/utils/photo-upload';

interface StompMessageEvent {
  occurredAt?: string;
  messageId?: string;
  clientMessageId?: string;
  senderId?: string;
  type?: string;
  text?: string | null;
  attachments?: { url?: string; mimeType?: string }[];
}

@Component({
  selector: 'app-chat',
  imports: [FormsModule, NgClass],
  template: `
    <div class="chat-page">
      <header class="chat-header">
        <button type="button" class="back-btn" (click)="goBack()" aria-label="Back to messages">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
        </button>
        <div class="header-info">
          <div class="avatar">
            @if (otherProfile()?.photos?.length) {
              <img [src]="otherProfile()!.photos[0].url" [alt]="otherProfile()!.name" />
            } @else {
              <span>{{ otherInitial() }}</span>
            }
            @if (otherProfile()?.isActive) { <span class="presence-dot" aria-label="Active now"></span> }
          </div>
          <div class="header-copy">
            <span class="eyebrow">Conversation</span>
            <h1>{{ otherName() }}</h1>
          </div>
        </div>
        <button type="button" class="report-btn" (click)="reportConversation()" aria-label="Report conversation">
          Report
        </button>
        <div class="connection-state" [class.connecting]="wsState() === 'connecting'" [class.offline]="wsState() === 'disconnected'">
          <span></span>
          {{ connectionLabel() }}
        </div>
      </header>

      <div class="messages-area" #messagesArea>
        @if (loading()) {
          <div class="loading-msgs">
            <div class="spinner" role="status" aria-label="Loading conversation"></div>
          </div>
        } @else {
          <div class="conversation-start">
            <div class="start-avatar">
              @if (otherProfile()?.photos?.length) {
                <img [src]="otherProfile()!.photos[0].url" [alt]="otherProfile()!.name" />
              } @else {
                <span>{{ otherInitial() }}</span>
              }
            </div>
            <span class="eyebrow">You connected</span>
            <h2>You and {{ otherName() }} matched</h2>
            <p>Start with something you noticed. Real conversations usually begin small.</p>
          </div>

          @if (messages().length === 0) {
            <div class="no-msgs">
              <p>Write the first message</p>
              <span>Ask about an interest or share a simple plan.</span>
            </div>
          }
          @for (msg of messages(); track msg.id) {
            <div class="message" [ngClass]="{ 'mine': msg.senderId === myId() }">
              <div class="bubble">
                @if (msg.type === 'photo') {
                  <div class="photo-frame" [class.ready]="isPhotoReady(msg.id)">
                    @if (!isPhotoReady(msg.id)) {
                      <div class="photo-skeleton" role="status" aria-label="Loading photo"></div>
                    }
                    @if (msg.content) {
                      <img
                        #photoImg
                        [attr.data-photo-id]="msg.id"
                        [src]="msg.content"
                        class="msg-photo"
                        [class.ready]="isPhotoReady(msg.id)"
                        alt="Photo"
                        (load)="markPhotoReady(msg.id)"
                        (error)="markPhotoReady(msg.id)"
                        (click)="isPhotoReady(msg.id) && openPreview(msg.content)"
                      />
                    }
                  </div>
                } @else {
                  {{ msg.content }}
                }
              </div>
              <span class="msg-time">{{ formatTime(msg.sentAt) }}</span>
            </div>
          }
        }
      </div>

      <div class="input-area">
        <div class="composer-shell">
          <label class="photo-btn" title="Send photo" aria-label="Send a photo">
            <input type="file" accept="image/*" (change)="sendPhoto($event)" hidden />
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 7a2 2 0 0 1 2-2h3l1.4-2h3.2L15 5h3a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
          </label>
          <input
            class="msg-input"
            type="text"
            [(ngModel)]="messageText"
            placeholder="Write a message"
            aria-label="Message"
            (keydown.enter)="sendMessage()"
          />
          <button class="send-btn" aria-label="Send message" (click)="sendMessage()" [disabled]="!messageText.trim() || (!previewMode && wsState() !== 'connected')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="m5 12 14-7-4 14-3-5z"/><path d="m12 14 7-9"/></svg>
          </button>
        </div>
      </div>
    </div>

    @if (previewUrl()) {
      <div class="lightbox" (click)="closePreview()">
        <button class="lightbox-close" (click)="closePreview()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
        <img [src]="previewUrl()!" class="lightbox-img" alt="Photo preview" (click)="$event.stopPropagation()" />
      </div>
    }
  `,
  styles: [`
    .chat-page {
      display: flex;
      flex-direction: column;
      height: 100dvh;
      background: transparent;
    }

    @media (min-width: 768px) {
      .chat-page {
        max-width: 800px;
        margin: 0 auto;
        border-left: 1px solid var(--border);
        border-right: 1px solid var(--border);
        height: 100dvh;
      }
    }

    .chat-header {
      display: flex;
      align-items: center;
      gap: 8px;
      min-height: var(--mobile-topbar-height);
      padding: 0 10px;
      background: var(--header-surface);
      z-index: 10;
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
    }

    .back-btn {
      background: none;
      border: none;
      cursor: pointer;
      width: 36px;
      height: 36px;
      padding: 7px;
      color: var(--brand);

      svg { width: 22px; height: 22px; display: block; }
    }

    .header-info {
      display: flex;
      align-items: center;
      gap: 8px;

      .avatar {
        width: 32px; height: 32px;
        border-radius: 50%;
        background: var(--brand-gradient);
        color: #fff;
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 700;
        font-size: 14px;
      }

      h2 { margin: 0; font-size: 15px; color: var(--text-primary); }
    }

    .online {
      font-size: 12px;
      color: var(--like);
      font-weight: 500;

      &.connecting { color: var(--gold-2); }
    }

    .messages-area {
      flex: 1;
      overflow-y: auto;
      padding: 18px 16px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding-bottom: 8px;
    }

    .loading-msgs {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .spinner {
      width: 36px; height: 36px;
      border: 3px solid var(--border-light);
      border-top: 3px solid var(--brand);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .no-msgs {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      p { color: var(--text-muted); font-size: 16px; }
    }

    .message {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 2px;

      &.mine {
        align-items: flex-end;

        .bubble {
          background: var(--brand-gradient);
          color: #fff;
          border-radius: 18px 18px 4px 18px;

          &:has(.photo-frame) {
            background: transparent;
            border: none;
            box-shadow: none;
          }
        }
      }
    }

    .bubble {
      color: var(--text-primary);
      padding: 10px 14px;
      border-radius: 18px 18px 18px 4px;
      max-width: 70%;
      font-size: 15px;
      line-height: 1.4;
      box-shadow: 0 8px 20px var(--shadow-sm);
      border: 1px solid var(--border-light);
      word-break: break-word;

      &:has(.photo-frame) {
        padding: 4px;
        background: transparent;
        border: none;
        box-shadow: none;
      }
    }

    [data-theme="dark"] .bubble {
      background: rgba(28, 26, 36, 0.92);
      border-color: rgba(255,255,255,0.08);
    }

    .photo-frame {
      position: relative;
      width: 200px;
      min-height: 168px;
      border-radius: 14px;
      overflow: hidden;
      background: var(--surface-3);

      &.ready { min-height: 0; }
    }

    .message.mine .photo-frame {
      background: rgba(156, 206, 43, 0.22);
    }

    .photo-skeleton {
      position: absolute;
      inset: 0;
      background: linear-gradient(
        90deg,
        transparent 0%,
        rgba(255, 255, 255, 0.28) 45%,
        transparent 100%
      );
      background-size: 220% 100%;
      animation: photo-shimmer 1.15s ease-in-out infinite;
    }

    @keyframes photo-shimmer {
      from { background-position: 100% 0; }
      to { background-position: -100% 0; }
    }

    .msg-photo {
      width: 100%;
      max-width: 200px;
      border-radius: 12px;
      display: block;
      cursor: zoom-in;
      opacity: 0;
      transition: opacity 0.2s;

      &.ready { opacity: 1; }
      &:active { opacity: 0.85; }
    }

    .lightbox {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.92);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 2000;
      animation: fade-in 0.18s ease;
    }

    @keyframes fade-in {
      from { opacity: 0; }
      to   { opacity: 1; }
    }

    .lightbox-img {
      max-width: 92vw;
      max-height: 88vh;
      border-radius: 12px;
      object-fit: contain;
      box-shadow: 0 8px 40px rgba(0,0,0,0.6);
      animation: zoom-in 0.18s ease;
    }

    @keyframes zoom-in {
      from { transform: scale(0.88); opacity: 0; }
      to   { transform: scale(1);    opacity: 1; }
    }

    .lightbox-close {
      position: absolute;
      top: 16px;
      right: 16px;
      width: 40px;
      height: 40px;
      border-radius: 50%;
      border: none;
      background: rgba(255,255,255,0.15);
      color: #fff;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      backdrop-filter: blur(4px);
      transition: background 0.15s;

      svg { width: 20px; height: 20px; }
      &:hover { background: rgba(255,255,255,0.25); }
    }

    .msg-time {
      font-size: 11px;
      color: var(--text-muted);
      padding: 0 4px;
    }

    .input-area {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 16px calc(12px + env(safe-area-inset-bottom, 0px));
      background: var(--surface-glass);
      border-top: 1px solid var(--border);
      flex-shrink: 0;
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
    }

    @media (min-width: 768px) {
      .input-area {
        padding-bottom: 16px;
      }
    }

    .photo-btn {
      color: var(--text-muted);
      cursor: pointer;
      padding: 4px;

      svg { width: 24px; height: 24px; display: block; }
    }

    .msg-input {
      flex: 1;
      border: 1.5px solid var(--border);
      border-radius: 24px;
      padding: 10px 16px;
      font-size: 15px;
      outline: none;
      background: var(--surface-2);
      color: var(--text-primary);
      transition: border-color 0.2s, box-shadow 0.2s;

      &:focus { border-color: var(--brand); background: var(--surface); box-shadow: 0 0 0 3px rgba(156, 206, 43, 0.16); }
    }

    .send-btn {
      width: 42px; height: 42px;
      border-radius: 50%;
      border: none;
      background: var(--brand-gradient);
      color: #fff;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      svg { width: 20px; height: 20px; }

      &:disabled { opacity: 0.4; cursor: not-allowed; }
    }

    /* Fresh conversation layout */
    .chat-page {
      background:
        radial-gradient(circle at 50% -10%, var(--brand-soft), transparent 34%),
        var(--bg);
    }
    .chat-header {
      min-height: 58px;
      padding: 6px 12px 6px 8px;
      justify-content: space-between;
      border-bottom: 1px solid var(--border-light);
      flex-shrink: 0;
    }
    .back-btn {
      width: 38px;
      height: 38px;
      padding: 8px;
      color: var(--text-primary);
      border-radius: 13px;
    }
    .back-btn:active { background: var(--surface-2); }
    .header-info { flex: 1; gap: 10px; min-width: 0; }
    .header-info .avatar {
      position: relative;
      width: 40px;
      height: 40px;
      flex: 0 0 auto;
      overflow: visible;
      background: linear-gradient(145deg, var(--surface-3), var(--brand-soft));
      color: var(--brand-ink);
      font-family: var(--font-editorial);
      font-size: 17px;
      font-weight: 600;
    }
    .header-info .avatar img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
    .presence-dot {
      position: absolute;
      right: -1px;
      bottom: 1px;
      width: 11px;
      height: 11px;
      border: 3px solid var(--surface);
      border-radius: 50%;
      background: var(--brand);
    }
    .header-copy { min-width: 0; }
    .eyebrow {
      display: block;
      color: var(--brand-ink);
      font-size: 9px;
      font-weight: 800;
      line-height: 1;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }
    .header-copy .eyebrow { display: none; }
    .header-copy h1 {
      margin: 0;
      overflow: hidden;
      color: var(--text-primary);
      font-size: 15px;
      font-weight: 700;
      letter-spacing: -0.02em;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .report-btn {
      border: 0;
      background: transparent;
      color: var(--text-muted);
      font: inherit;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      padding: 6px 8px;
    }

    .connection-state {
      padding: 6px 9px;
      display: flex;
      align-items: center;
      gap: 6px;
      border: 1px solid var(--brand-border);
      border-radius: 999px;
      background: var(--brand-soft);
      color: var(--brand-ink);
      font-size: 10px;
      font-weight: 700;
    }
    .connection-state > span { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
    .connection-state.connecting { color: var(--gold-2); border-color: rgba(242,140,38,0.24); background: rgba(242,140,38,0.1); }
    .connection-state.offline { color: var(--text-muted); border-color: var(--border); background: var(--surface-2); }

    .messages-area {
      width: 100%;
      max-width: 760px;
      margin: 0 auto;
      gap: 10px;
      padding: 22px 14px 12px;
    }
    .conversation-start {
      width: min(100%, 360px);
      margin: 14px auto 26px;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
    }
    .start-avatar {
      width: 66px;
      height: 66px;
      margin-bottom: 13px;
      display: grid;
      place-items: center;
      overflow: hidden;
      border: 3px solid var(--bg);
      border-radius: 50%;
      outline: 1px solid var(--brand-border);
      background: linear-gradient(145deg, var(--surface-3), var(--brand-soft));
      color: var(--brand-ink);
      font-family: var(--font-editorial);
      font-size: 27px;
      box-shadow: var(--shadow-float);
    }
    .start-avatar img { width: 100%; height: 100%; object-fit: cover; }
    .conversation-start h2 { margin: 7px 0 0; font-family: var(--font-editorial); font-size: 24px; font-weight: 600; letter-spacing: -0.04em; }
    .conversation-start p { margin: 7px 0 0; color: var(--text-muted); font-size: 12px; line-height: 1.5; }
    .no-msgs {
      flex: none;
      margin: 0 auto 18px;
      padding: 13px 16px;
      display: flex;
      flex-direction: column;
      gap: 2px;
      border: 1px solid var(--border);
      border-radius: 15px;
      background: var(--surface-glass);
    }
    .no-msgs p { margin: 0; color: var(--text-primary); font-size: 13px; font-weight: 700; }
    .no-msgs span { color: var(--text-muted); font-size: 11px; }

    .message { gap: 3px; }
    .bubble {
      max-width: min(78%, 520px);
      padding: 10px 13px;
      border: 1px solid var(--card-border);
      border-radius: 17px 17px 17px 6px;
      background: var(--card-surface);
      box-shadow: 0 3px 10px var(--shadow-sm);
      font-size: 14px;
      line-height: 1.45;
    }
    .message.mine .bubble {
      border: 1px solid var(--brand-border);
      border-radius: 17px 17px 6px 17px;
      background: var(--brand);
      color: #18200d;
      box-shadow: 0 5px 14px var(--brand-glow);
    }
    [data-theme="dark"] .bubble { background: var(--surface-3); border-color: var(--border); }
    [data-theme="dark"] .message.mine .bubble { background: var(--brand); color: #18200d; border-color: var(--brand-border); }
    .msg-time { padding: 0 3px; font-size: 9px; }

    .input-area {
      padding: 8px 10px calc(8px + env(safe-area-inset-bottom, 0px));
      border-top: 0;
      background: linear-gradient(transparent, var(--bg) 26%);
      backdrop-filter: none;
    }
    .composer-shell {
      width: 100%;
      max-width: 760px;
      min-height: 48px;
      margin: 0 auto;
      padding: 4px 4px 4px 7px;
      display: flex;
      align-items: center;
      gap: 4px;
      border: 1px solid var(--border);
      border-radius: 18px;
      background: var(--surface-glass);
      box-shadow: var(--shadow-float);
      backdrop-filter: blur(18px);
      -webkit-backdrop-filter: blur(18px);
    }
    .photo-btn {
      width: 38px;
      height: 38px;
      padding: 0;
      display: grid;
      place-items: center;
      flex: 0 0 auto;
      border-radius: 13px;
      color: var(--text-muted);
    }
    .photo-btn:active { background: var(--surface-2); }
    .photo-btn svg { width: 20px; height: 20px; }
    .msg-input {
      min-width: 0;
      padding: 9px 7px;
      border: 0;
      border-radius: 0;
      background: transparent;
      font-size: 14px;
      box-shadow: none;
    }
    .msg-input:focus { border: 0; background: transparent; box-shadow: none; }
    .send-btn {
      width: 39px;
      height: 39px;
      border-radius: 14px;
      background: var(--brand);
      color: #18200d;
      box-shadow: 0 7px 18px var(--brand-glow);
    }
    .send-btn svg { width: 18px; height: 18px; }
    .send-btn:disabled { background: var(--surface-3); color: var(--text-muted); box-shadow: none; opacity: 1; }

    @media (min-width: 768px) {
      .chat-page {
        max-width: 920px;
        border: 0;
      }
      .chat-header {
        min-height: 72px;
        padding: 10px 18px;
        border: 1px solid var(--border-light);
        border-top: 0;
        border-radius: 0 0 20px 20px;
        background: var(--surface-glass);
      }
      .header-info .avatar { width: 46px; height: 46px; }
      .header-copy .eyebrow { display: block; margin-bottom: 4px; }
      .header-copy h1 { font-size: 17px; }
      .messages-area { padding: 28px 20px 16px; }
      .conversation-start { margin-top: 28px; }
      .input-area { padding: 12px 20px 18px; }
      .composer-shell { min-height: 52px; border-radius: 19px; }
    }
  `]
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {
  @ViewChild('messagesArea') messagesArea!: ElementRef;
  @ViewChildren('photoImg') photoImgs!: QueryList<ElementRef<HTMLImageElement>>;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private matchService = inject(MatchService);
  private keycloak = inject(KeycloakService);
  private profileService = inject(ProfileService);
  private http = inject(HttpClient);
  private chatCache = inject(ChatHistoryCache);
  private reports = inject(ReportService);

  conversationId = signal('');
  messages = signal<Message[]>([]);
  loading = signal(true);
  wsState = signal<'disconnected' | 'connecting' | 'connected'>('disconnected');
  previewUrl = signal<string | null>(null);
  otherProfile = signal<Profile | null>(null);
  messageText = '';
  myId = signal('');  // profile UUID — used to distinguish own vs other messages
  private loadedPhotoIds = signal(new Set<string>());

  private stomp: MinimalStompClient | null = null;
  private seenIds = new Set<string>();
  private blobUrls = new Set<string>();
  private shouldScroll = false;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('conversationId') ?? '';
    this.conversationId.set(id);

    // Resolve the current user's profile ID (not the Keycloak user ID).
    // Profile IDs are what's stored in conversation participants, so we need them
    // both to identify own messages and to register the JWT→profileId mapping on the backend.
    this.profileService.getMe().subscribe({
      next: (profile) => {
        this.myId.set(profile.profileId);
        this.loadHistory(id, profile.profileId);
      },
      error: () => {
        // Fallback: load without profile ID registration (STOMP send will fail)
        this.loadHistory(id, undefined);
      }
    });
  }

  private loadHistory(id: string, profileId: string | undefined): void {
    markConversationRead(id);
    const cacheOwner = profileId ?? '';
    const cached = this.chatCache.read(cacheOwner, id);
    if (cached.length > 0) {
      this.replaceMessages(cached);
      this.loading.set(false);
      this.shouldScroll = true;
    }

    // Passing callerProfileId registers the JWT sub → profileId mapping on the backend,
    // which the WS controller uses to validate STOMP send access.
    this.matchService.getConversation(id, profileId).subscribe({
      next: (conv) => {
        this.loadOtherProfile(conv.participant1Id, conv.participant2Id, profileId);
        this.applyServerMessages(conv.messages ?? []);
        this.persistHistory();
        this.loading.set(false);
        this.shouldScroll = true;
        this.ensureStomp(id);
      },
      error: () => {
        this.loading.set(false);
        if (this.messages().length === 0) {
          this.router.navigate(['/matches']);
          return;
        }
        this.ensureStomp(id);
      }
    });
  }

  private loadOtherProfile(participant1Id: string, participant2Id: string, myProfileId: string | undefined): void {
    if (!myProfileId) return;
    const otherProfileId = participant1Id === myProfileId ? participant2Id : participant1Id;
    if (!otherProfileId || otherProfileId === myProfileId) return;
    this.profileService.getProfile(otherProfileId).subscribe({
      next: profile => this.otherProfile.set(profile)
    });
  }

  private replaceMessages(msgs: Message[]): void {
    this.seenIds = new Set(msgs.map(m => m.id));
    this.messages.set(msgs);
  }

  private applyServerMessages(serverMsgs: Message[]): void {
    serverMsgs.forEach(m => this.seenIds.add(m.id));
    this.messages.set(mergeServerChatMessages(this.messages(), serverMsgs, this.loadedPhotoIds()));
  }

  private persistHistory(): void {
    const conversationId = this.conversationId();
    const profileId = this.myId();
    if (!conversationId || !profileId) {
      return;
    }
    this.chatCache.write(profileId, conversationId, this.messages());
  }

  private ensureStomp(conversationId: string): void {
    if (this.wsState() !== 'disconnected') {
      return;
    }
    void this.connectStomp(conversationId);
  }

  ngOnDestroy(): void {
    this.stomp?.disconnect();
    this.stomp = null;
    this.blobUrls.forEach(url => URL.revokeObjectURL(url));
    this.blobUrls.clear();
  }

  ngAfterViewChecked(): void {
    this.captureCompletePhotos();
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  private captureCompletePhotos(): void {
    this.photoImgs?.forEach(ref => {
      const img = ref.nativeElement;
      const id = img.getAttribute('data-photo-id');
      if (!id || this.loadedPhotoIds().has(id)) {
        return;
      }
      if (img.complete && img.naturalWidth > 0) {
        this.markPhotoReady(id);
      }
    });
  }

  private async connectStomp(conversationId: string): Promise<void> {
    this.wsState.set('connecting');

    const token = await this.keycloak.getToken();
    if (!token) {
      this.wsState.set('disconnected');
      return;
    }

    this.stomp = new MinimalStompClient(environment.wsUrl, {
      onSocketClosed: () => this.wsState.set('disconnected'),
      onSocketError: () => this.wsState.set('disconnected')
    });

    try {
      await this.stomp.connect({ Authorization: `Bearer ${token}` });
      this.wsState.set('connected');

      this.stomp.subscribe(
        `/topic/conversations/${conversationId}`,
        (frame) => this.handleStompMessage(frame.body)
      );
    } catch {
      this.wsState.set('disconnected');
    }
  }

  private handleStompMessage(body: string): void {
    try {
      const event = JSON.parse(body) as StompMessageEvent;
      if (event.type === 'MODERATION_BLOCKED') {
        const blockedId = event.clientMessageId;
        if (blockedId) {
          this.messages.update(msgs => msgs.filter(message => message.id !== blockedId));
          this.seenIds.delete(blockedId);
          this.persistHistory();
        }
        this.showToast(event.text || 'This message was blocked by moderation.');
        return;
      }
      const id = event.messageId;
      if (!id || this.seenIds.has(id)) return;

      // If the backend echoes back our optimistically-added message (matched by clientMessageId),
      // replace the temporary local id with the server-assigned id instead of duplicating.
      if (event.clientMessageId && this.seenIds.has(event.clientMessageId)) {
        this.messages.update(msgs =>
          msgs.map(m => m.id === event.clientMessageId ? { ...m, id } : m)
        );
        this.transferPhotoReady(event.clientMessageId, id);
        this.seenIds.add(id);
        this.persistHistory();
        return;
      }

      this.seenIds.add(id);

      const isPhoto = event.type === 'IMAGE';
      const content = isPhoto
        ? (event.attachments?.[0]?.url ?? '')
        : (event.text ?? '');

      const msg: Message = {
        id,
        senderId: event.senderId ?? '',
        content,
        type: isPhoto ? 'photo' : 'text',
        sentAt: event.occurredAt ?? new Date().toISOString()
      };

      this.messages.update(msgs => [...msgs, msg]);
      this.shouldScroll = true;
      this.persistHistory();
    } catch {
      // ignore malformed frames
    }
  }

  readonly previewMode = environment.designPreview;

  sendMessage(): void {
    const text = this.messageText.trim();
    if (!text) return;
    const stomp = this.stomp;
    if (!this.previewMode && (!stomp || this.wsState() !== 'connected')) return;

    const clientMessageId = crypto.randomUUID();

    // Optimistic update — show message immediately without waiting for STOMP echo.
    const optimisticMsg: Message = {
      id: clientMessageId,
      senderId: this.myId(),
      content: text,
      type: 'text',
      sentAt: new Date().toISOString()
    };
    this.seenIds.add(clientMessageId);
    this.messages.update(msgs => [...msgs, optimisticMsg]);
    this.shouldScroll = true;
    this.persistHistory();
    this.messageText = '';

    if (this.previewMode) {
      if (previewTextBlocked(text)) {
        this.handleStompMessage(JSON.stringify({
          type: 'MODERATION_BLOCKED',
          clientMessageId,
          text: 'This message was blocked by moderation.'
        }));
      }
      return;
    }

    if (!stomp) return;
    stomp.send('/app/chat.send', {
      conversationId: this.conversationId(),
      clientMessageId,
      messageType: 'TEXT',
      text,
      attachments: []
    });
  }

  async sendPhoto(e: Event): Promise<void> {
     let file = (e.target as HTMLInputElement).files?.[0];
     (e.target as HTMLInputElement).value = '';
     if (!file) return;

     const clientMessageId = crypto.randomUUID();
     let previewUrl = '';
     if (!isHeicPhoto(file)) {
       previewUrl = URL.createObjectURL(file);
       this.blobUrls.add(previewUrl);
     }
     const pending: Message = {
       id: clientMessageId,
       senderId: this.myId(),
       content: previewUrl,
       type: 'photo',
       sentAt: new Date().toISOString()
     };
     this.seenIds.add(clientMessageId);
     this.messages.update(msgs => [...msgs, pending]);
     this.shouldScroll = true;
     this.persistHistory();

     try {
       file = await preparePhotoForUpload(file, { maxSizeBytes: PHOTO_UPLOAD_MAX_BYTES });

       if (!previewUrl) {
         previewUrl = URL.createObjectURL(file);
         this.blobUrls.add(previewUrl);
         this.messages.update(msgs =>
           msgs.map(m => m.id === clientMessageId ? { ...m, content: previewUrl } : m)
         );
       }

       const token = await this.keycloak.getToken();
       if (!token) {
         this.removePendingPhoto(clientMessageId, previewUrl);
         this.showToast('Failed to get authentication token');
         return;
       }

       const params = new URLSearchParams({
         senderId: this.myId(),
         clientMessageId
       });

       const formData = new FormData();
       formData.append('file', file, file.name);

       await firstValueFrom(
         this.http.post(
           `${environment.apiGatewayUrl}/rest/conversations/${this.conversationId()}/messages/photos?${params}`,
           formData,
           { headers: { Authorization: `Bearer ${token}` } }
         )
       );
     } catch (err: unknown) {
       const pendingMsg = this.messages().find(m => m.id === clientMessageId);
       this.removePendingPhoto(clientMessageId, pendingMsg?.content);
       this.showToast(photoUploadFailureMessage(err));
     }
   }

  isPhotoReady(id: string): boolean {
    return this.loadedPhotoIds().has(id);
  }

  markPhotoReady(id: string): void {
    if (this.loadedPhotoIds().has(id)) {
      return;
    }
    this.loadedPhotoIds.update(ids => {
      const next = new Set(ids);
      next.add(id);
      return next;
    });
  }

  private transferPhotoReady(fromId: string, toId: string): void {
    this.loadedPhotoIds.update(ids => {
      if (!ids.has(fromId)) {
        return ids;
      }
      const next = new Set(ids);
      next.delete(fromId);
      next.add(toId);
      return next;
    });
  }

  private removePendingPhoto(id: string, blobUrl?: string): void {
    this.seenIds.delete(id);
    this.messages.update(msgs => msgs.filter(m => m.id !== id));
    this.revokeBlob(blobUrl);
  }

  private revokeBlob(url?: string): void {
    if (!url || !this.blobUrls.has(url)) {
      return;
    }
    URL.revokeObjectURL(url);
    this.blobUrls.delete(url);
  }

  private showToast(message: string): void {
    alert(message);
  }

  openPreview(url: string): void {
    if (!url) return;
    this.previewUrl.set(url);
  }

  closePreview(): void {
    this.previewUrl.set(null);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closePreview();
  }

  reportConversation(): void {
    const details = window.prompt('Why are you reporting this conversation?');
    if (!details?.trim()) return;
    this.reports.reportConversation(this.conversationId(), 'other', details.trim()).subscribe({
      next: () => this.showToast('Thanks. We will review this conversation.'),
      error: (err: unknown) => this.showToast(moderationFailureMessage(err, 'Could not send the report. Please try again.'))
    });
  }

  goBack(): void {
    this.router.navigate(['/matches']);
  }

  otherName(): string {
    return this.otherProfile()?.name || 'New connection';
  }

  otherInitial(): string {
    return this.otherProfile()?.name?.[0]?.toUpperCase() || 'C';
  }

  connectionLabel(): string {
    if (this.wsState() === 'connected') return 'Live';
    if (this.wsState() === 'connecting') return 'Connecting';
    return 'Offline';
  }

  formatTime(dateStr: string): string {
    if (!dateStr) return '';
    return new Date(dateStr).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  private scrollToBottom(): void {
    try {
      this.messagesArea.nativeElement.scrollTop = this.messagesArea.nativeElement.scrollHeight;
    } catch {}
  }
}
