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

async function getCyclePrediction() {
  try {
    const response = await fetch('/api/periods/prediction', { credentials: 'include' });
    const prediction = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(prediction.error || 'Could not load cycle prediction.');
    return prediction;
  } catch (error) {
    const local = cycleInfo();
    return local ? {
      logged_cycles: local.count,
      average_cycle_days: local.avg,
      predicted_next_date: local.next
    } : { logged_cycles: 0 };
  }
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
    const notes = (typeof DB !== 'undefined' && typeof DB.mine === 'function') ? DB.mine('notes') : [];
    const moods = (typeof DB !== 'undefined' && typeof DB.mine === 'function') ? DB.mine('moods') : [];
    const uid = (typeof Auth !== 'undefined' && typeof Auth.id === 'function') ? Auth.id() : 0;
    let localTasks = [];
    try {
      const raw = localStorage.getItem('flowspace_tasks_' + uid);
      if (raw) localTasks = JSON.parse(raw);
      else if (typeof DB !== 'undefined' && typeof DB.mine === 'function') localTasks = DB.mine('tasks');
    } catch (_) {
      localTasks = (typeof DB !== 'undefined' && typeof DB.mine === 'function') ? DB.mine('tasks') : [];
    }

    const uName = (typeof Auth !== 'undefined' && Auth.user() && Auth.user().name)
      ? Auth.user().name
      : (typeof Auth !== 'undefined' && Auth.me && Auth.me.name) ? Auth.me.name : '';
    const nameEl = $('#name');
    if (nameEl) nameEl.textContent = uName.split(' ')[0] || 'there';

    const sNotes = $('#sNotes');
    if (sNotes) sNotes.textContent = notes.length;

    const sMoods = $('#sMoods');
    if (sMoods) sMoods.textContent = moods.length;

    const renderTasks = (tasksList) => {
      const sTasks = $('#sTasks');
      if (sTasks) sTasks.textContent = tasksList.filter(t => t.status !== 'DONE').length;
      const recentEl = $('#recent');
      if (recentEl) {
        const acts = [
          ...notes.map(n => ({ at: n.updatedAt || Date.now(), t: 'Note: ' + n.title })),
          ...tasksList.map(t => ({ at: Date.parse(t.created_at || t.createdAt || 0) || Date.now(), t: 'Task: ' + t.title })),
          ...moods.map(m => ({ at: m.createdAt || Date.now(), t: 'Wellness log for ' + fmt(m.date) }))
        ].sort((a, b) => b.at - a.at).slice(0, 5);
        recentEl.innerHTML = acts.length
          ? acts.map(a => `<li>${esc(a.t)} <span class="mute">(${fmt(ymd(a.at))})</span></li>`).join('')
          : '<li class="empty">Nothing here yet. Add a note, task or wellness log to get started.</li>';
      }
    };

    renderTasks(localTasks);

    fetch('/api/tasks', { credentials: 'include' })
      .then(r => r.ok ? r.json() : Promise.reject())
      .then(apiTasks => {
        if (Array.isArray(apiTasks)) {
          renderTasks(apiTasks);
          try {
            localStorage.setItem('flowspace_tasks_' + uid, JSON.stringify(apiTasks));
          } catch (_) {}
        }
      })
      .catch(() => {});

    const sNext = $('#sNext');
    if (sNext) {
      sNext.textContent = 'Loading...';
      if (typeof getCyclePrediction === 'function') {
        getCyclePrediction().then(prediction => {
          sNext.textContent = prediction && prediction.predicted_next_date ? fmt(prediction.predicted_next_date) : 'Not logged';
        }).catch(() => { sNext.textContent = 'Unavailable'; });
      } else {
        sNext.textContent = 'Not logged';
      }
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

    let periods = [], prediction = null, cycleApi = false;
    const cycleError = $('#cycleError');
    const getLocalPrediction = () => {
      const sorted = [...periods].sort((a, b) => a.cycle_start_date.localeCompare(b.cycle_start_date));
      if (!sorted.length) return null;
      const latest = sorted[sorted.length - 1];
      let days = latest.cycle_length;
      if (sorted.length > 1) {
        days = Math.round(avg(sorted.slice(1).map((cycle, i) => daysBetween(sorted[i].cycle_start_date, cycle.cycle_start_date))));
      }
      days = Math.min(60, Math.max(15, days));
      return { logged_cycles: sorted.length, average_cycle_days: days, predicted_next_date: addDays(latest.cycle_start_date, days),
        message: `Next period expected around ${fmt(addDays(latest.cycle_start_date, days))} (average cycle ${days} days, ${sorted.length} logged).` };
    };
    const refreshCycles = async (allowDemo = true) => {
      try {
        $('#cycleLoading').textContent = 'Loading cycle data...';
        const responses = await Promise.all([
          fetch('/api/periods', { credentials: 'include' }),
          fetch('/api/periods/prediction', { credentials: 'include' })
        ]);
        const bodies = await Promise.all(responses.map(async response => {
          const body = await response.json().catch(() => ({}));
          if (!response.ok) throw new Error(body.error || 'Could not load cycle data.');
          return body;
        }));
        periods = bodies[0]; prediction = bodies[1]; cycleApi = true;
        $('#cycleLoading').textContent = periods.length ? '' : 'No cycles logged yet.';
        cycleError.textContent = '';
        cal();
        return true;
      } catch (error) {
        cycleApi = false;
        if (!allowDemo) {
          cycleError.textContent = error.message || 'Could not refresh cycle data.';
          $('#cycleLoading').textContent = '';
          return false;
        }
        periods = DB.mine('periods').map(cycle => ({
          cycle_start_date: cycle.start,
          cycle_length: cycle.length
        }));
        prediction = getLocalPrediction();
        $('#cycleLoading').textContent = 'Demo mode: cycle data is saved in this browser.';
        cal();
        return false;
      }
    };
    const cal = () => {
      const starts = new Set(periods.map(period => period.cycle_start_date));
      const y = cm.getFullYear(), m = cm.getMonth(), first = new Date(y, m, 1).getDay();
      let h = ['S', 'M', 'T', 'W', 'T', 'F', 'S'].map(d => `<b role="columnheader">${d}</b>`).join('') + '<span aria-hidden="true"></span>'.repeat(first);
      for (let d = 1, n = new Date(y, m + 1, 0).getDate(); d <= n; d++) {
        const s = `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
        const marker = starts.has(s) ? 'cycle-start' : prediction && s === prediction.predicted_next_date ? 'cycle-predicted' : '';
        h += `<span class="cycle-date ${marker}" role="gridcell" aria-label="${s}${marker ? `, ${marker === 'cycle-start' ? 'period start' : 'predicted next period'}` : ''}">${d}</span>`;
      }
      $('#cal').innerHTML = h; $('#calTitle').textContent = cm.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
      $('#pred').textContent = prediction ? prediction.message : 'Log your first cycle to see a prediction.';
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
    onSubmit(cf, async v => {
      const button = $('button.btn', cf);
      button.disabled = true;
      cycleError.textContent = '';
      try {
        if (cycleApi) {
          const response = await fetch('/api/periods', {
            method: 'POST', credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ cycle_start_date: v.start, cycle_length: Number(v.length) })
          });
          const result = await response.json().catch(() => ({}));
          if (!response.ok) {
            const field = /length/i.test(result.error || '') ? 'length' : /date/i.test(result.error || '') ? 'start' : null;
            if (field) FSV.fail(cf, field, result.error);
            else cycleError.textContent = result.error || 'Could not save this cycle.';
            return;
          }
          await refreshCycles(false);
        } else {
          const existing = DB.mine('periods').find(cycle => cycle.start === v.start);
          if (existing) DB.upd('periods', existing.id, { length: Number(v.length) });
          else DB.add('periods', { userId: Auth.id(), start: v.start, length: Number(v.length), createdAt: Date.now() });
          periods = DB.mine('periods').map(cycle => ({ cycle_start_date: cycle.start, cycle_length: cycle.length }));
          prediction = getLocalPrediction();
          cal();
        }
        say('Cycle saved');
      } catch (error) {
        cycleError.textContent = error.message || 'Could not save this cycle.';
      } finally {
        button.disabled = false;
      }
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
    mf.elements.date.value = FSV.today(); sliders(mf); cal(); list(); refreshCycles();
  },
  analytics() {
    // If analytics.js is loaded, it takes precedence.
    const t = DB.mine('tasks'), done = t.filter(x => x.status === 'DONE').length, pct = t.length ? Math.round(done / t.length * 100) : 0, today = FSV.today();
    $('#done').innerHTML = t.length ? `<p><b>${pct}%</b> complete (${done} of ${t.length})</p><div class="meter"><i style="width:${pct}%"></i></div>
      <p class="mute">To do ${t.filter(x => x.status === 'TODO').length} · In progress ${t.filter(x => x.status === 'IN_PROGRESS').length} · Overdue ${t.filter(x => x.status !== 'DONE' && x.due < today).length}</p>` : '<p class="empty">Add tasks to see completion stats.</p>';
    const m = DB.mine('moods').sort((a, b) => a.date.localeCompare(b.date)), last = m.slice(-7);
    $('#trend').innerHTML = last.length ? last.map(x => `<div class="col" title="${label(x.mood)}"><i style="height:${MOOD[x.mood] * 20}%"></i><small>${x.date.slice(5)}</small></div>`).join('') : '<p class="empty">Log your mood to see a trend.</p>';
    $('#cyc').innerHTML = '<p class="mute">Loading cycle statistics...</p>';
    getCyclePrediction().then(c => {
      $('#cyc').innerHTML = c.logged_cycles
        ? `<p><b>${c.average_cycle_days} days</b> average cycle</p><p class="mute">${c.logged_cycles} cycle${c.logged_cycles > 1 ? 's' : ''} logged. Next expected around ${fmt(c.predicted_next_date)}.</p>`
        : '<p class="empty">Log a cycle to see statistics.</p>';
    });
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
if (!['login', 'register'].includes(page) && !Auth.user()) {
  location.replace('index.html');
} else {
  if ($('#nav')) nav(page);
  if (typeof pages[page] === 'function') pages[page]();
}
