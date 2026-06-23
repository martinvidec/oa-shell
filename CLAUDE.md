# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Sprache: Der Nutzer arbeitet auf **Deutsch** – Antworten standardmäßig auf Deutsch.

## Projektstatus: Design-Phase (noch kein Code)

Dieses Repo enthält **noch keinen Anwendungscode** – nur die Spezifikationsdokumente
unter `docs/` und diese Datei. Implementierung hat noch nicht begonnen.

- **Quelle der Wahrheit ist `docs/`** – vor jeder Umsetzung lesen. Maßgeblich:
  `docs/04-spezifikation-claude-chat.md` (technisches Design), davor
  `01-konzept`, `02-ist-analyse`, `03-anforderungsanalyse`.
- **Arbeitsplan = 17 GitHub-Issues** in Implementierungsreihenfolge. **Issue #1
  (Spike) zuerst**: Channels-Freischaltung + End-to-End-Durchstich validieren, bevor
  Features gebaut werden. Übersicht: `gh issue list`, Detail: `gh issue view <n>`.

## Worum es geht (Big Picture)

`oa-shell` ist eine Multi-User-Web-Chat-Oberfläche für **lokal laufende**
Claude-Code-Sessions über das **Channels**-Feature von Claude Code (Research Preview,
nach Vorbild des Telegram-Plugins).

**Hosting-Modell A (tragende Entscheidung):** Der Nutzer startet `claude` **selbst
lokal** (mit dem oa-shell-Channel); die Web-App ist **nur die Chat-Oberfläche**. Die
App **hostet keine Sessions, verwaltet keine Verzeichnisse, speichert keine API-Keys**.
Serverseitiges Session-Hosting wurde bewusst verworfen – nicht wieder einführen.

## Zwei Artefakte

- **Web-App** – Spring Boot (Maven, **JDK 21**), Thymeleaf + Vanilla JS. Module:
  `auth`, `bridge`, `session`, `chat`, `permission`, `files`, `web`, `persistence`.
- **Channel-Paket** (`channel/`) – Node mit `@modelcontextprotocol/sdk`. Zwei
  Entrypoints: `oa-shell login` (Device-Grant-CLI) und der Channel-MCP-Server, den
  der Nutzer an seine `claude`-Session anhängt.

## Architektur-Fakten, die zusammenpassen müssen

- **Auth:** Die App ist ihr **eigener OAuth2 Authorization Server** (Spring
  Authorization Server) und **föderiert die Identität an Google**. Zwei Login-Wege:
  Browser = Authorization Code (+PKCE); headless Channel = **Device Authorization
  Grant (RFC 8628)**. Das **Device-Grant-Token IST die Session-Auth** – kein
  separates Pairing.
- **Bridge ist invertiert:** Der Channel ist ein **ausgehender WebSocket-*Client*** zur
  App (NAT-freundlich); die App verbindet sich nie inbound zur Nutzer-Maschine. Das ist
  App-spezifisch und ändert den MCP-Channel-Vertrag **nicht**.
- **Channel↔claude-Vertrag** = offizielles Channels-Protokoll, an **einer Stelle
  kapseln** (Preview → Flags/Protokoll können sich ändern): Push
  `notifications/claude/channel`; `reply`-Tool; Permission-Relay
  `…/permission_request` → `…/permission`; Capabilities
  `experimental['claude/channel']`, `experimental['claude/channel/permission']`, `tools`.
- **Permission-Relay** ist der UI-Freigabe-Mechanismus. Er funktioniert, weil der
  Nutzer `claude` **interaktiv (echtes Terminal)** betreibt → der Dialog „wartet" und
  das Relay beantwortet ihn.
- **Multi-Session:** ein Nutzer ↔ mehrere Sessions; je Session ein eigener
  Channel/`cwd`/Chat/Datei-Kontext, **strikt isoliert**.
- **Dateizugriff** liefert der **Channel**, **strikt begrenzt auf den Session-`cwd`**
  (kanonisiert, kein `..`/Symlink-Ausbruch); die App proxyt nur read-only.

## Befehle

- **Es gibt noch nichts zu bauen/testen.** Sobald das Scaffolding (Issues #2/#3) steht:
  App über **Maven** (`mvn verify` etc.), Channel über **npm**. Bis dahin existieren
  keine Build-/Test-Befehle.
- **Channel starten (Nutzer-Seite, gemäß Spec):** einmal `oa-shell login`, dann
  `claude --dangerously-load-development-channels server:oa-shell` im Zielverzeichnis.
  (Eigene Channels sind in der Preview nicht allowlisted → Development-Flag nötig.)

## Randbedingungen

- **Public Repo:** keine Secrets/Keys/Tokens einchecken (`.gitignore` deckt sie ab);
  keine maschinenspezifischen Pfade/Versionen in die Docs (wurden bewusst gescrubbt –
  Mindest-/Soll-Versionen statt installierter Werte angeben).
- **Channels-Voraussetzungen:** Auth nur claude.ai-/Console-API-Key (**nicht**
  Bedrock/Vertex/Foundry); der Account muss die Preview freigeschaltet haben.

## Bereits entschieden – nicht neu aufrollen

- Hosting-**Modell A** (Nutzer betreibt `claude` lokal) statt serverseitigem Hosting.
- **Kein** App-seitiger Pro-Nutzer-API-Key (lokale Claude-Anmeldung des Nutzers).
- **Kein** Ad-hoc-Pairing → **Device-Grant-Login** (Token = Session-Auth).
- App als **eigener AS**, föderiert an Google (statt Googles eigenem Device-Flow).
- **Maven**, **Thymeleaf + Vanilla JS**, **JDK 21**.

## Referenzen

- `docs/01-konzept-claude-chat.md` … `docs/04-spezifikation-claude-chat.md`
- Channels: `https://code.claude.com/docs/en/channels` ·
  Referenz: `https://code.claude.com/docs/en/channels-reference`
