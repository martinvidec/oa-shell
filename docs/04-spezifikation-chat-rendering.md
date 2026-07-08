# Spezifikation: Chat-Darstellung — Markdown & Diffs

## 1. Übersicht

Clientseitiges **Markdown-Rendering** der `assistant`-Nachrichten im Web-Chat mit
vendored `marked` (Parser) + `DOMPurify` (Sanitizer) + `highlight.js` (Syntax). Keine
Backend-/Channel-Änderung. Diff-Darstellung ist zurückgestellt (D1) und wird nur als
blockiertes Backlog-Issue geführt. Grundlage: [Konzept](01-konzept-chat-rendering.md),
[Ist-Analyse](02-ist-analyse-chat-rendering.md),
[Anforderungsanalyse](03-anforderungsanalyse-chat-rendering.md).

## 2. Technisches Design

### 2.1 Architektur

- Neue vendored Assets unter `static/js/vendor/`:
  `marked.min.js` (global `marked`), `purify.min.js` (global `DOMPurify`),
  `highlight.min.js` (global `hljs`) sowie ein highlight.js-Theme
  `static/css/vendor/hljs-theme.css`. Versionen im Dateikopf/README vermerken.
- Neues Modul `static/js/markdown.js` mit einer Funktion
  `renderMarkdownInto(el, text)`, das den Rendering-/Sanitize-/Highlight-Ablauf kapselt.
- `templates/index.html`: im `<head>` das Theme-CSS; vor `chat.js` die drei Lib-Skripte
  und `markdown.js` einbinden (klassische `<script>`-Tags, Thymeleaf `@{…}`).

### 2.2 Datenmodell

Keine Änderung. Es wird weiterhin `{ role, text }` je Nachricht gepuffert
(`sessionMessages`). Gerendert wird ausschließlich im Client.

### 2.3 Schnittstellen / Render-Ablauf

`renderMessage(role, text)` (`chat.js`) wird verzweigt:

- `role === 'assistant'`: Container `div.msg.msg--assistant` erhält ein Kind
  `div.markdown-body`, in das `renderMarkdownInto` rendert.
- sonst: unverändert `el.textContent = text`.

`renderMarkdownInto(el, text)`:
1. `const html = marked.parse(text, { gfm: true, breaks: false })` — GFM inkl.
   Tabellen/Task-Listen; Code-Highlighting über die `marked`-Highlight-Option mit
   `hljs.highlightAuto`/`hljs.highlight` (erzeugt `<span class="hljs-…">`).
2. `const clean = DOMPurify.sanitize(html, { … Allowlist … })` — erlaubt die
   Markdown-Tags inkl. `class` (für hljs) und `input[type=checkbox][disabled]`
   (Task-Listen); verbietet aktive Inhalte.
3. `DOMPurify`-Hook `afterSanitizeAttributes`: bei `A`-Elementen `target="_blank"` und
   `rel="noopener noreferrer"` setzen; unsichere Schemata (nur `http`/`https`/`mailto`)
   werden von DOMPurify ohnehin entfernt.
4. `el.innerHTML = clean`.

`showSession()` nutzt denselben `renderMessage` → beide Render-Pfade konsistent (FA-06).

### 2.4 Sicherheit

- Einziger `innerHTML`-Zuweisungspunkt ist Schritt 4, ausschließlich mit
  DOMPurify-bereinigtem HTML (NFA-01).
- DOMPurify-Allowlist bewusst eng: Block-/Inline-Markdown-Tags, `pre/code/span` mit
  `class`, `a` mit `href/target/rel`, `table`-Familie, `input[type=checkbox][disabled]`.
  Kein `style`, kein `img` mit Event-Handlern (Event-Handler generell verboten).
- Alle Libs vendored, kein CDN (NFA-02).

## 3. Implementierungsplan

### 3.1 Änderungen pro Komponente

| Komponente | Änderung | Aufwand |
|---|---|---|
| `static/js/vendor/*` (neu) | `marked`, `DOMPurify`, `highlight.js` vendoren | Klein |
| `static/css/vendor/hljs-theme.css` (neu) | highlight.js-Theme | Klein |
| `static/js/markdown.js` (neu) | `renderMarkdownInto` (parse→sanitize→hook) | Mittel |
| `static/js/chat.js` | `renderMessage` verzweigen (assistant → Markdown) | Klein |
| `static/css/chat.css` | Styles für `.markdown-body` (Typo, Listen, `pre/code`, Tabellen, Blockquote); `pre-wrap` dort neutralisieren | Mittel |
| `templates/index.html` | Theme-CSS + Lib-/`markdown.js`-Skripte einbinden | Klein |
| E2E-Test | Markdown-Rendering + XSS-Vektor prüfen | Mittel |

### 3.2 Reihenfolge der Implementierung

1. **Markdown-Kern:** `marked` + `DOMPurify` vendoren, `markdown.js`, `renderMessage`
   verzweigen, `.markdown-body`-CSS, E2E-Test (Rendering + XSS). Deckt FA-01/02/03/05/06/07,
   NFA-01–04.
2. **Syntax-Hervorhebung:** `highlight.js` + Theme vendoren, in `markdown.js` einhängen,
   Code-Block-Styles. Deckt FA-04.
3. **Diffs (Backlog, blockiert):** Issue mit Verweis auf D1 (Upstream-Tool-Events);
   keine Umsetzung bis das Protokoll dies erlaubt. Deckt FA-08.

## 4. Testplan

- **E2E (Playwright, bestehende Suite):** `assistant`-Nachricht mit gemischtem Markdown
  rendert erwartete DOM-Elemente; XSS-Vektor wird neutralisiert (kein `alert`, kein
  `onerror`-Node); `user`-Nachricht bleibt Plaintext.
- **Manuell:** Repräsentative Claude-Antwort (Überschriften, Listen, Code mit Sprache,
  Tabelle, Link) visuell prüfen; Session-Wechsel prüft den zweiten Render-Pfad.
- **Regression:** bestehende App-/Channel-Tests bleiben grün.

## 5. Migration / Deployment

Rein additive Frontend-Änderung (statische Assets + JS/CSS). Kein Datenmodell, keine
Migration, keine Konfig. Deployment mit dem normalen App-Build.

## 6. Referenzen

- [Konzept](01-konzept-chat-rendering.md)
- [Ist-Analyse](02-ist-analyse-chat-rendering.md)
- [Anforderungsanalyse](03-anforderungsanalyse-chat-rendering.md)
