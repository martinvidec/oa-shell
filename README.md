# oa-shell

Multi-User **Chat-Oberfläche für lokale Claude-Code-Sessions** über das
[Channels-Feature](https://code.claude.com/docs/en/channels-reference) von Claude Code –
nach dem Vorbild des offiziellen Telegram-Plugins.

> **Status:** Design-/Spezifikationsphase. Noch kein Anwendungscode – die
> Architektur ist in [`docs/`](docs/) ausgearbeitet.

## Idee in einem Satz

Der Nutzer startet `claude` lokal selbst (mit dem `oa-shell`-Channel); die Web-App
ist nur die **Chat-Oberfläche**: Login, Chat, Tool-Freigaben und ein Datei-Browser
des Session-Arbeitsverzeichnisses – die App hostet **keine** Sessions, verwaltet
**keine** Verzeichnisse und speichert **keine** API-Keys.

## Eckpunkte der Architektur

- **Hosting-Modell:** `claude` läuft beim Nutzer; die App (Spring Boot) ist die Plattform.
- **Kommunikation:** eigener **Channel-MCP-Server** (Node), der sich **ausgehend** zur App verbindet.
- **Auth:** App ist eigener **OAuth2 Authorization Server** (föderiert an Google);
  Browser-Login (Authorization Code) **+ Device Grant** (RFC 8628) für headless –
  das Device-Login-Token ist zugleich die Session-Authentifizierung.
- **Multi-Session:** ein Nutzer kann mit mehreren Sessions verbunden sein.
- **Freigaben:** Tool-Genehmigungen über das **Permission-Relay** der Channels.
- **Tech:** Spring Boot (Maven, JDK 21), Thymeleaf + Vanilla JS, Node-Channel
  (`@modelcontextprotocol/sdk`).

## Dokumentation

1. [Konzept](docs/01-konzept-claude-chat.md)
2. [Ist-Analyse](docs/02-ist-analyse-claude-chat.md)
3. [Anforderungsanalyse](docs/03-anforderungsanalyse-claude-chat.md)
4. [Spezifikation](docs/04-spezifikation-claude-chat.md)

## Sicherheit

Dies ist ein **öffentliches** Repository. **Keine Secrets, API-Keys, Tokens oder
Zugangsdaten einchecken** – entsprechende Pfade sind in [`.gitignore`](.gitignore)
ausgeschlossen. Claude-Code-Channels sind ein **Research-Preview-Feature**; Flag- und
Protokoll-Details können sich ändern.
