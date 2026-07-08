// oa-shell — Markdown-Rendering für Chat-Nachrichten (nur `assistant`).
// Rendert Markdown -> HTML (marked) und bereinigt es vor dem Einfügen (DOMPurify).
// Dies ist der EINZIGE innerHTML-Zuweisungspunkt; alle Libs sind vendored
// (static/js/vendor/), keine Laufzeit-CDN-Abhängigkeit. Siehe docs/04-spezifikation-chat-rendering.md.
(function (global) {
  'use strict';

  var marked = global.marked;
  var DOMPurify = global.DOMPurify;

  if (marked && marked.setOptions) {
    // GitHub-Flavored-Markdown (inkl. Tabellen/Task-Listen); keine harten Zeilenumbrüche.
    marked.setOptions({ gfm: true, breaks: false });
  }

  // Enge Allowlist: Markdown-Block/Inline-Tags, `class` (u. a. für die spätere
  // highlight.js-Integration, #26), Task-Listen-Checkbox. Kein style/img/script,
  // keine Event-Handler-Attribute.
  var SANITIZE_CONFIG = {
    ALLOWED_TAGS: [
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'br', 'hr',
      'strong', 'em', 'del', 'code', 'pre', 'blockquote',
      'ul', 'ol', 'li', 'a', 'span',
      'table', 'thead', 'tbody', 'tr', 'th', 'td', 'input'
    ],
    ALLOWED_ATTR: ['href', 'title', 'class', 'start', 'type', 'checked', 'disabled', 'target', 'rel'],
    ALLOW_DATA_ATTR: false
  };

  if (DOMPurify && DOMPurify.addHook) {
    DOMPurify.addHook('afterSanitizeAttributes', function (node) {
      // Links extern und ohne Referrer/Opener öffnen (unsichere Schemata entfernt
      // DOMPurify bereits selbst).
      if (node.tagName === 'A') {
        node.setAttribute('target', '_blank');
        node.setAttribute('rel', 'noopener noreferrer');
      }
      // Nur deaktivierte Task-Listen-Checkboxen zulassen; andere Inputs entfernen.
      if (node.tagName === 'INPUT') {
        if (node.getAttribute('type') !== 'checkbox') {
          if (node.parentNode) node.parentNode.removeChild(node);
        } else {
          node.setAttribute('disabled', 'disabled');
        }
      }
    });
  }

  /**
   * Rendert `text` als Markdown in `el`. Fällt bei fehlenden Libs sicher auf
   * Plaintext zurück.
   */
  function renderMarkdownInto(el, text) {
    var src = text == null ? '' : String(text);
    if (!marked || !DOMPurify) {
      el.textContent = src;
      return;
    }
    var html = marked.parse(src);
    el.innerHTML = DOMPurify.sanitize(html, SANITIZE_CONFIG);
    // Syntax-Hervorhebung NACH dem Sanitizen: hljs arbeitet auf dem bereits als Text
    // eingefügten (sicheren) Code-Inhalt und ersetzt ihn durch escapte hljs-Spans
    // (Sprache aus der `language-*`-Klasse; sonst Auto/keine). Kein neuer XSS-Vektor.
    if (global.hljs && el.querySelectorAll) {
      var blocks = el.querySelectorAll('pre code');
      for (var i = 0; i < blocks.length; i++) {
        try {
          global.hljs.highlightElement(blocks[i]);
        } catch (e) {
          /* ignorieren — Code bleibt unhervorgehoben, aber lesbar */
        }
      }
    }
  }

  global.renderMarkdownInto = renderMarkdownInto;
})(window);
