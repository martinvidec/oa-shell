// oa-shell Chat-Frontend (Vanilla JS).
// Verbindet sich mit /ws, listet Sessions (/api/sessions), sendet Nachrichten und
// zeigt Replies inkrementell an. Routing/Ownership liegen im Backend (#9).
(() => {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const sessionList = $('session-list');
  const noSessions = $('no-sessions');
  const messagesEl = $('messages');
  const chatEmpty = $('chat-empty');
  const form = $('chat-form');
  const input = $('chat-input');
  const working = $('working');
  const wsStatus = $('ws-status');

  // Nur auf der angemeldeten Seite aktiv.
  if (!sessionList) return;

  let ws = null;
  let currentSessionId = null;

  function setStatus(connected) {
    wsStatus.textContent = connected ? 'verbunden' : 'getrennt';
    wsStatus.classList.toggle('status--on', connected);
    wsStatus.classList.toggle('status--off', !connected);
  }

  function setWorking(on) {
    working.hidden = !on;
  }

  function appendMessage(role, text) {
    const el = document.createElement('div');
    el.className = 'msg msg--' + role;
    el.textContent = text;
    messagesEl.appendChild(el);
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  function showSession(sessionId) {
    currentSessionId = sessionId;
    messagesEl.innerHTML = '';
    chatEmpty.hidden = true;
    messagesEl.hidden = false;
    form.hidden = false;
    setWorking(false);
    [...sessionList.children].forEach((li) =>
      li.classList.toggle('active', String(li.dataset.id) === String(sessionId)),
    );
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'selectSession', sessionId }));
    }
    input.focus();
  }

  async function loadSessions() {
    let items = [];
    try {
      const res = await fetch('/api/sessions', { headers: { Accept: 'application/json' } });
      if (res.ok) items = await res.json();
    } catch (_) {
      /* offline */
    }
    sessionList.innerHTML = '';
    noSessions.hidden = items.length > 0;
    for (const s of items) {
      const li = document.createElement('li');
      li.dataset.id = s.id;
      li.className = 'session' + (s.status === 'CONNECTED' ? '' : ' session--off');
      li.innerHTML =
        '<span class="session__name"></span><span class="session__dot" title="' + s.status + '"></span>';
      li.querySelector('.session__name').textContent = s.name;
      li.addEventListener('click', () => showSession(s.id));
      sessionList.appendChild(li);
    }
  }

  function connect() {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    ws = new WebSocket(proto + '://' + location.host + '/ws');
    ws.onopen = () => {
      setStatus(true);
      if (currentSessionId != null) ws.send(JSON.stringify({ type: 'selectSession', sessionId: currentSessionId }));
    };
    ws.onclose = () => {
      setStatus(false);
      setTimeout(connect, 2000);
    };
    ws.onmessage = (ev) => {
      let m;
      try { m = JSON.parse(ev.data); } catch (_) { return; }
      if (m.type === 'reply') {
        appendMessage('assistant', m.text);
        setWorking(false);
      } else if (m.type === 'permission_request') {
        appendPermission(m);
      } else if (m.type === 'error') {
        appendMessage('error', m.message || 'Fehler');
        setWorking(false);
      }
    };
  }

  // Minimaler Freigabe-Block (der polierte Dialog folgt in #12).
  function appendPermission(req) {
    const card = document.createElement('div');
    card.className = 'msg msg--permission';
    const title = document.createElement('div');
    title.className = 'perm__title';
    title.textContent = 'Claude möchte ' + (req.tool_name || 'ein Tool') + ' ausführen';
    card.appendChild(title);
    if (req.description) {
      const d = document.createElement('div');
      d.className = 'perm__desc';
      d.textContent = req.description;
      card.appendChild(d);
    }
    if (req.input_preview) {
      const pre = document.createElement('pre');
      pre.className = 'perm__preview';
      pre.textContent = req.input_preview;
      card.appendChild(pre);
    }
    const actions = document.createElement('div');
    actions.className = 'perm__actions';
    const decide = (behavior) => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'permissionVerdict', request_id: req.request_id, behavior }));
      }
      actions.remove();
      const result = document.createElement('div');
      result.className = 'perm__result';
      result.textContent = behavior === 'allow' ? '✓ erlaubt' : '✗ abgelehnt';
      card.appendChild(result);
    };
    const allow = document.createElement('button');
    allow.textContent = 'Erlauben';
    allow.addEventListener('click', () => decide('allow'));
    const deny = document.createElement('button');
    deny.className = 'btn-deny';
    deny.textContent = 'Ablehnen';
    deny.addEventListener('click', () => decide('deny'));
    actions.appendChild(allow);
    actions.appendChild(deny);
    card.appendChild(actions);
    messagesEl.appendChild(card);
    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const text = input.value.trim();
    if (!text || currentSessionId == null || !ws || ws.readyState !== WebSocket.OPEN) return;
    ws.send(JSON.stringify({ type: 'chat', sessionId: currentSessionId, text }));
    appendMessage('user', text);
    input.value = '';
    setWorking(true);
  });

  $('refresh-sessions').addEventListener('click', loadSessions);

  loadSessions();
  connect();
})();
