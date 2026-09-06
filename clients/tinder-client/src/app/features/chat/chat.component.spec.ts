// @vitest-environment jsdom
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { NEVER, of } from 'rxjs';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { KeycloakService } from '../../core/services/keycloak.service';
import { MatchService } from '../../core/services/match.service';
import { ProfileService } from '../../core/services/profile.service';
import { ChatHistoryCache } from '../../core/services/chat-history.cache';
import { ReportService } from '../../core/services/report.service';
import { ChatComponent } from './chat.component';

describe('ChatComponent photo loading placeholders', () => {
  let fixture: ComponentFixture<ChatComponent>;
  let component: ChatComponent;

  beforeAll(() => {
    try {
      TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
    } catch {
      // ng test already initialized the environment
    }
  });

  beforeEach(async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'conv-1' } } } },
        { provide: MatchService, useValue: { getConversation: vi.fn(() => NEVER) } },
        { provide: ProfileService, useValue: { getMe: vi.fn(() => NEVER) } },
        { provide: KeycloakService, useValue: { getToken: vi.fn() } },
        { provide: HttpClient, useValue: { post: vi.fn(() => of(null)) } },
        { provide: ReportService, useValue: { reportConversation: vi.fn(() => of(null)) } },
        ChatHistoryCache,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatComponent);
    component = fixture.componentInstance;
    component.loading.set(false);
    component.myId.set('me');
  });

  it('Given photo messages on both sides, when images have not loaded, then placeholders are shown', () => {
    component.messages.set([
      { id: 'theirs', senderId: 'other', content: 'https://cdn.example/theirs.jpg', type: 'photo', sentAt: '2026-08-22T10:00:00Z' },
      { id: 'mine', senderId: 'me', content: 'https://cdn.example/mine.jpg', type: 'photo', sentAt: '2026-08-22T10:01:00Z' },
    ]);
    fixture.detectChanges();

    const frames = fixture.nativeElement.querySelectorAll('.photo-frame');
    expect(frames).toHaveLength(2);
    expect(fixture.nativeElement.querySelectorAll('.photo-skeleton')).toHaveLength(2);
    expect(fixture.nativeElement.querySelectorAll('.message.mine .photo-skeleton')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('.message:not(.mine) .photo-skeleton')).toHaveLength(1);
  });

  it('Given a photo placeholder, when the image loads, then the photo is shown without the skeleton', () => {
    component.messages.set([
      { id: 'theirs', senderId: 'other', content: 'https://cdn.example/theirs.jpg', type: 'photo', sentAt: '2026-08-22T10:00:00Z' },
    ]);
    fixture.detectChanges();

    const img = fixture.nativeElement.querySelector('.msg-photo') as HTMLImageElement;
    img.dispatchEvent(new Event('load'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.photo-skeleton')).toBeNull();
    expect(img.classList.contains('ready')).toBe(true);
  });

  it('Given a photo image that is already complete, when rendered, then the skeleton is gone', () => {
    component.messages.set([
      { id: 'cached', senderId: 'other', content: 'https://cdn.example/cached.jpg', type: 'photo', sentAt: '2026-08-22T10:00:00Z' },
    ]);
    fixture.detectChanges();

    const img = fixture.nativeElement.querySelector('.msg-photo') as HTMLImageElement;
    Object.defineProperty(img, 'complete', { configurable: true, value: true });
    Object.defineProperty(img, 'naturalWidth', { configurable: true, value: 120 });
    component.ngAfterViewChecked();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.photo-skeleton')).toBeNull();
    expect(img.classList.contains('ready')).toBe(true);
  });

  it('Given cached history, when chat opens before the API returns, then messages are shown immediately', async () => {
    const cache = TestBed.inject(ChatHistoryCache);
    cache.write('me', 'conv-1', [
      { id: 'cached', senderId: 'other', content: 'hello from cache', type: 'text', sentAt: '2026-08-22T10:00:00Z' }
    ]);
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'conv-1' } } } },
        { provide: MatchService, useValue: { getConversation: vi.fn(() => NEVER) } },
        { provide: ProfileService, useValue: { getMe: vi.fn(() => of({ profileId: 'me' })) } },
        { provide: KeycloakService, useValue: { getToken: vi.fn() } },
        { provide: HttpClient, useValue: { post: vi.fn(() => of(null)) } },
        { provide: ReportService, useValue: { reportConversation: vi.fn(() => of(null)) } },
        { provide: ChatHistoryCache, useValue: cache },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.loading-msgs')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('hello from cache');
  });

  it('Given a conversation participant, when chat opens, then the participant profile anchors the header and intro', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'conv-1' } } } },
        { provide: MatchService, useValue: { getConversation: vi.fn(() => of({
          id: 'conv-1',
          participant1Id: 'me',
          participant2Id: 'mila',
          createdAt: '',
          messages: [],
        })) } },
        { provide: ProfileService, useValue: {
          getMe: vi.fn(() => of({ profileId: 'me' })),
          getProfile: vi.fn(() => of({
            profileId: 'mila',
            name: 'Mila',
            isActive: true,
            photos: [{ url: '/mila.jpg' }],
          })),
        } },
        { provide: KeycloakService, useValue: { getToken: vi.fn() } },
        { provide: HttpClient, useValue: { post: vi.fn(() => of(null)) } },
        { provide: ReportService, useValue: { reportConversation: vi.fn(() => of(null)) } },
        ChatHistoryCache,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.chat-header h1')?.textContent).toContain('Mila');
    expect(fixture.nativeElement.querySelector('.chat-header .avatar img')?.getAttribute('alt')).toBe('Mila');
    expect(fixture.nativeElement.querySelector('.conversation-start h2')?.textContent).toContain('You and Mila matched');
  });

  it('Given preview mode, when a hate message is sent, then it is dropped and the user is told', () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => undefined);
    (component as { previewMode: boolean }).previewMode = true;
    component.messageText = 'kill yourself';

    component.sendMessage();
    fixture.detectChanges();

    expect(component.messages()).toHaveLength(0);
    expect(alertSpy).toHaveBeenCalledWith('This message was blocked by moderation.');
    alertSpy.mockRestore();
  });

  it('Given preview mode, when a normal message is sent, then it stays in the conversation', () => {
    (component as { previewMode: boolean }).previewMode = true;
    component.messageText = 'That trail sounds perfect. Saturday?';

    component.sendMessage();
    fixture.detectChanges();

    expect(component.messages()).toHaveLength(1);
    expect(component.messages()[0].content).toBe('That trail sounds perfect. Saturday?');
  });
});
