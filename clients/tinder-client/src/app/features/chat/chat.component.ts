import {
  Component, inject, OnInit, OnDestroy, signal,
  ViewChild, ElementRef, AfterViewChecked
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { NgClass } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { MatchService, Message } from '../../core/services/match.service';
import { KeycloakService } from '../../core/services/keycloak.service';
import { ProfileService } from '../../core/services/profile.service';
import { MinimalStompClient } from '../../core/stomp-client';
import { environment } from '../../../environments/environment';

interface StompMessageEvent {
  occurredAt?: string;
  messageId?: string;
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
        <button class="back-btn" (click)="goBack()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
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
                  <img [src]="msg.content" class="msg-photo" alt="Photo" />
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
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
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
  `,
  styles: [`
    .chat-page {
      display: flex;
      flex-direction: column;
      height: 100vh;
      background: #f5f5f5;
    }

    .chat-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      background: #fff;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06);
      z-index: 10;
    }

    .back-btn {
      background: none;
      border: none;
      cursor: pointer;
      padding: 4px;
      color: #fd5564;

      svg { width: 24px; height: 24px; display: block; }
    }

    .header-info {
      display: flex;
      align-items: center;
      gap: 12px;

      .avatar {
        width: 42px; height: 42px;
        border-radius: 50%;
        background: linear-gradient(135deg, #fd5564, #ff8a00);
        color: #fff;
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 700;
        font-size: 14px;
      }

      h2 { margin: 0; font-size: 16px; color: #333; }
    }

    .online {
      font-size: 12px;
      color: #00d26a;
      font-weight: 500;

      &.connecting { color: #f0a500; }
    }

    .messages-area {
      flex: 1;
      overflow-y: auto;
      padding: 16px;
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
      border: 3px solid #f0f0f0;
      border-top: 3px solid #fd5564;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .no-msgs {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      p { color: #aaa; font-size: 16px; }
    }

    .message {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 2px;

      &.mine {
        align-items: flex-end;

        .bubble {
          background: linear-gradient(135deg, #fd5564, #ff8a00);
          color: #fff;
          border-radius: 18px 18px 4px 18px;
        }
      }
    }

    .bubble {
      background: #fff;
      color: #333;
      padding: 10px 14px;
      border-radius: 18px 18px 18px 4px;
      max-width: 70%;
      font-size: 15px;
      line-height: 1.4;
      box-shadow: 0 1px 4px rgba(0,0,0,0.08);
      word-break: break-word;
    }

    .msg-photo {
      max-width: 200px;
      border-radius: 12px;
      display: block;
    }

    .msg-time {
      font-size: 11px;
      color: #aaa;
      padding: 0 4px;
    }

    .input-area {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 16px;
      background: #fff;
      border-top: 1px solid #f0f0f0;
      padding-bottom: calc(12px + env(safe-area-inset-bottom, 0px));
    }

    .photo-btn {
      color: #aaa;
      cursor: pointer;
      padding: 4px;

      svg { width: 24px; height: 24px; display: block; }
    }

    .msg-input {
      flex: 1;
      border: 1.5px solid #e8e8e8;
      border-radius: 24px;
      padding: 10px 16px;
      font-size: 15px;
      outline: none;
      background: #f8f8f8;
      transition: border-color 0.2s;

      &:focus { border-color: #fd5564; background: #fff; }
    }

    .send-btn {
      width: 42px; height: 42px;
      border-radius: 50%;
      border: none;
      background: linear-gradient(135deg, #fd5564, #ff8a00);
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

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private matchService = inject(MatchService);
  private keycloak = inject(KeycloakService);
  private profileService = inject(ProfileService);
  private http = inject(HttpClient);

  conversationId = signal('');
  messages = signal<Message[]>([]);
  loading = signal(true);
  wsState = signal<'disconnected' | 'connecting' | 'connected'>('disconnected');
  messageText = '';
  myId = signal('');  // profile UUID — used to distinguish own vs other messages

  private stomp: MinimalStompClient | null = null;
  private seenIds = new Set<string>();
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
    // Passing callerProfileId registers the JWT sub → profileId mapping on the backend,
    // which the WS controller uses to validate STOMP send access.
    this.matchService.getConversation(id, profileId).subscribe({
      next: (conv) => {
        const msgs = conv.messages ?? [];
        msgs.forEach(m => this.seenIds.add(m.id));
        this.messages.set(msgs);
        this.loading.set(false);
        this.shouldScroll = true;
        this.connectStomp(id);
      },
      error: () => {
        this.loading.set(false);
        this.connectStomp(id);
      }
    });
  }

  ngOnDestroy(): void {
    this.stomp?.disconnect();
    this.stomp = null;
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
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
    } catch {
      // ignore malformed frames
    }
  }

  sendMessage(): void {
    const text = this.messageText.trim();
    if (!text || !this.stomp || this.wsState() !== 'connected') return;

    this.stomp.send('/app/chat.send', {
      conversationId: this.conversationId(),
      clientMessageId: crypto.randomUUID(),
      messageType: 'TEXT',
      text,
      attachments: []
    });

    this.messageText = '';
  }

  async sendPhoto(e: Event): Promise<void> {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;

    const token = await this.keycloak.getToken();
    if (!token) return;

    const clientMessageId = crypto.randomUUID();
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
    ).catch(() => null);

    // Photo arrives via STOMP broadcast — no local push needed
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
