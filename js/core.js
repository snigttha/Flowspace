/* FlowSpace core: shared helpers, session handling and page bootstrap.
   Load order on app pages: validate.js, core.js, then ONE page script (dashboard.js, notes.js, ...).
   Each page script registers itself with:  pages.<name> = function () { ... };
   Notes, tasks and wellness data sync with the backend API with localStorage fallback. */
   Notes, tasks and wellness data still use browser localStorage until their API owners replace them. */
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

const API = '/api';
const Auth = {
  me: null,
  id: () => (Auth.me ? (Auth.me.userId ?? Auth.me.id ?? Auth.me.email) : (+localStorage.getItem('fs_session') || 0)),
  user: () => Auth.me || (typeof DB !== 'undefined' && DB.d && DB.d.users ? DB.d.users.find(u => u.id === Auth.id()) : null),
  logout() {
    fetch(`${API}/logout`, { method: 'POST', credentials: 'include' })
      .catch(() => {})
      .finally(() => {
        try {
          localStorage.removeItem('fs_session');
          localStorage.removeItem('flowspace');
          if (typeof DB !== 'undefined') {
            DB.d = empty();
          }
        } catch (e) {}
        location.href = 'index.html';
      });
      .finally(() => { localStorage.removeItem('fs_session'); location.href = 'index.html'; });
  id: () => (Auth.me ? (Auth.me.id ?? Auth.me.userId ?? Auth.me.email) : 0),
  user: () => Auth.me,
  logout() {
    fetch(`${API}/logout`, { method: 'POST', credentials: 'include' })
      .catch(() => {})
      .finally(() => { location.href = 'index.html'; });
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
  const userObj = Auth.user();
  const userName = userObj ? (userObj.name || userObj.email || 'User') : 'User';
  $('#nav').innerHTML = '<div class="brand">FlowSpace</div>' + P.map(([p, l]) => `<a href="${p}.html" class="${p === page ? 'on' : ''}">${l}</a>`).join('') +
    `<p class="who">${esc(userName)}</p><button class="btn ghost" id="out">Log out</button>`;
  const uName = (Auth.user() && Auth.user().name) ? Auth.user().name : (Auth.me && Auth.me.name) ? Auth.me.name : 'User';
  $('#nav').innerHTML = '<div class="brand">FlowSpace</div>' + P.map(([p, l]) => `<a href="${p}.html" class="${p === page ? 'on' : ''}">${l}</a>`).join('') +
    `<p class="who">${esc(uName)}</p><button class="btn ghost" id="out">Log out</button>`;
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
  const response = await fetch(`${API}/periods/prediction`, { credentials: 'include' });
  const prediction = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(prediction.error || 'Could not load cycle prediction.');
  return prediction;
}

/* Page scripts fill this in. */
const pages = {};

/* Session check: ask the backend who is logged in, then start the page.
   Any failure (expired session, backend down) sends the user to login or fallback. */
   Any failure (expired session, backend down) sends the user to the login page. */
const page = document.body.dataset.page;
if (page !== 'login' && page !== 'register') {
  fetch(`${API}/me`, { credentials: 'include' })
    .then(r => (r.ok ? r.json() : Promise.reject()))
    .then(u => {
      Auth.me = u;
      if ($('#nav')) nav(page);
      if (typeof pages[page] === 'function') pages[page]();
    })
    .catch(() => {
      // If offline demo session exists, allow it; otherwise redirect
      if (localStorage.getItem('fs_session')) {
        if ($('#nav')) nav(page);
        if (typeof pages[page] === 'function') pages[page]();
      } else {
        location.replace('index.html');
      }
    });
    .catch(() => location.replace('index.html'));
}
