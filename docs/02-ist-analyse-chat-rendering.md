# Ist-Analyse: Chat-Darstellung — Markdown & Diffs

## 1. Aktueller Zustand

Der Web-Chat rendert jede Nachricht als **reinen Text**:

- `renderMessage(role, text)` (`static/js/chat.js:34`) erzeugt ein `div.msg.msg--<role>`
  und setzt `el.textContent = text`. Kein Markdown, keine HTML-Interpretation.
- `appendMessage(role, text)` puffert die Nachricht je Session in `sessionMessages`
  und ruft `renderMessage`. Beim Session-Wechsel rendert `showSession()` den Puffer neu
  (`static/js/chat.js:57`) — ein zweiter Rendering-Pfad, der ebenfalls angepasst werden muss.
- Nachrichten-Rollen: `user`, `assistant`, `error`, `system` (Claude-Antworten kommen
  als `reply` → `appendMessage('assistant', …)`, `static/js/chat.js:171`).
- CSS: `.msg { white-space: pre-wrap; … }` (`static/css/chat.css:79`) erhält aktuell
  Zeilenumbrüche des Plaintexts.

Assets werden ohne Build-Schritt direkt aus `static/` ausgeliefert und in
`templates/index.html` per Thymeleaf eingebunden:
`@{/css/chat.css}` (Zeile 7) und `@{/js/chat.js}` (Zeile 85). Es gibt **keinen
JS-Bundler/npm-Build** für das Frontend.

Der Channel liefert dem Chat **nur den finalen `reply`-Text** (`channel/src/protocol.ts`);
Tool-Use-Payloads (`Edit`/`Write`) sind nicht verfügbar (relevant für die
zurückgestellten Diffs, D1).

## 2. Relevante Dateien und Komponenten

| Datei/Komponente | Beschreibung | Relevanz |
|---|---|---|
| `app/src/main/resources/static/js/chat.js` | Chat-Frontend; `renderMessage`/`appendMessage`/`showSession` | **Zentral** — Rendering-Hook für Markdown |
| `app/src/main/resources/static/css/chat.css` | Styles inkl. `.msg`-Regeln (`white-space: pre-wrap`) | Styles für Markdown-Elemente; `pre-wrap` für Markdown-Nachrichten überschreiben |
| `app/src/main/resources/templates/index.html` | Bindet CSS/JS ein (Zeilen 7, 85) | Einbindung der vendored Libs (JS + highlight.js-Theme-CSS) |
| `app/src/main/resources/static/js/vendor/` (neu) | vendored `marked`, `DOMPurify`, `highlight.js` (+ Theme-CSS) | Neue statische Assets |
| `channel/src/protocol.ts` | Channel↔App/claude-Vertrag | Begründet D1 (keine Tool-Payloads → Diffs zurückgestellt) |

## 3. Bestehende Abhängigkeiten

- **Frontend:** Vanilla JS, keine Framework-/Bundler-Abhängigkeit; Libs müssen als
  eigenständige Browser-Builds (globale Symbole via `<script>`) einbindbar sein
  (`marked` UMD → `marked`, `DOMPurify` UMD → `DOMPurify`, `highlight.js` Browser-Build
  → `hljs` + Theme-CSS).
- **Backend:** Spring Boot liefert `static/` aus; keine Änderung nötig.
- Bestehende Sicherheits-Header (u. a. `X-Content-Type-Options: nosniff`,
  `Referrer-Policy`, HSTS) werden gesetzt; eine explizite Content-Security-Policy ist
  derzeit **nicht** konfiguriert.

## 4. Bekannte Einschränkungen

- **Kein JS-Build:** Libs werden **vendored** (Datei im Repo), kein npm/CDN zur
  Laufzeit (öffentliches Repo, keine externen Abhängigkeiten/Secrets).
- **Preview-Protokoll:** keine Tool-Use-Events → automatische Tool-Diffs unmöglich (D1).
- `white-space: pre-wrap` auf `.msg` würde bei HTML-Block-Inhalten zusätzliche
  Leerräume erzeugen → für Markdown-Nachrichten deaktivieren/kapseln.

## 5. Risiken bei Änderung

- **XSS (Haupt­risiko):** Markdown→HTML mit `innerHTML` ist ein Injektionsvektor.
  Muss zwingend über `DOMPurify` gefiltert werden (Allowlist; `javascript:`-Links,
  `<script>`, Event-Handler-Attribute neutralisiert). Links ggf. `rel="noopener"`
  und Ziel prüfen.
- **Zwei Render-Pfade:** `renderMessage` wird sowohl live als auch beim Session-Wechsel
  (`showSession`) genutzt — beide müssen konsistent markdown-rendern.
- **Layout/Styling:** Markdown-Elemente (Listen, Tabellen, Code-Blöcke) brauchen eigene
  Styles, ohne das bestehende Bubble-Layout zu brechen.
- **Performance:** sehr lange Nachrichten; Parsen/Highlighten im Main-Thread (bei den
  üblichen Chat-Größen unkritisch).
- **Vendored Libs:** Gewicht (`highlight.js` ist der größte Posten) und Pflege
  (Versions-Updates) im Repo.
