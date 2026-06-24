#!/usr/bin/env node
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { loadConfig } from './config.js';

/**
 * oa-shell Channel-Server (Skeleton) — von Claude Code via stdio gestartet.
 *
 * Deklariert die Channel-Capabilities; die restliche Logik (ausgehende WS zur App
 * mit Bearer-Token, reply-Tool, Permission-Relay, File-Serving unter cwd, Sender-
 * Gating) folgt in den Issues #8 und #13. Validierter Durchstich: `spike/`.
 */
async function main(): Promise<void> {
  const cfg = loadConfig();

  const mcp = new Server(
    { name: 'oashell', version: '0.0.1' },
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

  await mcp.connect(new StdioServerTransport());
  process.stderr.write(`[oashell-channel] MCP verbunden (stdio). App=${cfg.appUrl}, cwd=${process.cwd()}\n`);

  // TODO (Issue #8): ausgehende WSS zur App-Bridge (Bearer aus credentials),
  //   reply-Tool, Permission-Relay (permission_request/permission), Sender-Gating.
  // TODO (Issue #13): File-Serving (Baum/Inhalt) strikt unter process.cwd().
}

main().catch((err: unknown) => {
  process.stderr.write(String(err) + '\n');
  process.exit(1);
});
