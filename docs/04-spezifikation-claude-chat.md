# Spezifikation: oa-shell – Chat-Oberfläche für lokale Claude-Code-Sessions über Channels

> Status: Entwurf zur Prüfung · Sprache: Deutsch · Bezug:
> [Konzept](01-konzept-claude-chat.md) · [Ist-Analyse](02-ist-analyse-claude-chat.md) ·
> [Anforderungsanalyse](03-anforderungsanalyse-claude-chat.md)

## 1. Übersicht

`oa-shell` besteht aus zwei Artefakten:

1. **Web-App** (Spring Boot, Maven, JDK 21) – „die Plattform": Authentifizierung,
   Chat-UI, Permission-Relay-UI, Datei-Browser, Bridge-Endpunkt für Channels. Sie
   ist zugleich **OAuth2 Authorization Server** (Spring Authorization Server) und
   föderiert die Identität an **Google**.
2. **`oa-shell` Channel-Paket** (Node) – läuft auf der **Nutzer-Maschine**: ein
   `oa-shell login`-CLI (Device-Grant-Login) und der **Channel-MCP-Server**, den der
   Nutzer an seine lokale `claude`-Session anhängt.

Die Web-App **hostet keine** Claude-Sessions und verwaltet **keine** Verzeichnisse/
API-Keys. `claude` läuft lokal beim Nutzer (interaktives Terminal, eigene Anmeldung);
der Channel verbindet sich **ausgehend** zur App. Diese Spezifikation setzt die
Anforderungen FA-01…FA-33 / NFA-01…NFA-16 um.

## 2. Technisches Design

### 2.1 Architektur

#### 2.1.1 Komponenten Web-App (Maven-Module/Packages)

| Modul | Inhalt |
|---|---|
| `auth` | Spring Authorization Server: Authorization-Code-(+PKCE)- und **Device-Grant-Endpoints**, Approval-Seite, Token-/Revoke-Endpoint; **Google-Föderation** via `oauth2Login`. |
| `bridge` | WebSocket-Endpunkt `/bridge` für Channels; Token-Auth beim Handshake; `SessionRegistry` (in-memory Live-Verbindungen). |
| `session` | `Session`-Verwaltung (1..n je Nutzer), Status, Benennung, Ownership-Prüfung. |
| `chat` | Browser-WebSocket `/ws`; Routing Browser ↔ gewählte Session; Streaming. |
| `permission` | Relay-Logik: `permission_request` ↔ Browser-Dialog ↔ `permission`-Verdikt, `request_id`-Matching je Session. |
| `files` | `FileViewService`: REST-Proxy, fragt Baum/Inhalt über die Bridge beim Channel an. |
| `web` | Thymeleaf-Controller + Vanilla-JS-Frontend (Login, Device-Approval, Session-Umschalter, Chat, Datei-Browser, Freigabe-Dialog). |
| `persistence` | JPA-Entities + SAS-Token-Store (verschlüsselt). |

#### 2.1.2 Komponenten Channel-Paket (Node)

| Teil | Inhalt |
|---|---|
| `oa-shell login` (CLI) | Führt **Device Authorization Grant** gegen die App aus, zeigt `user_code`+URL, pollt Token, speichert es lokal (z. B. `~/.oa-shell/credentials.json`, 0600). |
| Channel-MCP-Server | Von `claude` per stdio gestartet; lädt das Token; öffnet **ausgehende WSS** zur App-Bridge; implementiert `claude/channel`, `reply`, `claude/channel/permission`, **File-Serving** (unter `cwd`), **Sender-Gating**. |

#### 2.1.3 Topologie & Schlüsselflüsse

```
[ Nutzer-Maschine ]                                [ Server: Web-App ]
 oa-shell login ─ Device Grant ──────────────────►  /oauth2/device_authorization
   (Token → ~/.oa-shell)         ◄── user_code/poll  /oauth2/token  (+ Google-Login/Approval im Browser)

 claude (interaktiv) ── stdio ── oa-shell channel ── WSS(Bearer) ──► /bridge ── SessionRegistry
                                   (cwd = Session-Verzeichnis)                       │
 Browser ── Login(Code) / Chat / Freigabe / Dateien ── WS/REST ──► Web-App ──────────┘
```

**Login (zwei Wege):** Browser → Authorization Code (+PKCE) am App-AS (föderiert an
Google). Headless → `oa-shell login` (Device Grant); der Nutzer bestätigt den
`user_code` im Browser (dort via Google eingeloggt). Das Token ist **Login +
Session-Auth**.

**Chat:** Browser `/ws` → App → (Bridge) Channel pusht `notifications/claude/channel`
→ Claude → `reply`-Tool → Channel → App → Browser (Streaming).

**Freigabe:** Claude Code → `permission_request` → Channel → App → Browser-Dialog →
Verdikt → `permission` → Claude. (Der lokale Terminal-Dialog des Nutzers bleibt
parallel offen; erste Antwort gewinnt.)

**Dateien:** Browser → REST → App → (Bridge) Channel liest Baum/Inhalt **unter `cwd`**
(kanonisiert, `..`/Symlink-sicher) → App proxyt read-only an den Browser.

> **Designhinweis (Bridge-Richtung):** Das Channels-Beispiel betreibt den Channel als
> lokalen HTTP-Server. Da unsere App **remote** und die Nutzer-Maschine i. d. R. nicht
> erreichbar ist, **invertieren** wir: der Channel ist **WS-Client** zur App. Das ist
> App-spezifisch und berührt den MCP-Channel-Vertrag (claude ↔ channel) nicht.

### 2.2 Datenmodell

| Entity | Felder (Auszug) | Zweck |
|---|---|---|
| `AppUser` | `id`, `googleSub` (unique), `email`, `displayName`, `createdAt` | App-Identität (föderiert via Google). |
| `Session` | `id`, `userId→AppUser`, `displayName`, `cwdBasename`, `status` (CONNECTED/DISCONNECTED), `authorizationId`, `createdAt`, `lastSeenAt` | Eine gekoppelte Channel-Session (1..n je Nutzer). Live-WS liegt in `SessionRegistry`. |
| `Message` *(Soll)* | `id`, `sessionId→Session`, `role` (USER/ASSISTANT/SYSTEM), `content`, `toolActivity?`, `ts` | Optionaler Verlauf je Session. |
| OAuth-Authorizations/Tokens | via **Spring Authorization Server** (`JdbcOAuth2AuthorizationService`), verknüpft mit `AppUser` | Device-/Access-/Refresh-Token; Revoke. |

- Beziehungen: `AppUser` 1—* `Session`; `AppUser` 1—* Authorizations; `Session` 1—* `Message`.
- **Verschlüsselung at-rest** für Token/Secrets (NFA-04); `googleSub` als stabiler
  externer Identifier; `Session.authorizationId` koppelt Revoke an Geräte-Trennung.

### 2.3 Schnittstellen

#### 2.3.1 Auth/OAuth (Spring Authorization Server)

| Endpoint | Grant/Funktion |
|---|---|
| `GET /oauth2/authorize` | Authorization Code (+PKCE) – Browser-Clients. |
| `POST /oauth2/device_authorization` | Device Grant: liefert `device_code`, `user_code`, `verification_uri`, `verification_uri_complete`, `interval`, `expires_in`. |
| `GET/POST /activate` (Device-Verification) | Approval-Seite; nur für eingeloggte Nutzer (sonst Google-Login). Bindet `device_code` ↔ `AppUser`. |
| `POST /oauth2/token` | `authorization_code` **und** `urn:ietf:params:oauth:grant-type:device_code` (Polling; Fehler `authorization_pending`/`slow_down`/`expired_token`). |
| `POST /oauth2/revoke` | Token/Gerät widerrufen (FA-09). |
| `/login/oauth2/code/google` | Föderations-Callback (Upstream-IdP). |

Client-Registrierung: ein vertrauenswürdiger **`oa-shell-channel`**-Client (public,
Device-Grant + Refresh, Scopes z. B. `session`, `files`).

#### 2.3.2 Bridge (App ↔ Channel), WebSocket `/bridge`

- **Handshake-Auth:** `Authorization: Bearer <access_token>` → App validiert (SAS),
  löst `AppUser`, legt/aktualisiert `Session` (CONNECTED), registriert Live-Verbindung.
- **Envelope:** `{ "type", "id"?, ...payload }` (JSON, NDJSON über WS-Text).

| Richtung | `type` | Payload |
|---|---|---|
| Channel→App | `hello` | `cwdBasename`, `claudeVersion`, `channelVersion` |
| App→Channel | `chat` | `text` |
| Channel→App | `reply` | `text` (ggf. Teil-Chunks für Streaming) |
| Channel→App | `permission_request` | `request_id`, `tool_name`, `description`, `input_preview` |
| App→Channel | `permission_verdict` | `request_id`, `behavior` (`allow`/`deny`) |
| App→Channel | `file_tree` | `requestId`, `path` |
| Channel→App | `file_tree_result` | `requestId`, `entries[]` (name, type, size) |
| App→Channel | `file_content` | `requestId`, `path` |
| Channel→App | `file_content_result` | `requestId`, `content`\|`truncated`\|`binary`, `size`, `mime` |
| beide | `ping`/`pong` | Heartbeat/Reconnect-Erkennung |

#### 2.3.3 Browser ↔ App

- **WebSocket `/ws`** (App-Session per Cookie authentifiziert): `selectSession`,
  `chat`, eingehend `reply`/`stream`/`permission_request`, ausgehend `permission_verdict`.
  Alle Nachrichten mit **Ownership-Prüfung** (Session gehört dem eingeloggten Nutzer).
- **REST:**
  - `GET /api/sessions` – Sessions des Nutzers (id, name, status, cwdBasename).
  - `POST /api/sessions/{id}/name` – umbenennen (FA-14).
  - `POST /api/sessions/{id}/disconnect` – trennen/Revoke (FA-09).
  - `GET /api/sessions/{id}/files?path=` – Baum (über Bridge, FA-24).
  - `GET /api/sessions/{id}/file?path=` – Inhalt (über Bridge, FA-25).

#### 2.3.4 Channel-MCP-Vertrag (claude ↔ channel)

Gemäß offizieller Referenz (verifiziert): Capabilities
`experimental['claude/channel']`, `experimental['claude/channel/permission']`,
`tools`; Inbound `notifications/claude/channel` (`content`,`meta`); `reply`-Tool;
Relay `notifications/claude/channel/permission_request` →
`notifications/claude/channel/permission`. **An einer Stelle gekapselt** (NFA-06).

## 3. Implementierungsplan

### 3.1 Änderungen pro Komponente

| Komponente | Änderung | Aufwand |
|---|---|---|
| Projekt-Setup | Maven-SB-Projekt (JDK 21) + Node-Channel-Paket-Skelett + CI + Git-Init | Klein |
| `auth` | SAS-Konfiguration, Google-Föderation, Device-Endpoints + Approval-Seite, Token/Revoke | Groß |
| `bridge` | WS-Endpoint, Token-Auth, `SessionRegistry`, Envelope-Routing | Mittel |
| Channel (Node) | `oa-shell login` (Device-Grant), MCP-Server, ausgehende WSS, File-Serving, Gating | Groß |
| `chat` | Browser-WS, Routing, Streaming, optional Verlauf | Mittel |
| `permission` | Relay + `request_id`-Matching + Browser-Dialog | Mittel |
| `files` | REST-Proxy + Browser-Datei-Browser/Viewer; cwd-Sandbox-Tests | Mittel |
| `session`/Frontend | Session-Liste/Umschalter, Kontext-Isolierung, Benennung | Mittel |
| Härtung | Token-Lifecycle/Revoke, Reconnect, Idle, Security-Review, Preview-Kapselung | Mittel |
| E2E | Playwright-Suite + Build-Integration | Mittel |

### 3.2 Reihenfolge der Implementierung

0. **Spike (zuerst, Konzept §7.1):**
   a) `fakechat`-Smoke-Test → Channels-Freischaltung des Accounts bestätigen.
   b) Minimaler `oa-shell`-Channel: ausgehende WS-Echo + `reply` + `permission_request`-
      Relay + File-Serving gegen einen Stub-Server, an einer echten `claude`-Session.
   → Erst nach erfolgreichem Durchstich weiter.
1. **Scaffolding:** Maven-SB-App, Node-Paket, CI-Pipeline, Repo.
2. **Auth:** SAS + Google-Föderation; Browser-Code-Login; Device-Endpoints + Approval;
   Token-Bindung an `AppUser`; `oa-shell login`-CLI.
3. **Bridge + Session:** WS-Auth, `SessionRegistry`, `Session`-Persistenz, `hello`.
4. **Chat:** Browser-WS, Routing, Streaming (+ optional Verlauf).
5. **Permission-Relay:** Ende-zu-Ende Dialog ↔ Verdikt.
6. **Dateien:** Channel-File-Serving (cwd-sandboxed) + REST-Proxy + Browser-UI.
7. **Multi-Session:** Liste/Umschalter, Kontext-Isolierung, Benennung.
8. **Härtung:** Revoke, Reconnect, Idle, Security-Review, Preview-Vertrag kapseln.
9. **E2E:** Playwright (AK-1…AK-9) in den Build einhängen.

## 4. Testplan

- **Unit (Java):** Device-Flow-Logik & Token-Bindung; **Pfad-Sandbox** (Kanonisierung,
  Symlinks, `..`-Ausbrüche, NFA-02); Bridge-Routing; `request_id`-Matching; Ownership.
- **Unit (Node):** MCP-Vertrag, File-Serving-Scope, Sender-Gating, Reconnect/Token-Reuse.
- **Integration:** SAS-Device-Flow (RFC-8628-Fehlercodes), Bridge-WS-Auth,
  **Multi-Session-Routing-Isolierung** (kein Kontext-Leck zwischen Sessions/Nutzern).
- **E2E (Playwright, NFA-14):** AK-1 (Login), AK-2 (Device-Login), AK-3 (Multi-Session),
  AK-4 (Chat), AK-5 (Freigabe Write), AK-6 (Datei nur unter `cwd`), AK-7 (Isolierung),
  AK-8 (Parallelität), AK-9 (Trennung/Reconnect/Revoke).
- **Smoke/Spike (CI, ggf. manuell-gegated):** `fakechat` + `oa-shell`-Channel gegen
  eine reale Session (abhängig von Preview-Freischaltung → als optionales Gate markiert).
- **Security:** Prompt-Injection-Gating, Token-Leakage (Logs/Browser), Pfad-Traversal,
  TLS/WSS-Erzwingung.

## 5. Migration / Deployment

- **Greenfield:** `git init`, Repo, CI; keine Datenmigration.
- **Build (polyglott, NFA-09):** Maven baut die App (Jar); Node-Paket für den Channel
  (`npm pack`/Repo/Release). Setup-Doku für beide.
- **Server-Deployment:** hinter **TLS** (WSS erzwungen); Google-OAuth-Client
  (Client-ID/Secret, Redirect-URIs) konfigurieren; SAS-Schlüssel & Token-Settings;
  DB (z. B. Postgres) mit SAS-Schema + App-Schema; **Verschlüsselungsschlüssel** für Token.
- **Nutzer-Setup:** Node ≥18 + `claude` ≥2.1.81; `oa-shell`-Channel installieren;
  einmal `oa-shell login`; danach
  `claude --dangerously-load-development-channels server:oa-shell` im gewünschten
  Verzeichnis starten.
- **Preview-Hinweise:** Channels sind Research Preview – `--channels`/Protokoll können
  sich ändern (NFA-06); `claude`-Version dokumentieren/pinnen; Account-Freischaltung
  Voraussetzung.

## 6. Referenzen

- [Konzept](01-konzept-claude-chat.md) · [Ist-Analyse](02-ist-analyse-claude-chat.md) ·
  [Anforderungsanalyse](03-anforderungsanalyse-claude-chat.md)
- Channels: `code.claude.com/docs/de/channels` · Referenz:
  `code.claude.com/docs/en/channels-reference`
- RFC 8628 (Device Authorization Grant); Spring Authorization Server (Device-Flow).
