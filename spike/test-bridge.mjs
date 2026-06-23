// Deterministischer Test (ohne Claude): validiert WS-Bridge + File-Serving + cwd-Sandbox.
// Startet eine eingebettete WS-"App", spawnt den Channel mit cwd=Fixture und prüft Antworten.
import { WebSocketServer } from 'ws'
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const PORT = 8801
const TOKEN = 'test-token'

// Fixture-Verzeichnis mit Inhalt anlegen
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'oashell-fixture-'))
fs.writeFileSync(path.join(tmp, 'hello.txt'), 'hi')
fs.mkdirSync(path.join(tmp, 'sub'))
fs.writeFileSync(path.join(tmp, 'sub', 'inner.txt'), 'nested')
fs.writeFileSync(path.join(os.tmpdir(), 'OUTSIDE_SECRET.txt'), 'should-not-be-readable')

let results = []
const check = (name, ok, extra = '') => { results.push({ name, ok }); console.log(`${ok ? '✅' : '❌'} ${name}${extra ? ' — ' + extra : ''}`) }

const wss = new WebSocketServer({ port: PORT, path: '/bridge' })
let channel = null
const pending = new Map(); let seq = 0
const ask = (type, payload) => new Promise((resolve, reject) => {
  const requestId = 'q' + (++seq); pending.set(requestId, resolve)
  channel.send(JSON.stringify({ type, requestId, ...payload }))
  setTimeout(() => { if (pending.has(requestId)) { pending.delete(requestId); reject(new Error('timeout')) } }, 6000)
})
const helloP = new Promise((resolve) => {
  wss.on('connection', (ws, req) => {
    if (req.headers['x-oashell-token'] !== TOKEN) { check('Token-Gate weist falsches Token ab', false, 'falsch verbunden'); ws.close(); return }
    channel = ws
    ws.on('message', (d) => {
      const m = JSON.parse(d.toString())
      if (m.type === 'hello') resolve(m)
      else if (m.requestId && pending.has(m.requestId)) { const r = pending.get(m.requestId); pending.delete(m.requestId); r(m) }
    })
  })
})

const child = spawn('node', [path.join(here, 'oashell-channel.mjs')], {
  cwd: tmp,
  env: { ...process.env, OASHELL_URL: `ws://127.0.0.1:${PORT}/bridge`, OASHELL_TOKEN: TOKEN },
  stdio: ['pipe', 'ignore', 'pipe'],   // stdin offen halten (MCP-Transport), stderr fürs Debug
})
child.stderr.on('data', () => {})       // verschlucken; bei Bedarf zum Debuggen ausgeben

async function run() {
  const hello = await Promise.race([helloP, new Promise((_, r) => setTimeout(() => r(new Error('kein hello')), 8000))])
  check('Channel verbindet sich ausgehend + Token-Gate ok (hello)', !!hello && hello.cwdBasename === path.basename(tmp), 'cwd=' + hello.cwdBasename)

  const tree = await ask('file_tree', { path: '.' })
  const names = (tree.entries || []).map(e => e.name).sort()
  check('file_tree liefert Inhalt unter cwd', JSON.stringify(names) === JSON.stringify(['hello.txt', 'sub']), names.join(','))

  const sub = await ask('file_tree', { path: 'sub' })
  check('file_tree für Unterordner', (sub.entries || []).some(e => e.name === 'inner.txt'))

  const content = await ask('file_content', { path: 'hello.txt' })
  check('file_content liest Datei', content.content === 'hi', JSON.stringify(content).slice(0, 60))

  const up = await ask('file_tree', { path: '../' })
  check('Sandbox: ".." wird abgewiesen', !!up.error, up.error || 'KEIN Fehler!')

  const abs = await ask('file_content', { path: path.join(os.tmpdir(), 'OUTSIDE_SECRET.txt') })
  check('Sandbox: absoluter Pfad außerhalb wird abgewiesen', !!abs.error && abs.content === undefined, abs.error || 'LECK!')

  const verdictRoundtrip = await ask('file_content', { path: 'hello.txt' }).then(() => true).catch(() => false)
  check('WS-Roundtrip stabil (mehrere Anfragen)', verdictRoundtrip)
}

run().catch(e => check('Testlauf', false, e.message)).finally(() => {
  setTimeout(() => {
    try { child.kill('SIGKILL') } catch {}
    try { fs.rmSync(tmp, { recursive: true, force: true }); fs.rmSync(path.join(os.tmpdir(), 'OUTSIDE_SECRET.txt'), { force: true }) } catch {}
    const failed = results.filter(r => !r.ok).length
    console.log(`\n${failed === 0 ? '✅ ALLE TESTS BESTANDEN' : '❌ ' + failed + ' Test(s) fehlgeschlagen'} (${results.length} gesamt)`)
    process.exit(failed === 0 ? 0 : 1)
  }, 300)
})
