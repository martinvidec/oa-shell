# Konzept: Chat-Darstellung — Markdown & Diffs (Claude-Code-nahe UX)

## 1. Zusammenfassung

Die Chat-Nachrichten der oa-shell-Web-UI werden aktuell als reiner Text angezeigt.
Ziel ist, Claude-Antworten als **gerendertes Markdown** darzustellen und **Diffs**
farbig/strukturiert anzuzeigen, damit die Chat-Darstellung möglichst nah an die
vertraute Claude-Code-UX herankommt.

## 2. Problemstellung

- `renderMessage()` (`static/js/chat.js`) setzt `el.textContent = text`. Markdown in
  Claude-Antworten (Überschriften, Listen, Code-Blöcke, Inline-Code, Tabellen)
  erscheint als **Rohtext** und ist schlecht lesbar.
- Von Claude im Antworttext mitgelieferte **Code-Änderungen/Diffs** sind nicht als
  solche erkennbar — kein `+`/`-`-Highlighting, keine Datei-/Hunk-Struktur.
- Die Darstellung weicht spürbar von der gewohnten Claude-Code-Terminal-UX ab.

## 3. Zielsetzung

- Claude-Antworten als gerendertes Markdown: Überschriften, fett/kursiv, Listen,
  Inline-Code, Code-Blöcke (Syntax-Hervorhebung wünschenswert), Links, Blockquotes,
  Tabellen.
- Diffs farbig/strukturiert: Zeilen `+`/`-` in grün/rot, monospace, ggf. Datei- und
  Hunk-Header.
- **XSS-sicher**: kein ungefiltertes `innerHTML`; präparierte Nachrichten dürfen
  keinen aktiven Inhalt ausführen.
- Leichtgewichtig und zur bestehenden Vanilla-JS-/Thymeleaf-Architektur passend
  (keine schweren Frameworks; öffentliches Repo, keine Secrets/CDN-Abhängigkeiten).
- **Messbar:** gängige Markdown-Elemente und ein unified-Diff werden korrekt
  gerendert; ein XSS-Testvektor (`<img onerror>`, `<script>`, `javascript:`-Link)
  wird neutralisiert.

## 4. Lösungsidee

- **Markdown:** In `renderMessage()` einen Markdown→HTML-Schritt für die betreffenden
  Rollen einführen, mit striktem Sanitizing. Zwei grundsätzliche Wege:
  - (a) **Vendored, geprüfte Mini-Libs** (z. B. `marked` als Parser + `DOMPurify`
    als Sanitizer), offline als statische Assets eingebunden.
  - (b) **Kleiner Eigenbau-Renderer** für ein definiertes Markdown-Subset, der die
    Ausgabe ausschließlich über DOM-APIs (`createElement`/`textContent`) aufbaut und
    damit ohne `innerHTML`/Sanitizer auskommt.
- **Diffs:** Fenced ` ```diff `-Blöcke (und optional erkannte unified-Diffs) als
  eigene Diff-Komponente rendern (Zeilenfärbung, monospace, optional Hunk-Header).
  Baut auf der Code-Block-Behandlung des Markdown-Renderers auf.
- **Rollenabhängig:** Markdown-Rendering primär für `assistant`; für `user`/`system`
  zu entscheiden (siehe offene Fragen).
- Styling der neuen Elemente in `static/css/chat.css` (Markdown-Typografie +
  Diff-Farben).

## 5. Betroffene Komponenten

| Komponente | Betroffenheit |
|---|---|
| `app/src/main/resources/static/js/chat.js` | Rendering-Hook in `renderMessage()`/`appendMessage()`; neuer Markdown-/Diff-Renderer |
| `app/src/main/resources/static/css/chat.css` | Styles für Markdown-Elemente und Diff-Zeilen |
| `app/src/main/resources/templates/index.html` | Einbindung neuer JS/CSS-Assets (bei vendored Libs) |
| `app/src/main/resources/static/js/vendor/*` (neu, optional) | vendored Parser/Sanitizer (bei Weg 4a) |

Für den hier betrachteten Umfang sind **keine** Backend- oder Channel-Protokoll-
Änderungen nötig — gerendert wird der bereits vorhandene `reply`-Text im Client.

## 6. Abgrenzung

- **In diesem Vorhaben umgesetzt:** rein clientseitiges **Markdown-Rendering** der
  `assistant`-Nachrichten (Claude-Antworten) auf Basis der heute vorhandenen Daten
  (`reply`-Text). Keine Backend-/Channel-Änderungen.
- **Zurückgestellt (eigenes Backlog-Issue, upstream-blockiert):** jegliche
  **Diff-Darstellung**. Grund: siehe Entscheidung D1 unten — der Channel erhält im
  aktuellen Research-Preview-Protokoll keine Tool-Use-Payloads, nur einen auf ~200
  Zeichen gekürzten `input_preview` im Permission-Request. Echte automatische
  `Edit`/`Write`-Diffs (wie im Claude-Code-Terminal) sind damit **nicht möglich** und
  hängen an einer Upstream-Erweiterung des Channels-Protokolls.
- Kein Rich-Text-/WYSIWYG-Editor für die Eingabe.
- Keine serverseitige Markdown-Verarbeitung (bleibt im Client).

## 7. Getroffene Entscheidungen

- **D1 — Diffs:** zurückgestellt. Umfang B (automatische echte Tool-Diffs) ist im
  Preview-Protokoll technisch nicht umsetzbar (Channel bekommt keine Tool-Payloads,
  nur gekürzten Permission-Preview). Es wird **nur** ein Backlog-Issue angelegt, das
  auf die fehlende Upstream-Fähigkeit verweist. Für dieses Vorhaben: **keine**
  Diff-Sonderbehandlung (ein ` ```diff `-Block wird lediglich als normaler
  Code-Block dargestellt).
- **D2 — Engine:** vendored `marked` (Parser) + `DOMPurify` (Sanitizer), offline als
  statische Assets.
- **D3 — Syntax-Hervorhebung:** ja, vendored `highlight.js` für Code-Blöcke.
- **D4 — Rollen:** Markdown nur für `assistant`; `user`/`system`/`error` bleiben
  Plaintext.
- **D5 — Markdown-Umfang:** GitHub-Flavored-Markdown (Überschriften, Betonung,
  Listen/Task-Listen, Inline-Code, Code-Blöcke, Links, Blockquotes, Tabellen).
