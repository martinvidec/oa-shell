// Stub-App (Spike, Issue #1) — steht stellvertretend für die spätere Spring-Boot-App.
// - WS-Server /bridge: nimmt die AUSGEHENDE Verbindung des Channels entgegen (Token-Gate).
// - HTTP-Steuerung (127.0.0.1:8798): Chat injizieren, Datei-Baum/Inhalt anfragen, Verdict-Policy.
// - Permission-Relay: beantwortet permission_request automatisch gemäß Policy (default allow) und loggt.
import { WebSocketServer } from 'ws'
import http from 'node:http'

const WS_PORT = 8799
const HTTP_PORT = 8798
const TOKEN = process.env.OASHELL_TOKEN || 'dev-token'
const log = (...a) => console.log('[stub]', ...a)

let channel = null
let verdictPolicy = 'allow'            // 'allow' | 'deny'
const pending = new Map()              // requestId -> resolve (für file_*)
let reqSeq = 0
const events = []                      // Protokoll aller Channel-Nachrichten

const wss = new WebSocketServer({ port: WS_PORT, path: '/bridge' })
wss.on('connection', (ws, req) => {
  if (req.headers['x-oashell-token'] !== TOKEN) {
    log('WS abgelehnt: ungültiges Token'); ws.close(); return
  }
  channel = ws
  log('Channel verbunden (Token ok)')
  ws.on('message', (data) => {
    let m; try { m = JSON.parse(data.toString()) } catch { return }
    events.push(m)
    if (m.type === 'hello') {
      log('hello:', m.cwdBasename, '(', m.cwd, ')')
    } else if (m.type === 'reply') {
      log('REPLY[' + m.chat_id + ']:', m.text)
    } else if (m.type === 'permission_request') {
      log('PERMISSION_REQUEST:', m.tool_name, '|', m.description, '| id=' + m.request_id)
      log('  -> Auto-Verdict:', verdictPolicy)
      ws.send(JSON.stringify({ type: 'permission_verdict', request_id: m.request_id, behavior: verdictPolicy }))
    } else if (m.type === 'file_tree_result' || m.type === 'file_content_result') {
      const r = pending.get(m.requestId); if (r) { pending.delete(m.requestId); r(m) }
    }
  })
  ws.on('close', () => { log('Channel getrennt'); if (channel === ws) channel = null })
})
log('WS-Bridge auf ws://127.0.0.1:' + WS_PORT + '/bridge')

function ask(type, payload) {
  return new Promise((resolve, reject) => {
    if (!channel) return reject(new Error('kein Channel verbunden'))
    const requestId = 'r' + (++reqSeq)
    pending.set(requestId, resolve)
    channel.send(JSON.stringify({ type, requestId, ...payload }))
    setTimeout(() => { if (pending.has(requestId)) { pending.delete(requestId); reject(new Error('timeout')) } }, 8000)
  })
}
const body = (req) => new Promise(r => { let b = ''; req.on('data', c => b += c); req.on('end', () => r(b)) })

http.createServer(async (req, res) => {
  const u = new URL(req.url, 'http://x')
  const json = (o, code = 200) => { res.writeHead(code, { 'content-type': 'application/json' }); res.end(JSON.stringify(o)) }
  try {
    if (u.pathname === '/status') return json({ channelConnected: !!channel, verdictPolicy, events })
    if (u.pathname === '/chat' && req.method === 'POST') {
      const { text, chat_id = '1' } = JSON.parse(await body(req) || '{}')
      if (!channel) return json({ error: 'kein Channel' }, 409)
      channel.send(JSON.stringify({ type: 'chat', text, chat_id })); return json({ ok: true })
    }
    if (u.pathname === '/verdict' && req.method === 'POST') {
      verdictPolicy = (JSON.parse(await body(req) || '{}').behavior === 'deny') ? 'deny' : 'allow'
      return json({ verdictPolicy })
    }
    if (u.pathname === '/tree') return json(await ask('file_tree', { path: u.searchParams.get('path') || '.' }))
    if (u.pathname === '/content') return json(await ask('file_content', { path: u.searchParams.get('path') || '' }))
    json({ error: 'unknown' }, 404)
  } catch (e) { json({ error: e.message }, 500) }
}).listen(HTTP_PORT, '127.0.0.1', () => log('HTTP-Steuerung auf http://127.0.0.1:' + HTTP_PORT))
