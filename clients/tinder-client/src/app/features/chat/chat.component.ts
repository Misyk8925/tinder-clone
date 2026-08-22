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
import { ChatHistoryCache } from '../../core/services/chat-history.cache';
import { markConversationRead } from '../matches/matches.component';
import { KeycloakService } from '../../core/services/keycloak.service';
import { ProfileService } from '../../core/services/profile.service';
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
          <div class="avatar">{{ conversationId().slice(0,2).toUpperCase() }}</div>
          <div>
            <h2>Match Chat</h2>
            <span class="online" [class.connecting]="wsState() === 'connecting'">
              {{ wsState() === 'connected' ? 'Online' : wsState() === 'connecting' ? 'Connecting…' : 'Offline' }}
            </span>
          </div>
        </div>
      </header>

      <div class="messages-area" #messagesArea>
        @if (loading()) {
          <div class="loading-msgs">
            <div class="spinner"></div>
          </div>
        } @else if (messages().length === 0) {
          <div class="no-msgs">
            <p>Say hello! 👋</p>
          </div>
        } @else {
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
        <label class="photo-btn" title="Send photo">
          <input type="file" accept="image/*" (change)="sendPhoto($event)" hidden />
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
            <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/>
            <polyline points="21 15 16 10 5 21"/>
          </svg>
        </label>
        <input
          class="msg-input"
          type="text"
          [(ngModel)]="messageText"
          placeholder="Type a message..."
          (keydown.enter)="sendMessage()"
        />
        <button class="send-btn" (click)="sendMessage()" [disabled]="!messageText.trim() || wsState() !== 'connected'">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
          </svg>
        </button>
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

  conversationId = signal('');
  messages = signal<Message[]>([]);
  loading = signal(true);
  wsState = signal<'disconnected' | 'connecting' | 'connected'>('disconnected');
  previewUrl = signal<string | null>(null);
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

  sendMessage(): void {
    const text = this.messageText.trim();
    if (!text || !this.stomp || this.wsState() !== 'connected') return;

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

    this.stomp.send('/app/chat.send', {
      conversationId: this.conversationId(),
      clientMessageId,
      messageType: 'TEXT',
      text,
      attachments: []
    });

    this.messageText = '';
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

  goBack(): void {
    this.router.navigate(['/matches']);
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
