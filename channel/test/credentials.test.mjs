import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { saveCredentials, loadCredentials, clearCredentials } from '../dist/credentials.js';

test('saveCredentials: round-trip, legt Verzeichnis an, Dateirechte 0600', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'oashell-cred-'));
  const file = path.join(dir, 'nested', 'credentials.json');
  const creds = {
    appUrl: 'http://app',
    accessToken: 'super-secret-token',
    tokenType: 'Bearer',
    obtainedAt: 1,
  };

  saveCredentials(file, creds);

  const mode = fs.statSync(file).mode & 0o777;
  assert.equal(mode, 0o600, 'Datei muss 0600 sein');
  assert.deepEqual(loadCredentials(file), creds);

  clearCredentials(file);
  assert.equal(loadCredentials(file), null);

  fs.rmSync(dir, { recursive: true, force: true });
});

test('loadCredentials: fehlende Datei -> null', () => {
  assert.equal(loadCredentials('/nonexistent/path/credentials.json'), null);
});
