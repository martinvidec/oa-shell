#!/usr/bin/env node
import { loadConfig } from './config.js';
import { saveCredentials, type StoredCredentials } from './credentials.js';
import { DeviceFlowError, pollForToken, requestDeviceAuthorization } from './device-flow.js';

const CLIENT_ID = 'oa-shell-channel';
const SCOPE = 'session files';

/**
 * `oa-shell login` — führt den OAuth 2.0 Device Authorization Grant gegen die App
 * durch und speichert das kontogebundene Token lokal (0600). Der Token wird nie
 * ausgegeben.
 */
async function main(): Promise<void> {
  const cfg = loadConfig();
  console.log(`oa-shell login → ${cfg.appUrl}`);

  const auth = await requestDeviceAuthorization(cfg.appUrl, CLIENT_ID, SCOPE);
  const url = auth.verification_uri_complete ?? auth.verification_uri;
  console.log('\nZum Anmelden im Browser öffnen:');
  console.log(`  ${url}`);
  console.log(`\nFalls nach einem Code gefragt wird:  ${auth.user_code}\n`);
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
