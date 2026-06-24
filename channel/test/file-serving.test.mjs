import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createFileServer, FileServingError } from '../dist/file-serving.js';

function fixture() {
  const dir = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'oashell-fs-')));
  fs.writeFileSync(path.join(dir, 'hello.txt'), 'hi');
  fs.mkdirSync(path.join(dir, 'sub'));
  fs.writeFileSync(path.join(dir, 'sub', 'inner.txt'), 'nested');
  fs.writeFileSync(path.join(dir, 'bin.dat'), Buffer.from([0x68, 0x00, 0x69])); // Null-Byte -> binär
  fs.writeFileSync(path.join(dir, 'big.txt'), 'x'.repeat(250_000));

  const outside = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'oashell-out-')));
  fs.writeFileSync(path.join(outside, 'secret.txt'), 'top-secret');
  let hasLink = false;
  try {
    fs.symlinkSync(path.join(outside, 'secret.txt'), path.join(dir, 'evil-link'));
    hasLink = true;
  } catch {
    /* Symlink ggf. nicht erstellbar */
  }
  return { dir, hasLink };
}

test('tree: listet Inhalt unter cwd', () => {
  const { dir } = fixture();
  const names = createFileServer(dir).tree('.').map((e) => e.name);
  assert.ok(names.includes('hello.txt'));
  assert.ok(names.includes('sub'));
});

test('content: liest Textdatei', () => {
  const { dir } = fixture();
  const r = createFileServer(dir).content('hello.txt');
  assert.equal(r.content, 'hi');
  assert.equal(r.size, 2);
});

test('Sandbox: ".." wird abgewiesen', () => {
  const { dir } = fixture();
  assert.throws(
    () => createFileServer(dir).tree('../'),
    (e) => e instanceof FileServingError && e.code === 'outside_cwd',
  );
});

test('Sandbox: absoluter Pfad außerhalb wird nicht geliefert', () => {
  const { dir } = fixture();
  assert.throws(
    () => createFileServer(dir).content('/etc/hostname'),
    (e) => e instanceof FileServingError,
  );
});

test('Sandbox: Symlink aus dem Verzeichnis heraus wird abgewiesen', () => {
  const { dir, hasLink } = fixture();
  if (!hasLink) return;
  assert.throws(
    () => createFileServer(dir).content('evil-link'),
    (e) => e instanceof FileServingError && e.code === 'outside_cwd',
  );
});

test('binäre Datei wird markiert', () => {
  const { dir } = fixture();
  assert.equal(createFileServer(dir).content('bin.dat').binary, true);
});

test('große Datei wird als truncated markiert', () => {
  const { dir } = fixture();
  const r = createFileServer(dir).content('big.txt');
  assert.equal(r.truncated, true);
  assert.ok(r.size > 200_000);
});
