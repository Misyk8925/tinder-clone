import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { describe, expect, it, vi } from 'vitest';
import { ReportService } from './report.service';
import { environment } from '../../../environments/environment';

describe('ReportService', () => {
  it('Given a profile report, when submitted, then it posts to the profiles report API', () => {
    const post = vi.fn().mockReturnValue('ok');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        ReportService,
        { provide: HttpClient, useValue: { post } }
      ]
    });

    TestBed.inject(ReportService).reportProfile('profile-1', 'spam', 'Unsolicited ads');

    expect(post).toHaveBeenCalledWith(
      `${environment.apiGatewayUrl}/api/v1/profiles/profile-1/report`,
      { reason: 'spam', details: 'Unsolicited ads' }
    );
  });

  it('Given a conversation report, when submitted, then it posts to the match report API', () => {
    const post = vi.fn().mockReturnValue('ok');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        ReportService,
        { provide: HttpClient, useValue: { post } }
      ]
    });

    TestBed.inject(ReportService).reportConversation('conv-1', 'harassment', 'Rude message', 'msg-9');

    expect(post).toHaveBeenCalledWith(
      `${environment.apiGatewayUrl}/rest/conversations/conv-1/report`,
      { reason: 'harassment', details: 'Rude message', messageId: 'msg-9' }
    );
  });
});
