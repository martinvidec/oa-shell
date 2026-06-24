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
    loadFileTree();
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
        loadFileTree(); // Auto-Refresh nach Schreibaktionen (Soll)
      } else if (m.type === 'permission_request') {
        enqueuePermission(m);
      } else if (m.type === 'error') {
        appendMessage('error', m.message || 'Fehler');
        setWorking(false);
      }
    };
  }

  // --- Freigabe-Dialog (Permission-Relay) ---
  // Mehrere gleichzeitige Anfragen werden nacheinander modal abgearbeitet.
  const permModal = $('perm-modal');
  const permQueue = [];
  let activePerm = null;

  function enqueuePermission(req) {
    permQueue.push(req);
    showNextPermission();
  }

  function showNextPermission() {
    if (activePerm || permQueue.length === 0 || !permModal) return;
    activePerm = permQueue.shift();
    $('perm-tool').textContent = activePerm.tool_name || '?';
    $('perm-desc').textContent = activePerm.description || '';
    const pre = $('perm-preview');
    pre.textContent = activePerm.input_preview || '';
    pre.hidden = !activePerm.input_preview;
    $('perm-reason').value = '';
    permModal.hidden = false;
    $('perm-allow').focus();
  }

  function decidePermission(behavior) {
    const req = activePerm;
    if (!req) return;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'permissionVerdict', request_id: req.request_id, behavior }));
      if (behavior === 'deny') {
        const reason = $('perm-reason').value.trim();
        if (reason) {
          // Begründung als Folge-Nachricht an Claude (FA-23, Soll).
          ws.send(JSON.stringify({ type: 'chat', sessionId: req.sessionId, text: reason }));
          appendMessage('user', reason);
        }
      }
    }
    appendMessage('system', 'Freigabe für ' + (req.tool_name || 'Tool') + ': '
      + (behavior === 'allow' ? 'erlaubt' : 'abgelehnt'));
    permModal.hidden = true;
    activePerm = null;
    showNextPermission();
  }

  if (permModal) {
    $('perm-allow').addEventListener('click', () => decidePermission('allow'));
    $('perm-deny').addEventListener('click', () => decidePermission('deny'));
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

  // --- Datei-Browser (read-only) ---
  const fileTreeEl = $('file-tree');
  const fileModal = $('file-modal');

  async function fetchJson(url) {
    try {
      const res = await fetch(url, { headers: { Accept: 'application/json' } });
      if (!res.ok) return { error: 'HTTP ' + res.status };
      return await res.json();
    } catch (_) {
      return { error: 'offline' };
    }
  }

  function loadFileTree() {
    if (!fileTreeEl) return;
    fileTreeEl.innerHTML = '';
    if (currentSessionId == null) return;
    renderDir('.', fileTreeEl);
  }

  async function renderDir(dirPath, container) {
    const data = await fetchJson(
      '/api/sessions/' + currentSessionId + '/files?path=' + encodeURIComponent(dirPath),
    );
    if (!data || data.error || !Array.isArray(data.entries)) {
      const note = document.createElement('div');
      note.className = 'file-note';
      note.textContent = '(' + ((data && data.error) || 'leer') + ')';
      container.appendChild(note);
      return;
    }
    const entries = data.entries.slice().sort((a, b) =>
      a.type === b.type ? a.name.localeCompare(b.name) : a.type === 'dir' ? -1 : 1,
    );
    for (const e of entries) {
      const childPath = dirPath === '.' ? e.name : dirPath + '/' + e.name;
      const row = document.createElement('div');
      row.className = 'file-row file-row--' + e.type;
      if (e.type === 'dir') {
        const box = document.createElement('div');
        box.className = 'file-children';
        box.hidden = true;
        let loaded = false;
        row.textContent = '▸ ' + e.name;
        row.addEventListener('click', () => {
          box.hidden = !box.hidden;
          row.textContent = (box.hidden ? '▸ ' : '▾ ') + e.name;
          if (!loaded && !box.hidden) {
            loaded = true;
            renderDir(childPath, box);
          }
        });
        container.appendChild(row);
        container.appendChild(box);
      } else {
        row.textContent = e.name;
        row.addEventListener('click', () => viewFile(childPath));
        container.appendChild(row);
      }
    }
  }

  async function viewFile(filePath) {
    $('file-modal-path').textContent = filePath;
    const pre = $('file-modal-content');
    pre.textContent = 'lädt …';
    fileModal.hidden = false;
    const data = await fetchJson(
      '/api/sessions/' + currentSessionId + '/file?path=' + encodeURIComponent(filePath),
    );
    if (!data || data.error) pre.textContent = 'Fehler: ' + ((data && data.error) || 'unbekannt');
    else if (data.binary) pre.textContent = '(binäre Datei – ' + data.size + ' Bytes)';
    else if (data.truncated) pre.textContent = '(Datei zu groß – ' + data.size + ' Bytes)';
    else pre.textContent = data.content || '';
  }

  if (fileTreeEl) {
    $('refresh-files').addEventListener('click', loadFileTree);
    $('file-modal-close').addEventListener('click', () => { fileModal.hidden = true; });
  }

  loadSessions();
  connect();
})();
