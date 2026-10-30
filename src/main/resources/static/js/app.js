/* FlowSpace frontend logic.
   Data layer = browser localStorage with seamless Java REST API sync (/api/...).
   Enables standalone demo operation while persisting real records to the database. */
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

const empty = () => ({ users: [], notes: [], tasks: [], periods: [], moods: [], seq: 1 });
const DB = {
  d: (() => { try { return JSON.parse(localStorage.getItem('flowspace')) || empty(); } catch (e) { return empty(); } })(),
  save() { localStorage.setItem('flowspace', JSON.stringify(this.d)); },
  add(t, row) { row.id = this.d.seq++; this.d[t].push(row); this.save(); return row; },
  mine(t) { return this.d[t].filter(r => r.userId === Auth.id()); },
  upd(t, id, patch) { const r = this.d[t].find(x => x.id === id && x.userId === Auth.id()); if (r) { Object.assign(r, patch); this.save(); } },
  del(t, id) { this.d[t] = this.d[t].filter(x => !(x.id === id && x.userId === Auth.id())); this.save(); }
};

const Auth = {
  id: () => +localStorage.getItem('fs_session') || 0,
  user() { return DB.d.users.find(u => u.id === this.id()) || { name: 'Friend' }; },
  login(id) { localStorage.setItem('fs_session', id); },
  logout() {
    fetch('/api/logout', { method: 'POST', credentials: 'include' }).catch(() => {});
    localStorage.removeItem('fs_session');
    try {
      localStorage.removeItem('flowspace');
      DB.d = empty();
    } catch (e) {}
    location.href = 'index.html';
  }
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
  $('#nav').innerHTML = '<div class="brand">FlowSpace</div>' + P.map(([p, l]) => `<a href="${p}.html" class="${p === page ? 'on' : ''}">${l}</a>`).join('') +
    `<p class="who">${esc(Auth.user().name)}</p><button class="btn ghost" id="out">Log out</button>`;
  $('#out').onclick = Auth.logout;
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
    onSubmit(f, v => {
      fetch('/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email: v.email.trim().toLowerCase(), password: v.password })
      })
      .then(async res => {
        if (!res.ok) {
          const err = await res.json().catch(() => ({}));
          throw new Error(err.error || err.message || 'Login failed');
        }
        return res.json();
      })
      .then(user => {
        let u = DB.d.users.find(x => x.email === user.email);
        if (!u) {
          u = { id: user.userId, name: user.name, email: user.email, createdAt: Date.now() };
          DB.d.users.push(u);
        } else {
          u.id = user.userId;
          u.name = user.name;
        }
        DB.save();
        Auth.login(user.userId);
        location.href = 'dashboard.html';
      })
      .catch(err => {
        return FSV.fail(f, 'password', err.message || 'Email or password is incorrect.');
      });
    });
  },
  register() {
    const f = $('#f');
    onSubmit(f, v => {
      const email = v.email.trim().toLowerCase();
      fetch('/api/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ name: v.name.trim(), email, password: v.password })
      })
      .then(async res => {
        if (!res.ok) {
          const err = await res.json().catch(() => ({}));
          throw new Error(err.error || err.message || 'Registration failed');
        }
        return res.json();
      })
      .then(user => {
        const u = { id: user.userId, name: user.name, email, createdAt: Date.now() };
        DB.d.users.push(u);
        DB.save();
        Auth.login(user.userId);
        location.href = 'dashboard.html';
      })
      .catch(err => {
        if (DB.d.users.some(u => u.email === email)) return FSV.fail(f, 'email', err.message || 'An account with this email already exists.');
        const u = DB.add('users', { name: v.name.trim(), email, createdAt: Date.now() });
        Auth.login(u.id);
        location.href = 'dashboard.html';
      });
    });
  },
  dashboard() {
    // If dashboard.js is loaded, it takes precedence. Otherwise fallback to this logic.
    if (typeof pages.dashboard === 'function' && pages.dashboard !== this.dashboard) {
      pages.dashboard();
      return;
    }
  },
  notes() {
    // Notes module owned by Member 2
  },
  tasks() {
    const f = $('#f'), NEXT = { TODO: ['IN_PROGRESS', 'Start'], IN_PROGRESS: ['DONE', 'Complete'], DONE: ['TODO', 'Reopen'] }, PR = { HIGH: 0, MEDIUM: 1, LOW: 2 };

    fetch('/api/tasks', { credentials: 'include' })
      .then(r => r.ok ? r.json() : [])
      .then(serverTasks => {
        if (Array.isArray(serverTasks) && serverTasks.length > 0) {
          const uid = Auth.id();
          DB.d.tasks = DB.d.tasks.filter(t => t.userId !== uid);
          serverTasks.forEach(st => {
            DB.d.tasks.push({
              id: st.task_id,
              userId: uid,
              title: st.title,
              due: st.due_date,
              priority: st.priority,
              status: st.status,
              createdAt: st.created_at ? new Date(st.created_at).getTime() : Date.now()
            });
          });
          DB.save();
          draw();
        }
      }).catch(() => {});

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
        fetch('/api/tasks/' + v.id, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ title: row.title, due_date: row.due, priority: row.priority })
        }).catch(() => {});
      } else {
        const added = DB.add('tasks', { ...row, userId: Auth.id(), status: 'TODO', createdAt: Date.now() });
        say('Task added');
        fetch('/api/tasks', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ title: row.title, due_date: row.due, priority: row.priority })
        })
        .then(r => r.ok ? r.json() : null)
        .then(c => {
          if (c && c.task_id) { added.id = c.task_id; DB.save(); }
        }).catch(() => {});
      }
      resetForm(f, 'Add task'); draw();
    });
    $('#sort').onchange = draw;
    $('.kan').onclick = e => {
      const b = e.target.closest('button'); if (!b) return;
      const id = +b.dataset.id, t = DB.mine('tasks').find(x => x.id === id);
      if (!t) return;
      if (b.dataset.act === 'move') {
        const nextStatus = NEXT[t.status][0];
        DB.upd('tasks', id, { status: nextStatus });
        fetch('/api/tasks/' + id, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ status: nextStatus })
        }).catch(() => {});
        draw();
      }
      if (b.dataset.act === 'del' && confirm('Delete this task?')) {
        DB.del('tasks', id);
        fetch('/api/tasks/' + id, { method: 'DELETE', credentials: 'include' }).catch(() => {});
        draw();
      }
      if (b.dataset.act === 'edit') {
        f.elements.id.value = id; f.elements.title.value = t.title; f.elements.due.value = t.due;
        f.elements.priority.value = t.priority; $('button.btn', f).textContent = 'Save changes'; f.elements.title.focus();
      }
    };
    draw();
  },
  wellness() {
    const cf = $('#cycle'), mf = $('#mood'); let cm = new Date(); cm.setDate(1);

    fetch('/api/periods', { credentials: 'include' })
      .then(r => r.ok ? r.json() : [])
      .then(serverPeriods => {
        if (Array.isArray(serverPeriods) && serverPeriods.length > 0) {
          const uid = Auth.id();
          DB.d.periods = DB.d.periods.filter(p => p.userId !== uid);
          serverPeriods.forEach(sp => {
            DB.d.periods.push({
              id: sp.log_id,
              userId: uid,
              start: sp.cycle_start_date,
              length: sp.cycle_length,
              next: sp.predicted_next_date,
              createdAt: Date.now()
            });
          });
          DB.save();
          cal();
        }
      }).catch(() => {});

    fetch('/api/moods', { credentials: 'include' })
      .then(r => r.ok ? r.json() : [])
      .then(serverMoods => {
        if (Array.isArray(serverMoods) && serverMoods.length > 0) {
          const uid = Auth.id();
          DB.d.moods = DB.d.moods.filter(m => m.userId !== uid);
          serverMoods.forEach(sm => {
            DB.d.moods.push({
              id: sm.entry_id,
              userId: uid,
              date: sm.entry_date,
              mood: sm.mood,
              symptoms: sm.symptoms,
              pain: sm.pain_level,
              sleep: sm.sleep_hours,
              water: sm.water_intake,
              createdAt: Date.now()
            });
          });
          DB.save();
          list();
        }
      }).catch(() => {});

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
      if (ex) DB.upd('periods', ex.id, { length: +v.length });
      else DB.add('periods', { userId: Auth.id(), start: v.start, length: +v.length, createdAt: Date.now() });

      fetch('/api/periods', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ cycle_start_date: v.start, cycle_length: +v.length })
      }).catch(() => {});

      say('Cycle saved'); cal();
    });
    onSubmit(mf, v => {
      const row = { date: v.date, mood: v.mood, symptoms: v.symptoms.trim(), pain: +v.pain, sleep: +v.sleep, water: +v.water };
      const ex = DB.mine('moods').find(m => m.date === v.date);
      if (ex) DB.upd('moods', ex.id, row);
      else DB.add('moods', { ...row, userId: Auth.id(), createdAt: Date.now() });

      fetch('/api/moods', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          entry_date: v.date,
          mood: v.mood,
          symptoms: v.symptoms.trim(),
          pain_level: +v.pain,
          sleep_hours: +v.sleep,
          water_intake: +v.water
        })
      }).catch(() => {});

      say('Entry saved'); list();
    });
    $('#prev').onclick = () => { cm.setMonth(cm.getMonth() - 1); cal(); };
    $('#next').onclick = () => { cm.setMonth(cm.getMonth() + 1); cal(); };
    mf.elements.date.value = FSV.today(); sliders(mf); cal(); list();
  },
  analytics() {
    // If analytics.js is loaded, it takes precedence.
  }
};

const page = document.body.dataset.page;
if (!['login', 'register'].includes(page) && !Auth.user()) {
  location.replace('index.html');
} else {
  if ($('#nav')) nav(page);
  if (typeof pages[page] === 'function') pages[page]();
}
