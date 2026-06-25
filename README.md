# oa-shell

Multi-User **Chat-Oberfläche für lokale Claude-Code-Sessions** über das
[Channels-Feature](https://code.claude.com/docs/en/channels-reference) von Claude Code –
nach dem Vorbild des offiziellen Telegram-Plugins.

Du startest `claude` lokal selbst (mit dem `oa-shell`-Channel); die Web-App ist nur die
**Oberfläche**: Login, Chat, Tool-Freigaben und ein Datei-Browser des Session-Arbeits­
verzeichnisses. Die App hostet **keine** Sessions, verwaltet **keine** Verzeichnisse und
speichert **keine** Claude-API-Keys.

```
[ deine Maschine ]                                  [ Server: oa-shell ]
 claude (interaktiv, dein Verzeichnis)               Spring-Boot-Web-App
   │ stdio (MCP)                                       (Login, Chat, Freigaben, Datei-Browser)
 oa-shell-Channel ──── ausgehende WSS (Bearer) ────►  /bridge
 Browser ──── Login / Chat / Freigabe / Dateien ────►  Web-UI
```

## Voraussetzungen

**Server (Web-App):** JDK 21, Maven ≥ 3.6.3, ein Google-OAuth-Client.
**Deine Maschine (Channel):** Node ≥ 18 und `claude` ≥ 2.1.81 mit für deinen Account
freigeschaltetem **Channels-Research-Preview** (Auth über claude.ai-Login *oder* einen
Anthropic-Console-API-Key; nicht auf Bedrock/Vertex/Foundry).

---

## 1. Server starten (Web-App)

### Google-OAuth einrichten

In der [Google Cloud Console](https://console.cloud.google.com/apis/credentials) einen
OAuth-2.0-Client (Typ „Web") anlegen:

- **Autorisierte Redirect-URI:** `http://localhost:8080/login/oauth2/code/google`
  (bzw. `https://DEINE-DOMAIN/login/oauth2/code/google` in Produktion)
- Client-ID und Secret kopieren.

### Starten

```bash
cd app
export JAVA_HOME="$(brew --prefix openjdk@21)"   # macOS; sonst auf ein JDK 21 zeigen
export GOOGLE_CLIENT_ID="…"
export GOOGLE_CLIENT_SECRET="…"

mvn spring-boot:run                              # Entwicklung
# ODER als Jar:
mvn -DskipTests package
java -jar target/oa-shell-app-0.0.1-SNAPSHOT.jar
```

Die App läuft auf **http://localhost:8080** (Port mit `--server.port=9090` überschreibbar,
falls 8080 belegt ist). Öffne die Seite und melde dich mit **„Mit Google anmelden"** an.

> **Produktion:** Hinter einem TLS-terminierenden Proxy betreiben (https/**wss**); die App
> übernimmt das Original-Schema aus den Forwarded-Headern. Details: [docs/06](docs/06-haertung-claude-chat.md).
> Ohne echte Google-Credentials startet die App trotzdem (Dummy-Defaults), aber der Login funktioniert dann nicht.

---

## 2. Channel einrichten (deine Maschine)

### Bauen

```bash
cd channel
npm ci
npm run build
npm link            # optional: macht `oa-shell` und `oa-shell-channel` global verfügbar
```

### Anmelden (Device Grant)

Die App-URL setzen (Default ist `http://127.0.0.1:8080`), dann einloggen:

```bash
export OASHELL_APP_URL="http://localhost:8080"   # bzw. deine Server-URL
oa-shell login        # ohne npm link: node dist/login.js
```

`oa-shell login` zeigt eine URL + einen Code. Im Browser (wo du angemeldet bist)
bestätigen → das kontogebundene Token wird unter `~/.oa-shell/credentials.json` (Rechte
`0600`) gespeichert. Es ist zugleich die Authentifizierung deiner Session.

### Channel an eine Claude-Session hängen

In dem **Verzeichnis**, in dem Claude arbeiten soll, eine `.mcp.json` anlegen:

```json
{
  "mcpServers": {
    "oashell": {
      "command": "oa-shell-channel",
      "env": { "OASHELL_APP_URL": "http://localhost:8080" }
    }
  }
}
```

> Ohne `npm link`: `"command": "node", "args": ["/ABS/PFAD/oa-shell/channel/dist/server.js"]`.

Dann Claude mit angehängtem Channel starten (im selben Verzeichnis):

```bash
claude --dangerously-load-development-channels server:oashell
```

Beim ersten Start einmalig den **Ordner-Trust-Dialog** im Terminal bestätigen. (Eigene
Channels sind in der Preview nicht auf der Allowlist – daher das Development-Flag.)

---

## 3. Benutzen

Im Browser erscheint deine Session in der Seitenleiste. Auswählen → chatten. Will Claude
ein Tool nutzen (z. B. `Write`, `Bash`), erscheint ein **Freigabedialog** (Erlauben/Ablehnen,
optional mit Begründung). Der **Datei-Browser** rechts zeigt das Arbeitsverzeichnis (read-only).
Sessions lassen sich umbenennen und trennen (Trennen widerruft das Token). Mehrere Sessions
parallel sind möglich; jede hat ihren eigenen Chat-/Datei-Kontext.

---

## Lokaler Durchlauf ohne Google (Profil `e2e`)

Zum schnellen lokalen Ausprobieren — und als Basis der E2E-Tests — gibt es ein
**Dev-Profil `e2e`**, das Google-Login und Device-Grant durch zwei einfache Endpunkte
ersetzt. So lässt sich der echte Pfad (echte `claude`-Session + echter Channel + App)
ohne Google durchspielen. **Nur lokal** — siehe Warnung unten.

App mit dem Profil starten:

```bash
cd app
JAVA_HOME="$(brew --prefix openjdk@21)" \
  java -jar target/oa-shell-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=e2e
```

**Browser ohne Google anmelden:** `http://localhost:8080/e2e/login?user=demo` öffnen
(setzt die Session-Cookie), dann auf `/` gehen.

**Channel ohne Device-Grant verbinden** — Token holen und als Credentials ablegen
(gleicher `user` wie beim Login, damit Browser und Channel demselben Konto gehören):

```bash
TOKEN=$(curl -s "http://localhost:8080/e2e/token?user=demo")
printf '{"appUrl":"http://localhost:8080","accessToken":"%s","tokenType":"Bearer","obtainedAt":0}' \
  "$TOKEN" > /tmp/oashell-e2e.json
```

Dann in der `.mcp.json` (aus Schritt 2) `OASHELL_CREDENTIALS` auf `/tmp/oashell-e2e.json`
zeigen lassen und `claude --dangerously-load-development-channels server:oashell` starten.
Die Session erscheint im Browser; Chat, Freigaben und Datei-Browser funktionieren wie im
echten Betrieb.

> ⚠️ **Nur für lokale Tests.** Unter Profil `e2e` kann sich jeder, der die App erreicht,
> als beliebiger Nutzer anmelden bzw. ein Channel-Token minten
> (`E2eLoginController` / `E2eTokenController`). Das Profil **niemals in Produktion**
> aktivieren.

---

## Build & Test

```bash
# Web-App: Unit- (Surefire) + E2E-Tests (Playwright/Failsafe)
cd app && JAVA_HOME="$(brew --prefix openjdk@21)" mvn verify
#   einzelner Unit-Test:  mvn -ntp -Dtest=PermissionServiceTest test
#   einzelner E2E-Test:   mvn -ntp -Dit.test=ChatFlowIT verify
#   E2E sichtbar/headed:  mvn -ntp -Dplaywright.headless=false verify

# Channel: TypeScript-Build + Tests
cd channel && npm ci && npm test
```

CI (`.github/workflows/ci.yml`) führt beides bei jedem Push aus.

## Repo-Struktur

| Pfad | Inhalt |
|---|---|
| `app/` | Spring-Boot-Web-App (Maven, JDK 21), Thymeleaf + Vanilla JS |
| `channel/` | Node/TypeScript-Channel-Paket (`oa-shell login` + Channel-Server) |
| `spike/` | Validierter End-to-End-Durchstich (Referenz/Wegwerf) |
| `docs/` | Konzept · Ist-Analyse · Anforderungen · Spezifikation · Spike-Report · Härtung |

## Dokumentation

1. [Konzept](docs/01-konzept-claude-chat.md)
2. [Ist-Analyse](docs/02-ist-analyse-claude-chat.md)
3. [Anforderungsanalyse](docs/03-anforderungsanalyse-claude-chat.md)
4. [Spezifikation](docs/04-spezifikation-claude-chat.md)
5. [Spike-Report](docs/05-spike-report-claude-chat.md)
6. [Härtung & Security-Review](docs/06-haertung-claude-chat.md)

## Sicherheit & Hinweise

- **Öffentliches Repo:** keine Secrets/Keys/Tokens einchecken ([`.gitignore`](.gitignore)
  deckt sie ab). Google-Credentials kommen ausschließlich aus der Umgebung.
- **Research Preview:** Das Channels-Feature von Claude Code ist Vorschau – Flag/Protokoll
  können sich ändern. Der Channel-Vertrag ist in `channel/src/protocol.ts` gekapselt.
- **Bekannte Folge-Schritte** (siehe [docs/06](docs/06-haertung-claude-chat.md)): persistente +
  verschlüsselte Token-Speicherung (aktuell in-memory), OS-Sandboxing der Channel-Prozesse,
  echtes Token-Streaming (Antworten kommen derzeit als ganze Nachrichten).
```
