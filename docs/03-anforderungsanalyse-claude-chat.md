# Anforderungsanalyse: oa-shell – Chat-Oberfläche für lokale Claude-Code-Sessions

> Status: Entwurf zur Prüfung (v3 – Modell A + Device Grant + Multi-Session) ·
> Sprache: Deutsch · Bezug: [Konzept](01-konzept-claude-chat.md) ·
> [Ist-Analyse](02-ist-analyse-claude-chat.md)

Priorität: **Muss** (v1 zwingend) · **Soll** (v1 angestrebt) · **Kann** (später).
Modell A: Der Nutzer betreibt `claude` lokal; die App ist die Chat-Oberfläche.

## 1. Funktionale Anforderungen

### 1.1 Authentifizierung & Nutzer

> Die App ist **eigener Authorization Server** (Spring Authorization Server) und
> **föderiert die Identität an Google** (Default, konfigurierbar). Zwei Login-Wege:
> **Browser** (Authorization Code, §1.1) und **headless Device Grant** (§1.2).

| ID | Anforderung | Priorität | Beschreibung |
|---|---|---|---|
| FA-01 | Browser-Login | Muss | Web-UI-Login per **Authorization Code (+PKCE)** am App-AS; Identität via **Google** (Default). |
| FA-02 | App-Session & Logout | Muss | Sichere Server-Session, Logout, Schutz aller App-Routen. |
| FA-03 | Identitäts-Provider konfigurierbar | Soll | Föderierter IdP (Google default) per Konfiguration austauschbar/erweiterbar (OIDC). |
| FA-04 | Nutzeridentität | Muss | Eindeutige App-Identität als Basis für Device-Login-/Session-Zuordnung. |
| FA-04b | App als Authorization Server | Muss | App stellt OAuth2-AS bereit (Authorization Code **und** Device Grant), Identität föderiert an Google. |

### 1.2 Headless-Login & Session-Auth – Device Authorization Grant (RFC 8628)

> Der Device-Grant ist der **Login-Weg für headless-Clients** (lokaler Channel/CLI).
> Das ausgegebene **Token ist zugleich die Session-Authentifizierung** – ein
> separater Pairing-Schritt entfällt.

| ID | Anforderung | Priorität | Beschreibung |
|---|---|---|---|
| FA-05 | Device Authorization Endpoint | Muss | App (als **AS**) gibt `device_code`, `user_code`, `verification_uri`, `interval`, `expires_in` aus. |
| FA-06 | Device-Approval-Seite | Muss | Eingeloggter Nutzer bestätigt den `user_code` im App-Browser → Login-Bindung an das Konto (Identität via Google). |
| FA-07 | Token Endpoint (Polling) | Muss | Channel pollt mit `grant_type=…device_code` bis Approval; erhält kontogebundenes Token = **Login + Session-Auth**. |
| FA-08 | Token-Lebenszyklus | Muss | Token verschlüsselt gespeichert; Ablauf/Refresh/**Revoke** definiert; nie im Klartext (vgl. NFA-04). |
| FA-09 | Autorisierte Geräte/Sessions verwalten | Soll | Liste autorisierter Channels/Sessions; einzeln **trennen/Revoke** möglich. |

> Der Device-Grant-Login kann **mehrfach** durchlaufen werden (je Channel/Session
> einmal) → Grundlage für mehrere parallele Sessions (§1.3).

### 1.3 Sessions – mehrere parallel je Nutzer (kein App-Hosting)

| ID | Anforderung | Priorität | Beschreibung |
|---|---|---|---|
| FA-10 | Mehrere Sessions auflisten | Muss | App zeigt **alle** gekoppelten, verbundenen Sessions des Nutzers (1..n). |
| FA-11 | Session-Status | Muss | Je Session verbunden/getrennt sichtbar; Reaktion, wenn eine Session endet. |
| FA-12 | Session-Umschalter | Muss | Aktive Session wählen; Chat, Freigaben und Datei-Browser beziehen sich auf die **gewählte** Session. |
| FA-13 | Session-Kontext-Isolierung | Muss | Chat-/Freigabe-/Datei-Kontext strikt **pro Session** getrennt (kein Vermischen). |
| FA-14 | Session benennen | Soll | Anzeigename je Session (z. B. `cwd`-Basisname) zur Unterscheidung. |

### 1.4 Chat (Channel-Kommunikation)

| ID | Anforderung | Priorität | Beschreibung |
|---|---|---|---|
| FA-15 | Nachricht senden | Muss | Eingabe wird via `notifications/claude/channel` (gated) in die gewählte Session gepusht. |
| FA-16 | Antwort empfangen | Muss | Claudes Antwort über das `reply`-Tool erscheint in der UI der richtigen Session. |
| FA-17 | Streaming/Inkrementell | Muss | Antworten/Status werden laufend in die UI gestreamt (WebSocket). |
| FA-18 | Tool-Aktivität anzeigen | Soll | Sichtbar machen, welche Tools laufen/liefen (Transparenz). |
| FA-19 | Verlauf anzeigen | Soll | Persistierter Nachrichtenverlauf je Session in der UI. |

### 1.5 Freigabe (Permission-Relay)

| ID | Anforderung | Priorität | Beschreibung |
|---|---|---|---|
| FA-20 | Freigabe-Dialog | Muss | Bei `permission_request` Dialog mit `tool_name`, `description`, `input_preview` (zur richtigen Session). |
| FA-21 | Allow/Deny zurückführen | Muss | Entscheidung als `permission` (mit `request_id`) an die Session. |
| FA-22 | Eindeutige Zuordnung | Muss | Verdikt nur bei passender, offener `request_id`; korrekte Session-Zuordnung (mehrere offen möglich). |
| FA-23 | Begründung bei Deny | Soll | Optionales Begründungsfeld, das als Folge-Nachricht an Claude geht. |

### 1.6 Dateizugriff (strikt auf Session-`cwd`)

| ID | Anforderung | Priorität | Beschreibung |
|---|---|---|---|
| FA-24 | Datei-Browser | Muss | Verzeichnisbaum **unterhalb des `cwd` der gewählten Session**, vom Channel geliefert. |
| FA-25 | Datei-Viewer (read-only) | Muss | Inhalt einzelner Dateien anzeigen (kein Editieren in v1). |
| FA-26 | Strikte Begrenzung | Muss | Kein Zugriff außerhalb `cwd` (kanonisierte Pfade, keine `..`-Ausbrüche, Symlink-Behandlung). |
| FA-27 | Auto-Refresh | Soll | Baum/Inhalt aktualisieren nach Schreibaktionen der Session. |
| FA-28 | Binär-/Größen-Handling | Soll | Große/binäre Dateien sinnvoll behandeln (Limit/Hinweis statt Roh-Dump). |

### 1.7 `oa-shell` Channel-Server (Auslieferung an den Nutzer)

| ID | Anforderung | Priorität | Beschreibung |
|---|---|---|---|
| FA-29 | Channel-Server | Muss | Node-MCP-Server: **Device-Grant-Auth**, `claude/channel`, `reply`, `claude/channel/permission`, **File-Serving (cwd)**, **ausgehende WS**, **Sender-Gating**. |
| FA-30 | Channel-Konfiguration | Muss | App-URL per Konfiguration/Env; Device-Grant beim Start (kein manuelles Secret nötig). |
| FA-31 | Setup-Doku & Startbefehl | Soll | Anleitung inkl. `claude --dangerously-load-development-channels server:oa-shell`. |
| FA-32 | Reconnect | Soll | Ausgehende WS stellt sich nach Abbruch wieder her (Token-Wiederverwendung). |
| FA-33 | Session-Ende erkennen | Soll | Beim Beenden der lokalen Session sauberer UI-Status (getrennt). |

## 2. Nicht-funktionale Anforderungen

| ID | Anforderung | Kategorie | Beschreibung |
|---|---|---|---|
| NFA-01 | Mandantentrennung | Sicherheit | Kein Zugriff auf fremde Sessions/Dateien/Chats; strikte Konto↔Session-Zuordnung. |
| NFA-02 | File-Serving-Grenze | Sicherheit | Channel liefert nur Pfade **unter `cwd`** (kanonisiert, Symlink-/`..`-sicher). |
| NFA-03 | Sender-Gating | Sicherheit | Channel akzeptiert nur die **token-authentifizierte** App-Verbindung (Prompt-Injection-Schutz). |
| NFA-04 | Token-Schutz | Sicherheit | Device-Grant-/Session-Token verschlüsselt at-rest; `user_code` kurzlebig+einmalig; Revoke; nie geloggt. |
| NFA-05 | Freigabepflicht | Sicherheit | Schreibende/ausführende Tools nur nach UI-Freigabe; kein `bypass`. |
| NFA-06 | Preview-Robustheit | Wartbarkeit | Channel-Protokoll **an einer Stelle gekapselt**; `claude`-Version dokumentiert; Smoke-Test in CI. |
| NFA-07 | Skalierung | Performance | App hostet keine Sessions → skaliert mit **Verbindungen** (WS), nicht mit Prozessen. Mehrere Sessions/Nutzer berücksichtigt. |
| NFA-08 | Streaming-Latenz | Performance | Antworten erscheinen spürbar inkrementell. |
| NFA-09 | Reproduzierbarer Build | Wartbarkeit | Web-App (Maven) und Channel-Server (Node-Paket) reproduzierbar baubar/auslieferbar. |
| NFA-10 | Beobachtbarkeit | Betrieb | Strukturierte Logs ohne Secrets; Verbindungs-/Health-Status der Channels. |
| NFA-11 | Laufzeit-Voraussetzungen | Kompatibilität | **Server:** JDK 21, Maven ≥3.6.3. **Nutzer-Maschine:** `claude` ≥2.1.81, Node ≥18. |
| NFA-12 | Auth-Kompatibilität | Kompatibilität | Claude-Auth (Nutzer-Seite) nur claude.ai/Console-Key; **nicht** Bedrock/Vertex/Foundry. |
| NFA-13 | Usability Freigabe | Usability | Freigabedialog zeigt Tool, Befehl/Argumente und erkennbares Risiko klar an. |
| NFA-14 | E2E-Testbarkeit | Qualität | UI-Features durch Playwright-E2E abgesichert (in Build/CI integriert). |
| NFA-15 | Transport-Sicherheit | Sicherheit | Bridge-Verbindung und Device-/Token-Endpunkte über **TLS/WSS**. |
| NFA-16 | Standardkonformität | Wartbarkeit | Device-Flow konform zu **RFC 8628** (Standard-Parameter, Polling-`interval`, `slow_down`/`expired_token`-Fehler). |

## 3. Akzeptanzkriterien (v1)

- [ ] **AK-1 (Login):** Zwei verschiedene Nutzer melden sich per Google-OAuth an;
      nicht-authentifizierte Zugriffe werden abgewiesen.
- [ ] **AK-2 (Device-Grant-Login):** Ein lokal gestarteter Channel durchläuft den
      Device-Flow; nach Bestätigung im Browser ist der Channel **eingeloggt** und die
      Session erscheint als „verbunden" (Login + Session-Auth in einem Schritt).
- [ ] **AK-3 (Multi-Session):** Ein Nutzer koppelt **zwei** Sessions, wechselt
      zwischen ihnen; Chat, Freigaben und Datei-Browser bleiben pro Session getrennt.
- [ ] **AK-4 (Chat):** Eine gesendete Nachricht erreicht die gewählte Session;
      Claudes Antwort erscheint inkrementell in der UI.
- [ ] **AK-5 (Freigabe):** „lege Datei X mit Inhalt Y an" löst einen Freigabedialog
      (`Write`) aus; **Allow** führt aus, **Deny** verhindert die Aktion.
- [ ] **AK-6 (Dateien):** Datei X erscheint im Datei-Browser (unter `cwd`) und ihr
      Inhalt im Viewer; Pfade außerhalb `cwd` sind nicht erreichbar.
- [ ] **AK-7 (Isolierung):** Nutzer A sieht/erreicht weder Sessions noch Dateien noch
      Chats von Nutzer B.
- [ ] **AK-8 (Parallelität):** Zwei Nutzer arbeiten gleichzeitig mit je eigenen
      Sessions, ohne sich gegenseitig zu beeinflussen.
- [ ] **AK-9 (Trennung/Revoke):** Beendet der Nutzer eine lokale Session → Status
      „getrennt"; Reconnect funktioniert; ein Revoke entzieht dem Gerät den Zugriff.

## 4. Abhängigkeiten zu anderen Anforderungen

- **Spike (Konzept §7.1)** – End-to-End mit `fakechat`-abgeleitetem Channel
  (WS + `reply` + Permission-Relay + File-Serving) ist Voraussetzung für
  FA-15…FA-28 und NFA-05. → **vor** der Feature-Implementierung validieren.
- FA-05…FA-08 (Device Grant) sind Voraussetzung für FA-10…FA-28 (alles braucht eine
  authentifizierte Session).
- FA-10…FA-13 (Multi-Session) bedingen die Kontext-Trennung in FA-15/16/20/24.
- FA-29/FA-30 (Channel-Server) sind Voraussetzung für jeglichen Datenfluss.
- FA-26/NFA-02 (cwd-Begrenzung) ist Voraussetzung für FA-24/25.
- NFA-14 (E2E) bezieht sich auf alle UI-Anforderungen.

## 5. Priorisierung

1. **Fundament/Spike (zuerst):** Channels-Freischaltung prüfen; `oa-shell`-Channel
   als `fakechat`-Ableitung mit WS + `reply` + Permission-Relay + File-Serving
   (FA-29/30); End-to-End-Durchstich.
2. **MVP-Kern (Muss):** FA-01/02/04/04b, FA-05/06/07/08, FA-10/11/12/13,
   FA-15/16/17, FA-20/21/22, FA-24/25/26; NFA-01…05, NFA-11/12/15/16.
3. **Stabilisierung (Soll):** FA-03/09/14/18/19/23/27/28/31/32/33;
   NFA-06/07/08/09/10/13/14.
4. **Komfort (Kann):** spätere Erweiterungen (z. B. Verlaufssuche, Session-übergreifende
   Aktionen).

## 6. Referenzen

- [Konzept](01-konzept-claude-chat.md) · [Ist-Analyse](02-ist-analyse-claude-chat.md)
