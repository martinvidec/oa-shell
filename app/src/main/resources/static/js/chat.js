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
      } else if (m.type === 'error') {
        appendMessage('error', m.message || 'Fehler');
        setWorking(false);
      }
    };
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
