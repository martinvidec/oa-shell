# oa-shell-channel

Produktives Channel-Paket (TypeScript) für die Nutzer-Maschine. Zwei Entrypoints:

- **`oa-shell`** (`dist/login.js`) — Device-Grant-Login gegen die App (Issue #6).
- **`oa-shell-channel`** (`dist/server.js`) — der Channel-MCP-Server, den Claude Code
  via stdio startet (Issues #8 = WS/Reply/Permission, #13 = File-Serving).

> Skelett. Der vollständige, bereits **validierte** Durchstich liegt in
> [`../spike`](../spike) und dient als Referenz-Implementierung.

## Entwicklung

```bash
npm install
npm run build      # tsc -> dist/
```

## Konfiguration (Env)

| Variable | Default | Zweck |
|---|---|---|
| `OASHELL_APP_URL` | `http://127.0.0.1:8080` | Basis-URL der App |
| `OASHELL_WS_URL` | aus App-URL abgeleitet (`…/bridge`) | Bridge-WebSocket |
| `OASHELL_CREDENTIALS` | `~/.oa-shell/credentials.json` | gespeichertes Device-Grant-Token |

## Start (später)

```bash
oa-shell login                                                   # einmalig
claude --dangerously-load-development-channels server:oashell    # Channel an die Session hängen
```
