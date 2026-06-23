// oa-shell Channel (Spike, Issue #1)
// Minimaler MCP-Channel-Server für Claude Code, der sich AUSGEHEND mit der App
// (hier: Stub-Server) verbindet und implementiert:
//   - claude/channel              : Nachrichten der App in die Session pushen
//   - reply-Tool                  : Claudes Antworten an die App
//   - claude/channel/permission   : Permission-Relay (request -> App -> verdict)
//   - File-Serving                : Baum/Inhalt STRIKT unter cwd
//   - Sender-Gating               : Token beim WS-Aufbau
//
// Start durch Claude Code via stdio (MCP). Konfig per Env:
//   OASHELL_URL   (default ws://127.0.0.1:8799/bridge)
//   OASHELL_TOKEN (default "dev-token")
import { Server } from '@modelcontextprotocol/sdk/server/index.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import { ListToolsRequestSchema, CallToolRequestSchema } from '@modelcontextprotocol/sdk/types.js'
import { z } from 'zod'
import { WebSocket } from 'ws'
import path from 'node:path'
import fs from 'node:fs'

const URL = process.env.OASHELL_URL || 'ws://127.0.0.1:8799/bridge'
const TOKEN = process.env.OASHELL_TOKEN || 'dev-token'
const BASE = fs.realpathSync(process.cwd())
const log = (...a) => process.stderr.write('[oashell-channel] ' + a.join(' ') + '\n')

// --- File-Serving, strikt auf BASE (cwd) begrenzt --------------------------
function safeResolve(rel) {
  const target = path.resolve(BASE, rel && rel !== '/' ? rel.replace(/^\/+/, '') : '.')
  let real = target
  try { real = fs.realpathSync(target) } catch { /* darf (noch) nicht existieren */ }
  if (real !== BASE && !real.startsWith(BASE + path.sep)) {
    throw new Error('Pfad außerhalb des Arbeitsverzeichnisses')
  }
  return target
}
function fileTree(rel) {
  const dir = safeResolve(rel)
  return fs.readdirSync(dir, { withFileTypes: true }).map(d => ({
    name: d.name,
    type: d.isDirectory() ? 'dir' : 'file',
    size: d.isFile() ? (() => { try { return fs.statSync(path.join(dir, d.name)).size } catch { return null } })() : null,
  }))
}
function fileContent(rel) {
  const f = safeResolve(rel)
  const st = fs.statSync(f)
  if (st.size > 200_000) return { truncated: true, size: st.size }
  return { content: fs.readFileSync(f, 'utf8'), size: st.size }
}

// --- MCP-Server (von Claude Code via stdio gestartet) ----------------------
const mcp = new Server(
  { name: 'oashell', version: '0.0.1' },
  {
    capabilities: {
      experimental: { 'claude/channel': {}, 'claude/channel/permission': {} },
      tools: {},
    },
    instructions:
      'Nachrichten kommen als <channel source="oashell" chat_id="..."> an. ' +
      'Antworte dem Nutzer ausschließlich über das reply-Tool und gib die chat_id aus dem Tag zurück.',
  },
)

let sock = null
const wsSend = (obj) => { if (sock && sock.readyState === WebSocket.OPEN) sock.send(JSON.stringify(obj)) }

// reply-Tool: Claude ruft es auf, um dem Nutzer zu antworten -> an App
mcp.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [{
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
  }],
}))
mcp.setRequestHandler(CallToolRequestSchema, async (req) => {
  if (req.params.name === 'reply') {
    const { chat_id, text } = req.params.arguments
    wsSend({ type: 'reply', chat_id, text })
    log('reply ->', JSON.stringify(text).slice(0, 80))
    return { content: [{ type: 'text', text: 'sent' }] }
  }
  throw new Error('unknown tool: ' + req.params.name)
})

// Permission-Relay: Claude Code -> Channel -> App
const PermReq = z.object({
  method: z.literal('notifications/claude/channel/permission_request'),
  params: z.object({
    request_id: z.string(),
    tool_name: z.string(),
    description: z.string(),
    input_preview: z.string(),
  }),
})
mcp.setNotificationHandler(PermReq, async ({ params }) => {
  log('permission_request', params.tool_name, params.request_id)
  wsSend({ type: 'permission_request', ...params })
})

await mcp.connect(new StdioServerTransport())
log('MCP verbunden (stdio). cwd =', BASE)

// --- Ausgehende WS zur App (Stub) ------------------------------------------
function connect() {
  log('verbinde WS ->', URL)
  const ws = new WebSocket(URL, { headers: { 'x-oashell-token': TOKEN } })
  ws.on('open', () => {
    sock = ws
    log('WS offen')
    wsSend({ type: 'hello', cwd: BASE, cwdBasename: path.basename(BASE), channelVersion: '0.0.1' })
  })
  ws.on('message', async (data) => {
    let m; try { m = JSON.parse(data.toString()) } catch { return }
    try {
      if (m.type === 'chat') {
        // App -> Session: Nachricht in die laufende Claude-Session pushen
        await mcp.notification({
          method: 'notifications/claude/channel',
          params: { content: m.text, meta: { chat_id: String(m.chat_id ?? '1') } },
        })
        log('chat -> session:', JSON.stringify(m.text).slice(0, 80))
      } else if (m.type === 'permission_verdict') {
        await mcp.notification({
          method: 'notifications/claude/channel/permission',
          params: { request_id: m.request_id, behavior: m.behavior },
        })
        log('verdict -> session:', m.request_id, m.behavior)
      } else if (m.type === 'file_tree') {
        let res; try { res = { entries: fileTree(m.path) } } catch (e) { res = { error: e.message } }
        wsSend({ type: 'file_tree_result', requestId: m.requestId, ...res })
      } else if (m.type === 'file_content') {
        let res; try { res = fileContent(m.path) } catch (e) { res = { error: e.message } }
        wsSend({ type: 'file_content_result', requestId: m.requestId, ...res })
      }
    } catch (e) { log('handler-Fehler:', e.message) }
  })
  ws.on('close', () => { sock = null; log('WS zu, reconnect in 1.5s'); setTimeout(connect, 1500) })
  ws.on('error', (e) => log('WS-Fehler:', e.message))
}
connect()
