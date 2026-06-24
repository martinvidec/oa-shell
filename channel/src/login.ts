#!/usr/bin/env node
import { loadConfig } from './config.js';

/**
 * `oa-shell login` — Device-Grant-Login gegen die App (Skeleton).
 *
 * Geplant (Issue #6): OAuth 2.0 Device Authorization Grant (RFC 8628) ausführen,
 * `user_code` + `verification_uri` anzeigen, Token pollen und unter
 * {@link OaShellConfig.credentialsPath} (0600) speichern. Das Token ist zugleich
 * die Session-Authentifizierung des Channels.
 */
async function main(): Promise<void> {
  const cfg = loadConfig();
  console.log('oa-shell login (Skeleton)');
  console.log('  App-URL:        ', cfg.appUrl);
  console.log('  Credentials ->  ', cfg.credentialsPath);
  console.error('TODO: Device Authorization Grant noch nicht implementiert (Issue #6).');
}

main().catch((err: unknown) => {
  console.error(err);
  process.exit(1);
});
