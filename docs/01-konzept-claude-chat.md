# Konzept: oa-shell – Multi-User Chat-Oberfläche für lokale Claude-Code-Sessions über Channels

> Status: Entwurf zur Prüfung (v3) · Sprache: Deutsch · Projekt: `oa-shell` (Greenfield)

## 1. Zusammenfassung

`oa-shell` ist eine **Multi-User-Web-Anwendung** (Spring-Boot-Backend + Browser-UI),
die als **Chat-Oberfläche** für Claude-Code-Sessions dient – nach dem Vorbild des
offiziellen Telegram-Plugins. Die App spielt die Rolle der externen Plattform
(analog „Telegram"); **die Claude-Session läuft beim Nutzer lokal**: Der Nutzer
startet `claude` selbst auf seiner Maschine, in seinem eigenen Verzeichnis, mit
seiner eigenen Claude-Anmeldung, und hängt dabei den **`oa-shell`-Channel-Server**
(Node, nach `fakechat`-Vorbild) an. Dieser Channel verbindet sich **ausgehend** mit
der Web-App. Über die App kann der Nutzer dann mit seiner Session chatten, jede
Tool-Freigabe (z. B. `Bash`, `Write`) per Browser-Dialog **erlauben/ablehnen** und
den **Datei-Baum des Session-Arbeitsverzeichnisses** einsehen. Login erfolgt über
einen **konfigurierbaren OAuth-Provider** (Default Google); ein **Pairing** verknüpft
das App-Konto mit der lokalen Session.

## 2. Problemstellung

Claude Code ist terminalgebunden. Gewünscht ist eine **browserbasierte,
mehrbenutzerfähige** Oberfläche, die:

- mehreren Nutzern den Chat mit ihrer **jeweils lokal laufenden** Claude-Session
  ermöglicht (jeder mit eigenem Login),
- den Datei-Baum **des Session-Arbeitsverzeichnisses** sichtbar macht (und nichts
  darüber hinaus),
- dem Nutzer die **Kontrolle** über schreibende/ausführende Aktionen lässt
  (Freigabe pro Tool-Nutzung),
- dem bewährten **Channel-Muster** des Telegram-Plugins folgt,
- **ohne** dass die App selbst Claude-Sessions hostet, Verzeichnisse verwaltet
  oder API-Keys speichert.

## 3. Zielsetzung

- **Z1:** Mehrbenutzer-Login. Die App ist **eigener Authorization Server** und
  **föderiert die Identität an Google** (Default-Provider, konfigurierbar). Zwei
  Login-Wege: **Browser** per Authorization Code (+PKCE), **headless** (lokaler
  Channel/CLI) per **OAuth 2.0 Device Authorization Grant** (RFC 8628).
- **Z2:** Der **Device-Grant-Login des Channels ist zugleich die
  Session-Authentifizierung** – ein separater Pairing-Schritt entfällt. Ein
  Device-Login = eine eingeloggte, gekoppelte Session.
- **Z3:** Chat mit der lokalen Session über das Channels-Protokoll (Multi-Turn, Streaming).
- **Z4:** Tool-Freigabe pro Aktion als Browser-Dialog via Permission-Relay.
- **Z5:** Datei-Browser + Viewer, **strikt begrenzt auf das Arbeitsverzeichnis der
  Session** (vom lokalen Channel geliefert).
- **Z6:** Ein Nutzer kann mit **mehreren** Claude-Sessions gleichzeitig verbunden
  sein und in der UI zwischen ihnen wechseln (je Session eigener Channel, eigenes
  `cwd`, eigener Chat/Datei-Browser).
- **Nicht-Ziele:** kein Hosten von Sessions, keine Verzeichnis-Verwaltung, keine
  API-Key-Speicherung durch die App.
- **Messbar:** Zwei verschiedene Nutzer melden sich an, koppeln je ihre lokale
  Session und führen je eine Aufgabe „lege Datei X mit Inhalt Y an" durch – inkl.
  Browser-Freigabe der `Write`-Aktion – und sehen das Ergebnis im Datei-Browser,
  ohne Zugriff auf die Session/Dateien des jeweils anderen.

## 4. Lösungsidee

### 4.1 Grobarchitektur / Topologie

```
[ Maschine des Nutzers ]                         [ Server ]
 claude  (interaktives Terminal,                  Spring-Boot-Web-App ("Plattform")
   cwd = Verzeichnis des Nutzers,                   ├─ AuthServer (AS, föderiert an Google):
   eigene Claude-Anmeldung)                         │   Browser-Login (Code) +
   │ stdio (MCP)                                    │   Device Grant (RFC 8628) für headless
 oa-shell Channel-Server (Node) ──ausgehende WS──► ├─ BridgeGateway (WS-Server für Channels)
   - Device-Grant-Login (RFC 8628, holt Token)      ├─ ChatService (Browser ↔ Session)
   - claude/channel  (Nachrichten in die Session)   ├─ PermissionRelayService
   - reply-Tool      (Antworten heraus)             ├─ FileViewService (fragt Baum/Inhalt
   - claude/channel/permission (Freigaben)          │   beim Channel an)
   - File-Serving    (Baum/Inhalt von cwd)          └─ SessionRegistry (Konto ↔ 1..n Sessions)
   - Sender-Gating   (nur authentifizierte Verb.)  Browser (UI) ⇄ WS/REST ⇄ Spring Boot
```

Kernprinzip: Die **Nutzer-Maschine baut die Verbindung auf** (ausgehende
WebSocket), wie das Telegram-Plugin die Telegram-API abruft. Dadurch ist **kein
Inbound-Zugang** zur Nutzer-Maschine nötig (NAT-freundlich), und die App muss
keine Sessions/Verzeichnisse hosten.

### 4.2 Nachrichtenfluss (verifiziert gegen die offizielle Channels-Doku)

1. **Login (zwei Wege, gleiche App-Identität, Quelle Google):**
   - **Browser:** Authorization Code (+PKCE) am App-AS, der an Google föderiert.
   - **Headless Device-Grant-Login (RFC 8628):** Der lokale Channel startet eine
     Device Authorization, holt beim App-AS `device_code` + `user_code` +
     `verification_uri`, zeigt `user_code`/URL an; der Nutzer bestätigt im Browser
     (Identität via Google); der Channel **pollt** das Token-Endpoint und erhält ein
     kontogebundenes Token. **Dieses Login-Token ist zugleich die Session-Auth** –
     damit baut der Channel die ausgehende WS auf (kein separates Pairing). Mehrfach
     durchführbar → **mehrere gekoppelte Sessions**.
2. **Eingang (Nutzer → Claude):** Browser → App → (gekoppelte WS) → Channel pusht
   `notifications/claude/channel` (`content`, `meta`) → Claude empfängt
   `<channel source="oa-shell" …>`.
3. **Ausgang (Claude → Nutzer):** Claude ruft das **`reply`-Tool** → Channel → App
   → Browser (Streaming).
4. **Freigabe (Permission-Relay):** Claude Code sendet
   `notifications/claude/channel/permission_request`
   `{request_id, tool_name, description, input_preview}` → Channel → App →
   Browser-Dialog → zurück als `notifications/claude/channel/permission`
   `{request_id, behavior: "allow"|"deny"}`. (Der lokale Terminal-Dialog des Nutzers
   bleibt parallel offen – erste Antwort gewinnt.)
5. **Dateien:** Browser → App → (WS) → Channel liest Baum/Inhalt **unter `cwd`**
   (strikt begrenzt) → zurück an App → Browser.

### 4.3 Multi-User, Identität & Gating

- OAuth-Login → App-Identität. Jeder Nutzer koppelt **eine oder mehrere** eigene
  lokale Sessions; die App führt sie je Konto in der `SessionRegistry` und bietet
  einen Session-Umschalter.
- **Sender-Gating:** Der Channel akzeptiert nur Nachrichten über die **authentifizierte**
  App-Verbindung (Device-Grant-Token) → Schutz vor Prompt-Injection.
- Isolierung zwischen Nutzern ist reine **App-Verantwortung** (Konto ↔ Session-Zuordnung);
  es gibt keine geteilten serverseitigen Ressourcen.

### 4.4 Dateizugriff

Der **Channel-Server** (Node, auf der Nutzer-Maschine) liefert Verzeichnisbaum und
Datei-Inhalte **ausschließlich unterhalb seines Arbeitsverzeichnisses** (`process.cwd()`
= cwd der Session). Die App **proxyt** diese Daten nur an den Browser (read-only Viewer)
und speichert/verwaltet selbst keine Dateien.

## 5. Betroffene Komponenten

Greenfield – folgende Komponenten werden **neu** erstellt:

| Komponente | Stack | Zweck |
|---|---|---|
| Spring-Boot-Projekt | **Maven**, Java (JDK 21) | Backend-Grundgerüst, Build |
| Auth-Server (`AuthServer`) | **Spring Authorization Server** | App = eigener AS: **Browser-Login** (Authorization Code +PKCE) **+ Device Grant** (RFC 8628); föderiert Identität an **Google** (konfigurierbar). Device-Login-Token = Session-Auth. |
| `BridgeGateway` | Spring WebSocket | Endpunkt, mit dem sich Channel-Server **ausgehend** (token-authentifiziert) verbinden |
| `SessionRegistry` | Java | **1..n** aktive Sessions je Nutzer verwalten |
| `ChatService` | Java | Nachrichten Browser ↔ (gewählte) Session routen (Streaming) |
| `PermissionRelayService` | Java | `permission_request` → UI, Verdikt zurück |
| `FileViewService` | Java | Baum/Inhalt der gewählten Session beim Channel anfragen, an Browser proxen |
| Persistenz | Spring Data | Nutzer, Device-Grant-/Session-Token (verschlüsselt), optional Verlauf |
| **`oa-shell` Channel-Server** | **Node + `@modelcontextprotocol/sdk`** | Brücke: **Device-Grant-Auth** + channel/reply/permission **+ File-Serving + ausgehende WS + Gating** |
| Frontend | **Thymeleaf + Vanilla JS** | Login, Device-Approval-Seite, **Session-Liste/-Umschalter**, Chat (Streaming), Datei-Browser, Freigabe-Dialog |

## 6. Abgrenzung – NICHT Teil von v1

- **Kein** Hosten von Claude-Sessions durch die App; **keine** serverseitigen
  Arbeitsverzeichnisse; **keine** API-Key-Speicherung (alles auf der Nutzer-Maschine).
- **Kein** Editieren/Speichern von Dateien aus der UI (Viewer read-only).
- **Kein** Dateizugriff außerhalb des Session-`cwd`.
- **Kein** produktives Härten der Channel-Preview (Research Preview, s. §7).
- **Kein** Marketplace-Eintrag des Channels (Start über Development-Flag).
- **Keine** Telegram/Discord/iMessage-Anbindung – die App ist die Plattform.
- Mehrere parallele Sessions je Nutzer **sind** unterstützt (Z6); ausgenommen sind
  nur Komfort-Funktionen wie Verlaufssuche oder Session-übergreifende Aktionen.

## 7. Offene Fragen & Risiken

1. **Validierungs-Spike (zuerst):** Ende-zu-Ende-Durchstich mit einem von `fakechat`
   abgeleiteten Channel: ausgehende WS zur App, `reply`, **Permission-Relay** und
   **File-Serving** zusammen. Da der Nutzer `claude` interaktiv betreibt, ist das
   frühere pty/headless-Problem entschärft – zu bestätigen bleibt das Zusammenspiel.
2. **Account-Freischaltung (Preview):** Channels müssen für den Account des Nutzers
   verfügbar sein (Pro/Max ohne Org: an; Team/Enterprise: Admin). Pro Nutzer auf
   dessen Maschine zu prüfen.
3. **Preview-Instabilität:** `--channels`-Syntax/Protokoll können sich ändern →
   Channel-Vertrag an einer Stelle kapseln, `claude`-Version dokumentieren.
4. **Device-Grant-/Token-Sicherheit:** Die Device-Grant-Tokens sind das zentrale
   Geheimnis (statt API-Keys): `user_code` kurzlebig + einmalig, Tokens verschlüsselt
   gespeichert, Refresh/Ablauf/Revoke definiert, nie im Klartext geloggt. Wer ein
   gültiges Token hat, kann Nachrichten in die Session schicken/Freigaben erteilen.
4a. **Multi-Session-Modell:** Token-/Session-Bindung **je Channel** (1 Device-Login
   = 1 Token = 1 Session) vs. ein Token mit mehreren Verbindungen; UX des
   Session-Umschalters, getrennte Chat-/Datei-Kontexte pro Session.
4b. **AS-Token-Format/Scopes:** Token-Typ (opaque vs. JWT), Scopes, Gültigkeit/Refresh,
   Revoke-Mechanik des App-AS – in der Spec zu fixieren.
5. **File-Serving-Grenzen:** strikte Begrenzung auf `cwd` (kanonisierte Pfade,
   Symlink-Behandlung), Größen-/Binärdatei-Handling, keine `..`-Ausbrüche.
6. **Verbindungs-/Lebenszyklus:** Reconnect der ausgehenden WS, Erkennen, wenn die
   lokale Session endet (Channel weg), saubere UI-Zustände.
7. **Channel-Distribution:** Wie erhält/startet der Nutzer den `oa-shell`-Channel
   (npm-Paket/Repo/Plugin) und mit welchem Befehl
   (`claude --dangerously-load-development-channels server:oa-shell`)? Setup-Doku nötig.
8. **Verhalten bei „Ablehnen":** Turn beenden vs. optionales Begründungsfeld
   (Empfehlung: optionales Feld).

### Überholte Entscheidungen (durch Modell A ersetzt)

- ~~Pro-Nutzer-API-Key in der App~~ → entfällt; lokale Claude-Anmeldung des Nutzers.
- ~~Server startet/hostet Sessions; serverseitige Arbeitsverzeichnisse; Container-
  Isolierung; pty auf der JVM~~ → entfällt; Session läuft lokal beim Nutzer.

### Erledigte Entscheidungen

- Build-Tool: **Maven** · Frontend: **Thymeleaf + Vanilla JS** · JDK **21 LTS**
- Kommunikation: **Channels + Permission-Relay** (eigener Node-Channel-Server)
- Multi-User-Login: App = **eigener Authorization Server**, föderiert an **Google**
  (konfigurierbar). Zwei Wege: Browser (Authorization Code) **+ Device Grant (RFC 8628)**
  für headless. **Device-Login-Token = Session-Auth** (ersetzt separates Pairing).
- **Mehrere Sessions je Nutzer** (Session-Umschalter; je Session eigener Channel/`cwd`/Chat)
- Hosting: **Nutzer betreibt `claude` lokal; App = nur Chat-Oberfläche**
- Dateizugriff: **nur Session-`cwd`**, vom Channel geliefert, App proxyt read-only

### Verifiziert gegen offizielle Quellen

- Channels & Telegram-Setup, `--channels`, Auth, Preview:
  `https://code.claude.com/docs/de/channels`
- Channels-Referenz (Capabilities, `notifications/claude/channel`, `reply`-Tool,
  Permission-Relay `permission_request`/`permission`, Sender-Gating):
  `https://code.claude.com/docs/en/channels-reference`
- Verifiziert: Die Flags `--channels` / `--dangerously-load-development-channels`
  existieren in aktuellen Claude-Code-Versionen (≥2.1.81); Node genügt als Laufzeit
  des Channels (Bun laut Docs nicht erforderlich).
