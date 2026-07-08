# Anforderungsanalyse: Chat-Darstellung — Markdown & Diffs

## 1. Funktionale Anforderungen

| ID | Anforderung | Priorität | Beschreibung |
|---|---|---|---|
| FA-01 | Markdown-Rendering `assistant` | Muss | Claude-Antworten werden als gerendertes Markdown angezeigt statt als Plaintext. |
| FA-02 | Kern-Markdown | Muss | Überschriften, fett/kursiv, geordnete/ungeordnete Listen, Inline-Code, Code-Blöcke, Links, Blockquotes. |
| FA-03 | GFM-Erweiterungen | Soll | Tabellen und Task-Listen (`- [ ]`/`- [x]`). |
| FA-04 | Syntax-Hervorhebung | Soll | Code-Blöcke werden per `highlight.js` farblich hervorgehoben (Sprache aus dem Fence, sonst Auto/keine). |
| FA-05 | Links sicher öffnen | Soll | Links öffnen in neuem Tab mit `rel="noopener noreferrer"`; nur sichere Schemata (`http`/`https`/`mailto`). |
| FA-06 | Konsistenz beider Render-Pfade | Muss | Live-Nachrichten und beim Session-Wechsel neu gerenderte Nachrichten werden identisch dargestellt. |
| FA-07 | Nicht-`assistant`-Rollen unverändert | Muss | `user`, `system`, `error` bleiben Plaintext (`textContent`), inkl. bisherigem `pre-wrap`-Verhalten. |
| FA-08 | Diff-Darstellung | Kann (zurückgestellt) | Zurückgestellt bis Upstream-Tool-Events verfügbar sind (D1); nur als Backlog-Issue geführt. Bis dahin wird ein ` ```diff `-Block als normaler Code-Block gezeigt. |

## 2. Nicht-funktionale Anforderungen

| ID | Anforderung | Kategorie | Beschreibung |
|---|---|---|---|
| NFA-01 | XSS-Schutz | Sicherheit | Gerendertes HTML wird vor dem Einfügen mit `DOMPurify` (Allowlist) bereinigt; kein aktiver Inhalt (`<script>`, Event-Handler, `javascript:`) wird ausgeführt. |
| NFA-02 | Offline/keine CDN | Sicherheit/Betrieb | Libs werden vendored (im Repo), keine Laufzeit-Abhängigkeit von externen CDNs. |
| NFA-03 | Keine schweren Frameworks | Architektur | Nur eigenständige Browser-Builds; Vanilla-JS-Architektur bleibt erhalten (kein Bundler). |
| NFA-04 | Vertrag gekapselt | Architektur | Keine Änderung am Channel-Protokoll für dieses Vorhaben. |
| NFA-05 | Performance | Performance | Rendering typischer Chat-Nachrichten ohne merkliche Verzögerung. |

## 3. Akzeptanzkriterien

- [ ] Eine `assistant`-Nachricht mit Überschriften, Listen, Inline-Code, Code-Block,
      Link, Blockquote und Tabelle wird korrekt formatiert dargestellt.
- [ ] Ein Code-Block mit Sprach-Fence (z. B. ` ```java `) wird syntaxhervorgehoben.
- [ ] Ein XSS-Testvektor in einer `assistant`-Nachricht
      (`<img src=x onerror=alert(1)>`, `<script>…`, `[x](javascript:alert(1))`)
      führt **nicht** zur Ausführung; der Inhalt wird neutralisiert/entfernt.
- [ ] `user`-, `system`- und `error`-Nachrichten werden weiterhin als Plaintext
      angezeigt (Sonderzeichen nicht als Markdown interpretiert).
- [ ] Nach Session-Wechsel werden gepufferte `assistant`-Nachrichten identisch
      (markdown-gerendert) dargestellt.
- [ ] Keine externe Netzwerk-Anfrage zum Laden von Rendering-Libs (alles vendored).
- [ ] Bestehende Tests bleiben grün; ein E2E-Test deckt das Markdown-Rendering ab.

## 4. Abhängigkeiten zu anderen Anforderungen

- **D1 / FA-08 (Diffs):** hängt an einer Upstream-Erweiterung des Channels-Protokolls
  (Tool-Use-Events). Separates Backlog-Issue, blockiert.
- Baut auf der bestehenden Chat-/WebSocket-Infrastruktur auf (Issues #9/#15), keine
  Backend-Änderung.

## 5. Priorisierung

1. **Muss:** FA-01, FA-02, FA-06, FA-07, NFA-01–04.
2. **Soll:** FA-03, FA-04, FA-05, NFA-05.
3. **Kann/zurückgestellt:** FA-08 (Diffs) — nur Backlog-Issue.
