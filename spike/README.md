# Spike (Issue #1): Channel↔App-Durchstich

Minimaler, lauffähiger Nachweis des Architektur-Kernstücks aus der
[Spezifikation](../docs/04-spezifikation-claude-chat.md). Ergebnis und Erkenntnisse:
[Spike-Report](../docs/05-spike-report-claude-chat.md). **Status: GO.**

> Dies ist Spike-/Wegwerf-Code zum Validieren – nicht die spätere Produktions-App.
> Das Token `dev-token` ist ein lokaler Platzhalter, kein Secret.

## Inhalt

- `oashell-channel.mjs` – minimaler MCP-Channel: ausgehende WS, `reply`-Tool,
  Permission-Relay, File-Serving (strikt unter `cwd`), Sender-Gating (Token).
- `stub-server.mjs` – steht für die spätere App: WS-Gegenstelle + HTTP-Steuerung
  (Chat injizieren, Datei-Baum/Inhalt, Auto-Freigabe-Policy).
- `test-bridge.mjs` – deterministischer Test (ohne Claude) für WS + File-Serving + Sandbox.

## Setup

```bash
cd spike && npm install
```

## 1) Deterministischer Test (ohne Claude)

```bash
npm run test:bridge      # erwartet: 7/7 grün
```

## 2) Interaktiver Durchstich (mit echter Claude-Session)

Voraussetzung: `claude` interaktiv nutzbar, Account mit Channels-Preview.

```bash
# Terminal A: Stub-App starten
npm run stub

# Terminal B: in einem Arbeitsverzeichnis claude mit dem Channel starten
cd /pfad/zum/arbeitsverzeichnis
OASHELL_URL=ws://127.0.0.1:8799/bridge OASHELL_TOKEN=dev-token \
  claude --mcp-config <(echo '{"mcpServers":{"oashell":{"command":"node","args":["/ABS/PFAD/spike/oashell-channel.mjs"],"env":{"OASHELL_URL":"ws://127.0.0.1:8799/bridge","OASHELL_TOKEN":"dev-token"}}}}') \
         --strict-mcp-config --dangerously-load-development-channels server:oashell
# Ordner-Trust-Dialog mit Enter bestätigen.

# Terminal C: Chat injizieren / Dateien abfragen
curl -s localhost:8798/status
curl -s -XPOST localhost:8798/chat -d '{"text":"Create hello.txt with the text hi via Write, then reply done.","chat_id":"1"}' -H content-type:application/json
curl -s 'localhost:8798/tree?path=.'
curl -s -XPOST localhost:8798/verdict -d '{"behavior":"deny"}' -H content-type:application/json   # Freigabe-Policy umstellen
```

Erwartet im Stub-Log: `Channel verbunden` → `PERMISSION_REQUEST: Write → allow` →
`REPLY[1]: done`; die Datei erscheint im Arbeitsverzeichnis.
