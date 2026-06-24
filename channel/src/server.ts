#!/usr/bin/env node
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { z } from 'zod';
import path from 'node:path';
import { loadConfig } from './config.js';
import { loadCredentials } from './credentials.js';
import { BridgeClient } from './bridge-client.js';
import { CHANNEL_NOTIFICATION, PERMISSION_REQUEST, PERMISSION_VERDICT } from './protocol.js';

const VERSION = '0.0.1';
const CWD = process.cwd();
const log = (msg: string): void => void process.stderr.write(`[oashell-channel] ${msg}\n`);

/**
 * oa-shell Channel-Server — von Claude Code via stdio gestartet. Verbindet sich
 * ausgehend und token-authentifiziert mit der App-Bridge, sendet hello und
 * vermittelt Chat (Push), reply-Tool und Permission-Relay. File-Serving folgt in #13.
 */
async function main(): Promise<void> {
  const cfg = loadConfig();
  const creds = loadCredentials(cfg.credentialsPath);

  const mcp = new Server(
    { name: 'oashell', version: VERSION },
    {
      capabilities: {
        experimental: { 'claude/channel': {}, 'claude/channel/permission': {} },
        tools: {},
      },
      instructions:
        'Nachrichten kommen als <channel source="oashell" chat_id="..."> an. ' +
        'Antworte ausschließlich über das reply-Tool und gib die chat_id aus dem Tag zurück.',
    },
  );

  let bridge: BridgeClient | null = null;

  // reply-Tool: Claude antwortet dem Nutzer -> an die App
  mcp.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: [
      {
        name: 'reply',
        description: 'Sende eine Nachricht über diesen Channel an den Nutzer zurück.',
        inputSchema: {
          type: 'object',
          properties: {
            chat_id: { type: 'string', description: 'Die Unterhaltung, in der geantwortet wird' },
            text: { type: 'string', description: 'Der Nachrichtentext' },
          },
          required: ['chat_id', 'text'],
        },
      },
    ],
  }));
  mcp.setRequestHandler(CallToolRequestSchema, async (req) => {
    if (req.params.name === 'reply') {
      const { chat_id, text } = req.params.arguments as { chat_id: string; text: string };
      bridge?.send({ type: 'reply', chat_id, text });
      return { content: [{ type: 'text', text: 'sent' }] };
    }
    throw new Error(`unbekanntes Tool: ${req.params.name}`);
  });

  // Permission-Relay: Claude Code -> Channel -> App
  const PermissionRequest = z.object({
    method: z.literal(PERMISSION_REQUEST),
    params: z.object({
      request_id: z.string(),
      tool_name: z.string(),
      description: z.string(),
      input_preview: z.string(),
    }),
  });
  mcp.setNotificationHandler(PermissionRequest, async ({ params }) => {
    bridge?.send({ type: 'permission_request', ...params });
  });

  await mcp.connect(new StdioServerTransport());
  log(`MCP verbunden (stdio). App=${cfg.appUrl} cwd=${CWD}`);

  if (!creds) {
    log('Kein Token gefunden — bitte zuerst "oa-shell login" ausführen.');
    return;
  }

  bridge = new BridgeClient({
    wsUrl: cfg.wsUrl,
    token: creds.accessToken,
    log,
    onOpen: () =>
      bridge?.send({ type: 'hello', cwd: CWD, cwdBasename: path.basename(CWD), channelVersion: VERSION }),
    onMessage: async (msg) => {
      if (msg.type === 'chat') {
        await mcp.notification({
          method: CHANNEL_NOTIFICATION,
          params: { content: msg.text, meta: { chat_id: String(msg.chat_id ?? '1') } },
        });
      } else if (msg.type === 'permission_verdict') {
        await mcp.notification({
          method: PERMISSION_VERDICT,
          params: { request_id: msg.request_id, behavior: msg.behavior },
        });
      } else if (msg.type === 'file_tree') {
        // TODO (#13): File-Serving (Baum unter cwd, Pfad-Sandbox)
        bridge?.send({ type: 'file_tree_result', requestId: msg.requestId, error: 'not_implemented' });
      } else if (msg.type === 'file_content') {
        // TODO (#13): File-Serving (Inhalt unter cwd, Pfad-Sandbox)
        bridge?.send({ type: 'file_content_result', requestId: msg.requestId, error: 'not_implemented' });
      }
    },
  });
  bridge.start();
  log(`Bridge-Verbindung zu ${cfg.wsUrl} gestartet.`);
}

main().catch((err: unknown) => {
  process.stderr.write(String(err) + '\n');
  process.exit(1);
});
