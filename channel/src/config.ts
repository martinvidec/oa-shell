import os from 'node:os';
import path from 'node:path';

/** Laufzeit-Konfiguration des Channels/CLIs (aus Env + gespeichertem Token). */
export interface OaShellConfig {
  /** Basis-URL der App (HTTP[S]). */
  appUrl: string;
  /** Bridge-WebSocket-URL (aus appUrl abgeleitet, sofern nicht gesetzt). */
  wsUrl: string;
  /** Pfad der lokal gespeicherten Zugangsdaten (Device-Grant-Token). */
  credentialsPath: string;
}

/**
 * Lädt die Konfiguration aus der Umgebung. Präzedenz der App-URL:
 * `OASHELL_APP_URL` (z. B. aus der Plugin-`userConfig`) > `fallbackAppUrl`
 * (z. B. die beim Login gespeicherte `appUrl`) > Default `http://127.0.0.1:8080`.
 */
export function loadConfig(fallbackAppUrl?: string): OaShellConfig {
  const appUrl = process.env.OASHELL_APP_URL ?? fallbackAppUrl ?? 'http://127.0.0.1:8080';
  const wsUrl = process.env.OASHELL_WS_URL ?? appUrl.replace(/^http/i, 'ws') + '/bridge';
  const credentialsPath =
    process.env.OASHELL_CREDENTIALS ?? path.join(os.homedir(), '.oa-shell', 'credentials.json');
  return { appUrl, wsUrl, credentialsPath };
}
