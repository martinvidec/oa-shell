import fs from 'node:fs';
import path from 'node:path';

/** Lokal gespeicherte Zugangsdaten (Device-Grant-Token). Enthält Geheimnisse. */
export interface StoredCredentials {
  appUrl: string;
  accessToken: string;
  tokenType: string;
  refreshToken?: string;
  scope?: string;
  /** Ablauf des Access-Tokens (epoch ms), sofern bekannt. */
  expiresAt?: number;
  /** Zeitpunkt der Ausstellung (epoch ms). */
  obtainedAt: number;
}

/**
 * Speichert die Zugangsdaten atomar mit Dateirechten 0600 (Verzeichnis 0700).
 * Der Token wird nie geloggt.
 */
export function saveCredentials(file: string, creds: StoredCredentials): void {
  const dir = path.dirname(file);
  fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  const tmp = `${file}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(creds, null, 2), { mode: 0o600 });
  fs.chmodSync(tmp, 0o600);
  fs.renameSync(tmp, file);
  fs.chmodSync(file, 0o600);
}

export function loadCredentials(file: string): StoredCredentials | null {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8')) as StoredCredentials;
  } catch {
    return null;
  }
}

export function clearCredentials(file: string): void {
  try {
    fs.rmSync(file, { force: true });
  } catch {
    /* ignorieren */
  }
}
