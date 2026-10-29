/* FlowSpace frontend logic.
   Data layer = browser localStorage so the UI runs standalone for demos.
   JAVA INTEGRATION: replace the DB.* / Auth.* calls with fetch('/api/...') to the Java servlets.
   DEMO ONLY: passwords are kept in plain text here; the Java backend must store a BCrypt/PBKDF2 hash. */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = d => d ? new Date(d + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '-';
const ymd = ms => new Date(ms).toLocaleDateString('en-CA');
const addDays = (s, n) => { const d = new Date(s + 'T00:00:00Z'); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10); };
const daysBetween = (a, b) => Math.round((Date.parse(b + 'T00:00:00Z') - Date.parse(a + 'T00:00:00Z')) / 864e5);
const avg = a => a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0;
const MOOD = { HAPPY: 5, CALM: 4, NEUTRAL: 3, TIRED: 2, ANXIOUS: 2, IRRITABLE: 2, SAD: 1 };
const label = s => s.charAt(0) + s.slice(1).toLowerCase().replace('_', ' ');

const empty = () => ({
  users: [{ id: 1, name: 'Demo User', email: 'demo@flowspace.test', password: 'password', createdAt: Date.now() }],
  notes: [],
  tasks: [],
  periods: [],
  moods: [],
  seq: 10
});
const DB = {
  d: (() => {
    try {
      const stored = JSON.parse(localStorage.getItem('flowspace'));
      if (stored && stored.users && stored.users.length) return stored;
    } catch (e) {}
    const init = empty();
    localStorage.setItem('flowspace', JSON.stringify(init));
    return init;
  })(),
  save() { localStorage.setItem('flowspace', JSON.stringify(this.d)); },
  add(t, row) { row.id = this.d.seq++; this.d[t].push(row); this.save(); return row; },
  mine(t) { return (this.d[t] || []).filter(r => r.userId === Auth.id()); },
  upd(t, id, patch) { const r = (this.d[t] || []).find(x => x.id === id && x.userId === Auth.id()); if (r) { Object.assign(r, patch); this.save(); } },
  del(t, id) { this.d[t] = (this.d[t] || []).filter(x => !(x.id === id && x.userId === Auth.id())); this.save(); }
};
const Auth = {
  id: () => +localStorage.getItem('fs_session') || 1,
  user() { return (DB.d.users || []).find(u => u.id === this.id()) || DB.d.users[0] || { name: 'Demo User', id: 1 }; },
  login(id) { localStorage.setItem('fs_session', id); },
  logout() { localStorage.removeItem('fs_session'); location.href = 'index.html'; }
};

function say(msg) {
  let t = $('#toast'); if (!t) { t = document.createElement('div'); t.id = 'toast'; t.setAttribute('role', 'status'); document.body.appendChild(t); }
  t.textContent = msg; t.classList.add('on'); clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove('on'), 2200);
}
function onSubmit(f, fn) {
  FSV.live(f);
  f.addEventListener('submit', e => { e.preventDefault(); if (FSV.check(f)) fn(Object.fromEntries(new FormData(f))); });
}
function resetForm(f, btn) { f.reset(); const h = f.elements.namedItem('id'); if (h) h.value = ''; FSV.clear(f); if (btn) $('button.btn', f).textContent = btn; sliders(f); }
function sliders(r = document) { $$('input[type=range]', r).forEach(s => { const o = s.parentElement.querySelector('output'); if (o) o.textContent = s.value; }); }
document.addEventListener('input', e => { if (e.target.type === 'range') sliders(e.target.form); });

function nav(page) {
  const P = [['dashboard', 'Dashboard'], ['notes', 'Notes'], ['tasks', 'Tasks'], ['wellness', 'Wellness'], ['analytics', 'Insights']];
  const user = Auth.user();
  $('#nav').innerHTML = '<div class="brand">FlowSpace</div>' +
    `<div class="nav-search-container" id="globalSearchContainer">
      <div class="nav-search-box">
        <span class="search-icon" aria-hidden="true">&#128269;</span>
        <input type="search" id="globalSearchInput" placeholder="Search notes, tasks, mood..." autocomplete="off" aria-label="Global search">
        <span class="search-kbd">/</span>
        <button type="button" class="search-clear" id="searchClearBtn" aria-label="Clear search">&times;</button>
        <div id="searchResultsDropdown" class="search-results-dropdown" role="region" aria-live="polite"></div>
      </div>
    </div>` +
    P.map(([p, l]) => `<a href="${p}.html" class="${p === page ? 'on' : ''}">${l}</a>`).join('') +
    `<p class="who">${esc(user ? user.name : 'Demo User')}</p><button class="btn ghost" id="out">Log out</button>`;
  $('#out').onclick = Auth.logout;
  if (window.GlobalSearch && typeof window.GlobalSearch.bindEvents === 'function') {
    window.GlobalSearch.bindEvents();
  }
}

function cycleInfo() {
  const p = DB.mine('periods').sort((a, b) => a.start.localeCompare(b.start)); if (!p.length) return null;
  const last = p[p.length - 1]; let len = last.length;
  if (p.length > 1) len = Math.round(avg(p.slice(1).map((x, i) => daysBetween(p[i].start, x.start))));
  len = Math.min(60, Math.max(15, len));
  return { avg: len, count: p.length, last: last.start, next: addDays(last.start, len) };
}

const pages = {
  login() {
    const f = $('#f');
    const demoBtn = $('#demoBtn');
    if (demoBtn) {
      demoBtn.onclick = () => {
        Auth.login(1);
        location.href = 'dashboard.html';
      };
    }
    onSubmit(f, v => {
      const u = DB.d.users.find(u => u.email === v.email.trim().toLowerCase() && u.password === v.password);
      if (!u) return FSV.fail(f, 'password', 'Email or password is incorrect.');
      Auth.login(u.id); location.href = 'dashboard.html';
    });
  },
  register() {
    const f = $('#f');
    onSubmit(f, v => {
      const email = v.email.trim().toLowerCase();
      if (DB.d.users.some(u => u.email === email)) return FSV.fail(f, 'email', 'An account with this email already exists.');
      const u = DB.add('users', { name: v.name.trim(), email, password: v.password, createdAt: Date.now() });
      Auth.login(u.id); location.href = 'dashboard.html';
    });
  },
  dashboard() {
    const notes = DB.mine('notes'), tasks = DB.mine('tasks'), moods = DB.mine('moods'), c = cycleInfo();
    $('#name').textContent = Auth.user().name.split(' ')[0];
    $('#sNotes').textContent = notes.length;
    $('#sTasks').textContent = tasks.filter(t => t.status !== 'DONE').length;
    $('#sMoods').textContent = moods.length;
    $('#sNext').textContent = c ? fmt(c.next) : 'Not logged';
    const acts = [...notes.map(n => ({ at: n.updatedAt, t: 'Note: ' + n.title })), ...tasks.map(t => ({ at: t.createdAt, t: 'Task: ' + t.title })),
      ...moods.map(m => ({ at: m.createdAt, t: 'Wellness log for ' + fmt(m.date) }))].sort((a, b) => b.at - a.at).slice(0, 5);
    $('#recent').innerHTML = acts.length ? acts.map(a => `<li>${esc(a.t)} <span class="mute">(${fmt(ymd(a.at))})</span></li>`).join('') : '<li class="empty">Nothing here yet. Add a note, task or wellness log to get started.</li>';
  },
  notes() {
    const f = $('#f'), draw = () => {
      const q = $('#q').value.trim().toLowerCase(), c = $('#cat').value;
      const rows = DB.mine('notes').filter(n => (!c || n.category === c) && (n.title + ' ' + n.content).toLowerCase().includes(q)).sort((a, b) => b.updatedAt - a.updatedAt);
      $('#list').innerHTML = rows.length ? rows.map(n => `<article class="card"><div class="row"><h3>${esc(n.title)}</h3><span class="tag">${esc(n.category)}</span></div>
        <p>${esc(n.content.slice(0, 160))}${n.content.length > 160 ? '...' : ''}</p><small class="mute">Updated ${fmt(ymd(n.updatedAt))}</small>
        <div class="actions"><button class="btn ghost" data-act="edit" data-id="${n.id}">Edit</button><button class="btn ghost" data-act="del" data-id="${n.id}">Delete</button></div></article>`).join('')
        : '<p class="empty">No notes found. Write one with the form.</p>';
    };
    onSubmit(f, v => {
      const row = { title: v.title.trim(), category: v.category, content: v.content.trim(), updatedAt: Date.now() };
      if (v.id) {
        DB.upd('notes', +v.id, row);
        say('Note updated');
        fetch('/api/notes', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-User-Id': String(Auth.id()) }, body: JSON.stringify({ id: +v.id, ...row }) }).catch(() => {});
      } else {
        const added = DB.add('notes', { ...row, userId: Auth.id(), createdAt: Date.now() });
        say('Note added');
        fetch('/api/notes', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-User-Id': String(Auth.id()) }, body: JSON.stringify(added) }).catch(() => {});
      }
      resetForm(f, 'Add note'); draw();
    });
    $('#q').oninput = $('#cat').onchange = draw;
    $('#list').onclick = e => {
      const b = e.target.closest('button'); if (!b) return; const id = +b.dataset.id;
      if (b.dataset.act === 'del' && confirm('Delete this note?')) {
        DB.del('notes', id);
        fetch(`/api/notes?id=${id}`, { method: 'DELETE', headers: { 'X-User-Id': String(Auth.id()) } }).catch(() => {});
        say('Note deleted'); draw();
      }
      if (b.dataset.act === 'edit') { const n = DB.mine('notes').find(x => x.id === id); f.elements.id.value = id; f.elements.title.value = n.title; f.elements.category.value = n.category; f.elements.content.value = n.content; $('button.btn', f).textContent = 'Save changes'; f.elements.title.focus(); }
    };
    draw();
  },
  tasks() {
    const f = $('#f'), NEXT = { TODO: ['IN_PROGRESS', 'Start'], IN_PROGRESS: ['DONE', 'Complete'], DONE: ['TODO', 'Reopen'] }, PR = { HIGH: 0, MEDIUM: 1, LOW: 2 };
    const draw = () => {
      const by = $('#sort').value, today = FSV.today(), all = DB.mine('tasks').sort((a, b) => by === 'priority' ? PR[a.priority] - PR[b.priority] : a.due.localeCompare(b.due));
      ['TODO', 'IN_PROGRESS', 'DONE'].forEach(s => {
        const rows = all.filter(t => t.status === s);
        $('#c-' + s).innerHTML = rows.map(t => `<article class="card"><div class="row"><h3>${esc(t.title)}</h3><span class="tag ${t.priority}">${label(t.priority)}</span></div>
          <small class="${t.due < today && s !== 'DONE' ? 'late' : 'mute'}">Due ${fmt(t.due)}${t.due < today && s !== 'DONE' ? ' (overdue)' : ''}</small>
          <div class="actions"><button class="btn ghost" data-act="move" data-id="${t.id}">${NEXT[s][1]}</button><button class="btn ghost" data-act="edit" data-id="${t.id}">Edit</button><button class="btn ghost" data-act="del" data-id="${t.id}">Delete</button></div></article>`).join('')
          || '<p class="mute">Nothing here.</p>';
        $('#n-' + s).textContent = rows.length;
      });
    };
    onSubmit(f, v => {
      const row = { title: v.title.trim(), due: v.due, priority: v.priority };
      if (v.id) {
        DB.upd('tasks', +v.id, row);
        say('Task updated');
        fetch('/api/tasks', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-User-Id': String(Auth.id()) }, body: JSON.stringify({ id: +v.id, ...row }) }).catch(() => {});
      } else {
        const added = DB.add('tasks', { ...row, userId: Auth.id(), status: 'TODO', createdAt: Date.now() });
        say('Task added');
        fetch('/api/tasks', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-User-Id': String(Auth.id()) }, body: JSON.stringify(added) }).catch(() => {});
      }
      resetForm(f, 'Add task'); draw();
    });
    $('#sort').onchange = draw;
    $('.kan').onclick = e => {
      const b = e.target.closest('button'); if (!b) return; const id = +b.dataset.id, t = DB.mine('tasks').find(x => x.id === id);
      if (b.dataset.act === 'move') {
        const newStatus = NEXT[t.status][0];
        DB.upd('tasks', id, { status: newStatus });
        fetch('/api/tasks', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-User-Id': String(Auth.id()) }, body: JSON.stringify({ id, ...t, status: newStatus }) }).catch(() => {});
        draw();
      }
      if (b.dataset.act === 'del' && confirm('Delete this task?')) {
        DB.del('tasks', id);
        fetch(`/api/tasks?id=${id}`, { method: 'DELETE', headers: { 'X-User-Id': String(Auth.id()) } }).catch(() => {});
        draw();
      }
      if (b.dataset.act === 'edit') { f.elements.id.value = id; f.elements.title.value = t.title; f.elements.due.value = t.due; f.elements.priority.value = t.priority; $('button.btn', f).textContent = 'Save changes'; f.elements.title.focus(); }
    };
    draw();
  },
  wellness() {
    const cf = $('#cycle'), mf = $('#mood'); let cm = new Date(); cm.setDate(1);
    const cal = () => {
      const c = cycleInfo(), starts = new Set(DB.mine('periods').map(p => p.start)), y = cm.getFullYear(), m = cm.getMonth(), first = new Date(y, m, 1).getDay();
      let h = ['S', 'M', 'T', 'W', 'T', 'F', 'S'].map(d => `<b>${d}</b>`).join('') + '<span></span>'.repeat(first);
      for (let d = 1, n = new Date(y, m + 1, 0).getDate(); d <= n; d++) {
        const s = `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
        h += `<span class="${starts.has(s) ? 'p' : c && s === c.next ? 'n' : ''}">${d}</span>`;
      }
      $('#cal').innerHTML = h; $('#calTitle').textContent = cm.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
      $('#pred').textContent = c ? `Next period expected around ${fmt(c.next)} (average cycle ${c.avg} days, ${c.count} logged).` : 'Log your first cycle to see a prediction.';
    };
    const list = () => {
      const m = DB.mine('moods').sort((a, b) => b.date.localeCompare(a.date)).slice(0, 7);
      $('#moods').innerHTML = m.length ? m.map(x => `<tr><td>${fmt(x.date)}</td><td>${label(x.mood)}</td><td>${x.pain}/10</td><td>${x.sleep} h</td><td>${x.water}</td><td>${esc(x.symptoms) || '-'}</td></tr>`).join('') : '<tr><td colspan="6" class="mute">No entries yet.</td></tr>';
    };
    onSubmit(cf, v => {
      const ex = DB.mine('periods').find(p => p.start === v.start);
      if (ex) DB.upd('periods', ex.id, { length: +v.length }); else DB.add('periods', { userId: Auth.id(), start: v.start, length: +v.length, createdAt: Date.now() });
      say('Cycle saved'); cal();
    });
    onSubmit(mf, v => {
      const row = { date: v.date, mood: v.mood, symptoms: v.symptoms.trim(), pain: +v.pain, sleep: +v.sleep, water: +v.water }, ex = DB.mine('moods').find(m => m.date === v.date);
      if (ex) DB.upd('moods', ex.id, row); else DB.add('moods', { ...row, userId: Auth.id(), createdAt: Date.now() });
      say('Entry saved'); list();
    });
    $('#prev').onclick = () => { cm.setMonth(cm.getMonth() - 1); cal(); };
    $('#next').onclick = () => { cm.setMonth(cm.getMonth() + 1); cal(); };
    mf.elements.date.value = FSV.today(); sliders(mf); cal(); list();
  },
  analytics() {
    const t = DB.mine('tasks'), done = t.filter(x => x.status === 'DONE').length, pct = t.length ? Math.round(done / t.length * 100) : 0, today = FSV.today();
    $('#done').innerHTML = t.length ? `<p><b>${pct}%</b> complete (${done} of ${t.length})</p><div class="meter"><i style="width:${pct}%"></i></div>
      <p class="mute">To do ${t.filter(x => x.status === 'TODO').length} · In progress ${t.filter(x => x.status === 'IN_PROGRESS').length} · Overdue ${t.filter(x => x.status !== 'DONE' && x.due < today).length}</p>` : '<p class="empty">Add tasks to see completion stats.</p>';
    const m = DB.mine('moods').sort((a, b) => a.date.localeCompare(b.date)), last = m.slice(-7);
    $('#trend').innerHTML = last.length ? last.map(x => `<div class="col" title="${label(x.mood)}"><i style="height:${MOOD[x.mood] * 20}%"></i><small>${x.date.slice(5)}</small></div>`).join('') : '<p class="empty">Log your mood to see a trend.</p>';
    const c = cycleInfo();
    $('#cyc').innerHTML = c ? `<p><b>${c.avg} days</b> average cycle</p><p class="mute">${c.count} cycle${c.count > 1 ? 's' : ''} logged. Next expected around ${fmt(c.next)}.</p>` : '<p class="empty">Log a cycle to see statistics.</p>';
    const ins = [];
    if (m.length >= 3) {
      ins.push(`Across ${m.length} entries you average ${avg(m.map(x => x.sleep)).toFixed(1)} hours of sleep, ${avg(m.map(x => x.water)).toFixed(1)} glasses of water and pain level ${avg(m.map(x => x.pain)).toFixed(1)}/10.`);
      const a = m.filter(x => x.sleep >= 7), b = m.filter(x => x.sleep < 7);
      if (a.length && b.length) ins.push(`Your mood score averages ${avg(a.map(x => MOOD[x.mood])).toFixed(1)}/5 on days with 7+ hours of sleep versus ${avg(b.map(x => MOOD[x.mood])).toFixed(1)}/5 on shorter nights.`);
    }
    if (t.length) ins.push(`${done} of ${t.length} tasks are done.`);
    $('#ins').innerHTML = ins.length ? ins.map(i => `<li>${esc(i)}</li>`).join('') : '<li class="empty">Insights appear once you have a few wellness entries and tasks.</li>';
  }
};

const page = document.body.dataset.page;
if (!['login', 'register'].includes(page) && !Auth.user()) location.replace('index.html');
else { if ($('#nav')) nav(page); pages[page](); }
