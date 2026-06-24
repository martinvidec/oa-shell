import { test } from 'node:test';
import assert from 'node:assert/strict';
import { WebSocketServer } from 'ws';
import { BridgeClient } from '../dist/bridge-client.js';

function listening(wss) {
  return new Promise((resolve) => wss.on('listening', () => resolve(wss.address().port)));
}

async function waitFor(predicate, timeout = 2000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    if (predicate()) return;
    await new Promise((r) => setTimeout(r, 25));
  }
  throw new Error('Bedingung nicht erfüllt (Timeout)');
}

test('BridgeClient: Bearer-Token im Handshake, hello senden, Nachricht empfangen', async () => {
  const wss = new WebSocketServer({ port: 0 });
  const port = await listening(wss);

  let authHeader = null;
  const serverGot = [];
  let serverSocket = null;
  wss.on('connection', (ws, req) => {
    authHeader = req.headers['authorization'];
    serverSocket = ws;
    ws.on('message', (d) => serverGot.push(JSON.parse(d.toString())));
  });

  const clientGot = [];
  const client = new BridgeClient({
    wsUrl: `ws://127.0.0.1:${port}`,
    token: 'tok-123',
    onOpen: () => client.send({ type: 'hello', cwd: '/p/proj', cwdBasename: 'proj', channelVersion: '0.0.1' }),
    onMessage: (m) => clientGot.push(m),
  });
  client.start();

  await waitFor(() => serverGot.length >= 1);
  assert.equal(authHeader, 'Bearer tok-123', 'Bearer-Token muss im Handshake-Header stehen');
  assert.equal(serverGot[0].type, 'hello');
  assert.equal(serverGot[0].cwdBasename, 'proj');

  serverSocket.send(JSON.stringify({ type: 'chat', text: 'hallo' }));
  await waitFor(() => clientGot.length >= 1);
  assert.equal(clientGot[0].type, 'chat');
  assert.equal(clientGot[0].text, 'hallo');

  client.stop();
  await new Promise((r) => wss.close(r));
});

test('BridgeClient: reconnectet nach Verbindungsabbruch', async () => {
  const wss = new WebSocketServer({ port: 0 });
  const port = await listening(wss);

  let connections = 0;
  let firstSocket = null;
  wss.on('connection', (ws) => {
    connections += 1;
    if (connections === 1) firstSocket = ws;
  });

  const client = new BridgeClient({
    wsUrl: `ws://127.0.0.1:${port}`,
    token: 't',
    onMessage: () => {},
    reconnectMs: 50,
  });
  client.start();

  await waitFor(() => connections >= 1);
  firstSocket.close();
  await waitFor(() => connections >= 2, 3000);
  assert.ok(connections >= 2, 'Client muss nach Abbruch neu verbinden');

  client.stop();
  await new Promise((r) => wss.close(r));
});
