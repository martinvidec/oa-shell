# Ist-Analyse: oa-shell – Ausgangslage & technische Umgebung

> Status: Entwurf zur Prüfung (v2 – Hosting-Modell A) · Sprache: Deutsch ·
> Bezug: [Konzept](01-konzept-claude-chat.md)

## 1. Aktueller Zustand

`oa-shell` ist ein **Greenfield-Projekt**: Es existiert **kein Anwendungscode** –
bisher nur die Dokumente unter `docs/`.

Die Laufzeit teilt sich in Modell A in **zwei Seiten**:

- **Server** (Web-App): Spring Boot, JDK 21, Maven. Hostet **keine** Claude-Sessions.
- **Nutzer-Maschine**: `claude` (interaktives Terminal) + `oa-shell`-Channel-Server
  (Node), im Verzeichnis und mit der Claude-Anmeldung des Nutzers.

Die Fakten zum Channel-Protokoll sind gegen die offizielle Claude-Code-Doku
**verifiziert**; die Umgebungsangaben beschreiben die **vorausgesetzte/empfohlene**
Toolchain je Seite.

Legende Status: ✅ Standard-Tooling / vorausgesetzt · ⚠️ Mindestversion beachten ·
🔲 neu zu schaffen / einzurichten · ❓ noch zu validieren (Spike).

## 2. Relevante Umgebung und Komponenten (Ist)

| Komponente | Seite | Status / Anforderung | Relevanz |
|---|---|---|---|
| `claude` CLI | Nutzer | ✅ benötigt **≥2.1.81** | Hostet Session + Channel beim Nutzer. Channels ab ≥2.1.80, Permission-Relay ab ≥2.1.81. |
| Channels-Feature | Nutzer | ❓ Preview; Flags `--channels` / `--dangerously-load-development-channels` existieren in der CLI | Trägt die Kommunikation. Freischaltung **pro Nutzer-Account** zu prüfen (Spike). |
| Claude-Auth | Nutzer | ✅ claude.ai-Login **oder** Console-API-Key (lokal) | Lokale Anmeldung des Nutzers genügt; **App speichert keine Keys**. |
| Node.js / npm | Nutzer | ✅ **Node ≥18** + npm | Laufzeit des Channel-Servers. „Bun, Node, and Deno all work" – Node genügt. |
| Bun | Nutzer | 🔲 **nicht erforderlich** | Für einen eigenen Channel nicht nötig (nur fertige Plugins nutzen Bun). |
| `@modelcontextprotocol/sdk` | Nutzer | 🔲 per npm einzubinden | Einzige harte Abhängigkeit des Channel-Servers. |
| JDK | Server | ⚠️ **JDK 21 LTS** | Spring Boot 3.x braucht Java 17+; JDK 21 als Voraussetzung gesetzt. |
| Maven | Server | ✅ **Maven ≥3.6.3** | Build-Tool der Web-App (SB-3-Minimum). |
| git | Server | ✅ Git (Repo eingerichtet) | Versionsverwaltung. |
| `claude-plugins-official` Marketplace | Nutzer | ✅ verfügbar | Quelle für `fakechat` als Referenz-/Ableitungsbasis des Channels. |
| OAuth-Provider (Google) | Server | 🔲 keine Client-Credentials | Login: Google-OAuth-Client anzulegen; Provider konfigurierbar. |
| **Web-App + Channel-Server + Frontend** | beide | 🔲 komplett neu | Sämtliche Komponenten (Konzept §5) neu zu erstellen. |

## 3. Bestehende Abhängigkeiten

**Interne Abhängigkeiten:** keine – Greenfield.

**Externe Abhängigkeiten (Ist bzw. einzuführen):**

- **Claude Code CLI + Channels-Protokoll** (Vertrag, verifiziert gegen
  `code.claude.com/docs/.../channels` und `.../channels-reference`):
  - Inbound: `notifications/claude/channel` (`content`, `meta`).
  - Reply: Standard-MCP-Tool (z. B. `reply` mit `chat_id`, `text`).
  - Permission-Relay: `notifications/claude/channel/permission_request`
    (`request_id`, `tool_name`, `description`, `input_preview`) →
    `notifications/claude/channel/permission` (`request_id`, `behavior`).
  - Capabilities: `experimental['claude/channel']`,
    `experimental['claude/channel/permission']`, `tools`.
- **MCP SDK** (`@modelcontextprotocol/sdk`, Node) – Basis des Channel-Servers
  (zzgl. File-Serving + ausgehender WS-Client zur App).
- **Spring-Boot-Ökosystem** (neu): Web/WebSocket, Security OAuth2 Client, Data, Thymeleaf.
- **App als Authorization Server** (Baustein: **Spring Authorization Server**, das
  sowohl Authorization Code als auch **Device Grant (RFC 8628, seit 1.1)**
  unterstützt). Der AS bietet **zwei Login-Wege**: Browser (Authorization Code) und
  headless **Device-Grant-Login** (= zugleich Session-Auth). Die **Identität wird an
  Google föderiert** (Default-IdP, konfigurierbar; OIDC) – die App ist der AS, Google
  der IdP.

## 4. Bekannte Einschränkungen

1. **JDK-Voraussetzung (Server):** Spring Boot 3.x braucht Java 17+ →
   **JDK 21 LTS** einsetzen.
2. **Research-Preview-Risiko:** `--channels`-Syntax/Protokoll **können sich ändern**.
3. **Eigene Channels nicht allowlisted:** Start beim Nutzer nur mit
   `--dangerously-load-development-channels server:oa-shell`.
4. **Session muss offen bleiben:** Events kommen nur an, „while the session is open";
   beendet der Nutzer `claude`, endet die Verbindung.
5. **NAT/Erreichbarkeit:** Die Nutzer-Maschine ist i. d. R. nicht von außen
   erreichbar → der **Channel verbindet sich ausgehend** zur App (kein Inbound nötig).
6. **Auth-Beschränkung des Features:** Channels nur mit claude.ai-/Console-API-Key-Auth;
   **nicht** Bedrock/Vertex/Foundry.
7. **Kein offizielles Java-Agent-SDK** – aber irrelevant: die App steuert Claude
   **nicht** in-process, sondern ausschließlich über den Channel.

## 5. Risiken / Stolpersteine beim Aufbau

Da es keinen Bestandscode gibt, bestehen keine Regressionsrisiken; die Risiken
liegen in den Fundamenten. **Hinweis:** Code wird auf der **Maschine des Nutzers**
ausgeführt (mit dessen Rechten und dessen Freigaben über das Relay) – die frühere
„RCE-as-a-Service"-Gefahr für den Server entfällt damit weitgehend.

- **Preview-Instabilität (hoch):** Protokoll-/Flag-Wechsel kann Channel-Server und
  Start-Befehl brechen. → Vertrag kapseln, Version dokumentieren, Smoke-Test in CI.
- **Mandanten-/Pairing-Sicherheit (hoch):** Die App muss Konto↔Session strikt
  trennen; Pairing-Token sind das zentrale Geheimnis (Token-Hijack = fremde Session
  steuerbar). → kurzlebige, einmalige Token; WSS/TLS; verschlüsselte Speicherung.
- **File-Serving-Grenzen (mittel):** strikte `cwd`-Begrenzung (Kanonisierung,
  Symlinks, `..`), Größen-/Binär-Handling.
- **Verbindungs-Lebenszyklus (mittel):** Reconnect der ausgehenden WS, Erkennen von
  Session-Ende, konsistente UI-Zustände.
- **Account-Freischaltung (mittel):** Falls der Nutzer-Account Channels nicht
  erlaubt, registriert sich der Channel nicht (stiller Fehlschlag). → im Spike prüfen.
- **Polyglotte Auslieferung (niedrig–mittel):** Web-App (Maven) **und**
  Channel-Server (Node-Paket) müssen gebaut/verteilt und beim Nutzer gestartet
  werden. → Setup-Doku + Startbefehl bereitstellen.

## 6. Referenzen

- [Konzept](01-konzept-claude-chat.md)
- Offizielle Doku: `code.claude.com/docs/de/channels`,
  `code.claude.com/docs/en/channels-reference`
