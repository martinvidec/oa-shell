import { WebSocket } from 'ws';
import type { AppToChannel, ChannelToApp } from './protocol.js';

export interface BridgeClientOptions {
  /** Bridge-WebSocket-URL der App (z. B. wss://app/bridge). */
  wsUrl: string;
  /** Device-Grant-Access-Token (Bearer) für den Handshake. */
  token: string;
  /** Eingehende App-Nachrichten (nur über die authentifizierte, offene Verbindung). */
  onMessage: (msg: AppToChannel) => void;
  onOpen?: () => void;
  reconnectMs?: number;
  log?: (msg: string) => void;
}

/**
 * Kapselt die AUSGEHENDE, token-authentifizierte WebSocket-Verbindung des Channels
 * zur App-Bridge: Handshake mit Bearer-Token, Senden/Empfangen von Envelopes,
 * Reconnect und Sender-Gating (es werden nur Nachrichten der aktuellen, offenen
 * Verbindung verarbeitet).
 */
export class BridgeClient {
  private ws: WebSocket | null = null;
  private stopped = false;

  constructor(private readonly opts: BridgeClientOptions) {}

  start(): void {
    this.stopped = false;
    this.connect();
  }

  stop(): void {
    this.stopped = true;
    this.ws?.close();
    this.ws = null;
  }

  send(msg: ChannelToApp): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg));
    }
  }

  private connect(): void {
    if (this.stopped) {
      return;
    }
    const ws = new WebSocket(this.opts.wsUrl, {
      headers: { Authorization: `Bearer ${this.opts.token}` },
    });
    this.ws = ws;

    ws.on('open', () => {
      this.log('WS offen');
      this.opts.onOpen?.();
    });

    ws.on('message', (data: Buffer) => {
      // Sender-Gating: nur Nachrichten der aktuellen, authentifizierten, offenen Verbindung.
      if (this.ws !== ws || ws.readyState !== WebSocket.OPEN) {
        return;
      }
      let msg: AppToChannel;
      try {
        msg = JSON.parse(data.toString()) as AppToChannel;
      } catch {
        return;
      }
      this.opts.onMessage(msg);
    });

    ws.on('close', () => {
      this.log('WS getrennt');
      if (!this.stopped) {
        setTimeout(() => this.connect(), this.opts.reconnectMs ?? 1500);
      }
    });

    ws.on('error', (err: Error) => this.log(`WS-Fehler: ${err.message}`));
  }

  private log(msg: string): void {
    this.opts.log?.(msg);
  }
}
