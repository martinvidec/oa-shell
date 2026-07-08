# oa-shell – Claude-Code-Plugin

Self-contained Plugin-Paket, das den oa-shell-Channel als Claude-Code-Plugin bereitstellt
(Marketplace-Manifest liegt im Repo-Root unter `.claude-plugin/marketplace.json`).

## Inhalt

- `.claude-plugin/plugin.json` — Manifest: bindet den channel-fähigen MCP-Server (`oashell`)
  und fragt beim Aktivieren die App-URL ab (`userConfig.app_url` → `OASHELL_APP_URL`).
- `commands/login.md` — Slash-Command `/oa-shell:login` (Device-Grant-Login).
- `server/server.cjs`, `server/login.cjs` — **generierte** Single-File-Bundles des Channels
  (Deps inline, kein `node_modules` nötig).

## Bundles neu erzeugen

Die Dateien unter `server/` werden aus `../channel/src` gebündelt und sind eingecheckt,
damit das Plugin ohne Build-Schritt (auch von GitHub) installierbar ist. Nach Änderungen
am Channel-Code neu bauen:

```bash
cd ../channel
npm ci
npm run bundle      # esbuild → ../plugin/server/*.cjs
```

## Installieren / nutzen

Siehe Repo-`README.md`, Abschnitt „2. Channel einrichten → Variante A (Plugin)".
