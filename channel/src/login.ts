#!/usr/bin/env node
import { spawn } from 'node:child_process';
import { loadConfig } from './config.js';
import { saveCredentials, type StoredCredentials } from './credentials.js';
import { DeviceFlowError, pollForToken, requestDeviceAuthorization } from './device-flow.js';

const CLIENT_ID = 'oa-shell-channel';
const SCOPE = 'session files';

/** Öffnet die Verifizierungs-URL best-effort im Standardbrowser (Fehler werden ignoriert). */
function openBrowser(url: string): void {
  const cmd =
    process.platform === 'darwin' ? 'open' : process.platform === 'win32' ? 'start' : 'xdg-open';
  try {
    const child = spawn(cmd, [url], { stdio: 'ignore', detached: true, shell: process.platform === 'win32' });
    child.on('error', () => undefined);
    child.unref();
  } catch {
    /* kein Browser verfügbar (z. B. headless) – URL wird ohnehin ausgegeben */
  }
}

/**
 * `oa-shell login [app-url]` — führt den OAuth 2.0 Device Authorization Grant gegen die
 * App durch und speichert das kontogebundene Token lokal (0600). Der Token wird nie
 * ausgegeben. Die App-URL kann als Argument übergeben werden (Vorrang vor `OASHELL_APP_URL`).
 */
async function main(): Promise<void> {
  const argUrl = process.argv[2]?.trim();
  const cfg = loadConfig();
  if (argUrl) {
    cfg.appUrl = argUrl;
    cfg.wsUrl = process.env.OASHELL_WS_URL ?? argUrl.replace(/^http/i, 'ws') + '/bridge';
  }
  console.log(`oa-shell login → ${cfg.appUrl}`);

  const auth = await requestDeviceAuthorization(cfg.appUrl, CLIENT_ID, SCOPE);
  const url = auth.verification_uri_complete ?? auth.verification_uri;
  console.log('\nZum Anmelden im Browser öffnen:');
  console.log(`  ${url}`);
  console.log(`\nFalls nach einem Code gefragt wird:  ${auth.user_code}\n`);
  openBrowser(url);
  process.stdout.write('Warte auf Bestätigung ');

  let token;
  try {
    token = await pollForToken(cfg.appUrl, CLIENT_ID, auth, {
      onPending: () => process.stdout.write('.'),
    });
  } catch (err) {
    process.stdout.write('\n');
    if (err instanceof DeviceFlowError) {
      console.error(`Fehlgeschlagen: ${err.message}`);
      process.exit(1);
    }
    throw err;
  }
  process.stdout.write('\n');

  const creds: StoredCredentials = {
    appUrl: cfg.appUrl,
    accessToken: token.access_token,
    tokenType: token.token_type,
    refreshToken: token.refresh_token,
    scope: token.scope,
    expiresAt: token.expires_in ? Date.now() + token.expires_in * 1000 : undefined,
    obtainedAt: Date.now(),
  };
  saveCredentials(cfg.credentialsPath, creds);
  console.log(`✓ Angemeldet. Zugangsdaten gespeichert: ${cfg.credentialsPath}`);
}

main().catch((err: unknown) => {
  console.error(err instanceof Error ? err.message : String(err));
  process.exit(1);
});
