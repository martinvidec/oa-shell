# Spike-Report (Issue #1): Channels-Durchstich – **Ergebnis: GO**

> Bezug: [Konzept](01-konzept-claude-chat.md) · [Spezifikation](04-spezifikation-claude-chat.md) ·
> Code: [`spike/`](../spike) · Issue #1

## 1. Ziel & Ergebnis

Validieren, dass das in der Spezifikation entworfene Channel↔App-Modell real
funktioniert – **vor** Beginn der Feature-Implementierung. **Ergebnis: GO.** Alle
Akzeptanzkriterien wurden an einer echten `claude`-Session nachgewiesen.

## 2. Validierte Akzeptanzkriterien

| Kriterium | Ergebnis | Nachweis |
|---|---|---|
| Channels für den Account freigeschaltet | ✅ | Registrierungs-Notiz „Channels (experimental) … inject directly …" erschien in interaktiver Session |
| Gepushte Channel-Events kommen an, Claude reagiert | ✅ | Probe-Events (`PROBE_EVENT_n`) wurden in der Session angezeigt/verarbeitet |
| Eigener Channel verbindet sich **ausgehend** (WS) + Sender-Gating | ✅ | `hello` am Stub, Token-Gate; deterministischer Test (7/7) |
| **Permission-Relay**: `Write` löst Request aus, remote allow/deny | ✅ | `PERMISSION_REQUEST: Write → allow`, Datei danach geschrieben |
| `reply`-Tool: Antwort an die App | ✅ | `REPLY[1]: done` am Stub |
| **File-Serving** strikt unter `cwd` | ✅ | Baum/Inhalt geliefert; `..` und absolute Pfade abgewiesen (Test) |
| Erkenntnisse/Risiken + Go/No-Go dokumentiert | ✅ | dieses Dokument |

## 3. Vorgehen

- **Deterministisch** ([`spike/test-bridge.mjs`](../spike/test-bridge.mjs), `npm run test:bridge`):
  WS-Bridge + File-Serving + cwd-Sandbox ohne Claude → **7/7 grün**.
- **Interaktiver Durchstich**: Stub-App ([`spike/stub-server.mjs`](../spike/stub-server.mjs)) +
  echte `claude`-Session (interaktiv, im Pseudo-Terminal) mit dem
  [`spike/oashell-channel.mjs`](../spike/oashell-channel.mjs). Chat injiziert →
  Claude schrieb die Datei (über Write, nach Relay-Freigabe) → `reply` zurück.

## 4. Schlüssel-Erkenntnisse

1. **Interaktive Session ist zwingend** (bestätigt die Spec-Annahme §7.1):
   `-p`/`--input-format stream-json` stellt **keine** gepushten Channel-Events zu –
   jede Nachricht re-initialisiert die Session. Erst eine **langlebige interaktive**
   Session (echtes/Pseudo-Terminal) liefert Events und Permission-Relay. → Der Nutzer
   muss `claude` interaktiv betreiben (Produktionsmodell), kein headless `-p`.
2. **Permission-Relay deckt auch MCP-Tools ab**: Sowohl `Write` als auch
   `mcp__oashell__reply` lösten ein `permission_request` aus; das Auto-Verdict des
   Stubs (`allow`) schloss den lokalen Dialog. → Im echten Betrieb braucht **jeder**
   Tool-Einsatz (inkl. des `reply`-Tools) eine Freigabe; die UI muss das abbilden.
3. **Eigener Node-Channel genügt** (kein Bun). Start:
   `claude --mcp-config <cfg> --strict-mcp-config --dangerously-load-development-channels server:oashell`.
4. **Ordner-Trust-Dialog** erscheint beim ersten Start in einem Verzeichnis
   („Is this a project you … trust?") – einmalig im Terminal zu bestätigen. Relevant
   fürs Nutzer-Setup (Channel-Consent ist mit `--mcp-config`/`--strict-mcp-config`
   umgangen, der Ordner-Trust nicht).
5. **File-Serving-Sandbox hält**: relative Ausbrüche (`..`) und absolute Pfade
   außerhalb `cwd` werden abgewiesen (der Channel behandelt eingehende Pfade
   relativ zu `cwd`).

## 5. Risiken / Implikationen für die Implementierung

- **Research Preview:** `--channels`/`--dangerously-load-development-channels` und das
  Protokoll können sich ändern → Channel-Vertrag an **einer** Stelle kapseln (NFA-06).
- **Lokaler Dialog läuft parallel:** Das Relay ersetzt den Terminal-Dialog nicht, es
  läuft parallel (erste Antwort gewinnt). Für reine Browser-UX dokumentieren, dass der
  Nutzer im Browser **oder** Terminal antworten kann.
- **Streaming:** Das `reply`-Tool liefert **ganze** Nachrichten. Echtes Token-Streaming
  in die UI ist damit nicht out-of-the-box; für v1 ganze Antworten akzeptieren oder
  partielle Reply-Aufrufe prüfen.
- **Freigabe-Last:** Da jeder Tool-Einsatz (auch `reply`) eine Freigabe auslöst, ist
  eine UX/Policy nötig (z. B. „reply" generell erlauben), sonst zu viele Dialoge.

## 6. Entscheidung

**GO.** Das Architektur-Kernstück (Modell A, Channel-Bridge, Permission-Relay,
File-Serving) ist nachgewiesen. Nächste Schritte: Scaffolding (Issues #2/#3), dann
Auth (#4–#6) usw. gemäß Implementierungsreihenfolge.
