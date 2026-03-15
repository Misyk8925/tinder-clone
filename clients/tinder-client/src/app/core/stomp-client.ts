export interface StompFrame {
  command: string;
  headers: Record<string, string>;
  body: string;
}

interface StompClientOptions {
  onSocketClosed?: () => void;
  onSocketError?: (message: string) => void;
}

type FrameHandler = (frame: StompFrame) => void;

export class MinimalStompClient {
  private socket: WebSocket | null = null;
  private receiveBuffer = '';
  private isConnected = false;
  private connectResolve: (() => void) | null = null;
  private connectReject: ((error: Error) => void) | null = null;

  private readonly subscriptions = new Map<string, FrameHandler>();

  constructor(
    private readonly socketUrl: string,
    private readonly options: StompClientOptions = {}
  ) {}

  connect(connectHeaders: Record<string, string> = {}): Promise<void> {
    if (this.isConnected) {
      return Promise.resolve();
    }

    if (!this.socketUrl) {
      return Promise.reject(new Error('WebSocket URL is required'));
    }

    return new Promise<void>((resolve, reject) => {
      this.connectResolve = resolve;
      this.connectReject = reject;

      const socket = new WebSocket(this.socketUrl);
      this.socket = socket;

      socket.onopen = () => {
        this.sendRawFrame('CONNECT', {
          'accept-version': '1.2',
          'heart-beat': '0,0',
          ...connectHeaders
        });
      };

      socket.onmessage = (event) => {
        if (typeof event.data === 'string') {
          this.handleIncomingChunk(event.data);
        }
      };

      socket.onerror = () => {
        this.failConnect('WebSocket connection error');
        this.options.onSocketError?.('WebSocket connection error');
      };

      socket.onclose = () => {
        this.isConnected = false;
        this.failConnect('WebSocket closed before STOMP CONNECTED frame');
        this.options.onSocketClosed?.();
      };
    });
  }

  subscribe(destination: string, onMessage: FrameHandler): string {
    this.ensureConnected();

    const id = `sub-${this.makeId()}`;
    this.subscriptions.set(id, onMessage);

    this.sendRawFrame('SUBSCRIBE', {
      id,
      destination
    });

    return id;
  }

  send(destination: string, payload: unknown, headers: Record<string, string> = {}): void {
    this.ensureConnected();

    const body = JSON.stringify(payload);
    this.sendRawFrame(
      'SEND',
      {
        destination,
        'content-type': 'application/json',
        ...headers
      },
      body
    );
  }

  disconnect(): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN && this.isConnected) {
      this.sendRawFrame('DISCONNECT', {});
    }

    this.connectResolve = null;
    this.connectReject = null;
    this.isConnected = false;
    this.subscriptions.clear();

    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }

  private ensureConnected(): void {
    if (!this.socket || !this.isConnected || this.socket.readyState !== WebSocket.OPEN) {
      throw new Error('STOMP client is not connected');
    }
  }

  private failConnect(message: string): void {
    if (this.connectReject) {
      this.connectReject(new Error(message));
      this.connectReject = null;
      this.connectResolve = null;
    }
  }

  private handleIncomingChunk(chunk: string): void {
    this.receiveBuffer += chunk;

    while (true) {
      const frameEnd = this.receiveBuffer.indexOf('\0');
      if (frameEnd < 0) {
        return;
      }

      const rawFrame = this.receiveBuffer.slice(0, frameEnd);
      this.receiveBuffer = this.receiveBuffer.slice(frameEnd + 1);

      const normalized = rawFrame.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
      if (normalized.trim().length === 0) {
        continue;
      }

      const frame = this.parseFrame(normalized.replace(/^\n+/, ''));
      this.handleFrame(frame);
    }
  }

  private handleFrame(frame: StompFrame): void {
    if (frame.command === 'CONNECTED') {
      this.isConnected = true;
      if (this.connectResolve) {
        this.connectResolve();
      }
      this.connectResolve = null;
      this.connectReject = null;
      return;
    }

    if (frame.command === 'ERROR') {
      const message = frame.body || frame.headers['message'] || 'Server returned STOMP ERROR frame';
      this.options.onSocketError?.(message);
      this.failConnect(message);
      return;
    }

    if (frame.command === 'MESSAGE') {
      const subscriptionId = frame.headers['subscription'];
      if (subscriptionId && this.subscriptions.has(subscriptionId)) {
        const handler = this.subscriptions.get(subscriptionId);
        handler?.(frame);
      }
    }
  }

  private parseFrame(rawFrame: string): StompFrame {
    const separatorIndex = rawFrame.indexOf('\n\n');
    const headerSection = separatorIndex >= 0 ? rawFrame.slice(0, separatorIndex) : rawFrame;
    const body = separatorIndex >= 0 ? rawFrame.slice(separatorIndex + 2) : '';

    const headerLines = headerSection.split('\n');
    const command = headerLines[0]?.trim() ?? '';
    const headers: Record<string, string> = {};

    for (const line of headerLines.slice(1)) {
      const delimiter = line.indexOf(':');
      if (delimiter < 0) {
        continue;
      }
      const key = line.slice(0, delimiter).trim();
      const value = line.slice(delimiter + 1).trim();
      headers[key] = value;
    }

    return {
      command,
      headers,
      body
    };
  }

  private sendRawFrame(command: string, headers: Record<string, string>, body = ''): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket is not open');
    }

    const lines = [command, ...Object.entries(headers).map(([key, value]) => `${key}:${value}`)];

    if (body.length > 0 && !Object.prototype.hasOwnProperty.call(headers, 'content-length')) {
      lines.push(`content-length:${new TextEncoder().encode(body).length}`);
    }

    const frame = `${lines.join('\n')}\n\n${body}\0`;
    this.socket.send(frame);
  }

  private makeId(): string {
    if (
      typeof globalThis !== 'undefined' &&
      globalThis.crypto &&
      typeof globalThis.crypto.randomUUID === 'function'
    ) {
      return globalThis.crypto.randomUUID();
    }
    return `${Date.now()}-${Math.floor(Math.random() * 1_000_000_000)}`;
  }
}
