/* FlowSpace Dashboard Controller (Member 6 + Member 3 Integration)
   Consumes GET /api/analytics as the authoritative data source with robust offline fallback. */
/* global window, document, fetch, Promise, localStorage, setTimeout, clearTimeout */

window.pages = window.pages || {};
const pages = window.pages;

const $ = window.$ || ((s, r = document) => r.querySelector(s));
const $$ = window.$$ || ((s, r = document) => [...r.querySelectorAll(s)]);
const esc = window.esc || (s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])));
const fmt = window.fmt || (d => d ? new Date(d.includes('T') ? d : d + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '-');
const ymd = window.ymd || (ms => new Date(ms).toLocaleDateString('en-CA'));

pages.dashboard = function () {
  const loadingEl  = $('#dash-loading');
  const errorEl    = $('#dash-error');
  const errorMsgEl = $('#dash-error-msg');
  const retryBtn   = $('#dash-retry-btn');
  const welcomeEl  = $('#welcome-state');

  // Greet user
  const Auth = (typeof window.Auth !== 'undefined' && window.Auth) ? window.Auth : null;
  let userName = 'there';
  try {
    const user = (Auth && typeof Auth.user === 'function') ? Auth.user() : (Auth ? Auth.me : null);
    if (user && user.name) {
      userName = String(user.name).trim().split(' ')[0];
    } else if (localStorage.getItem('fs_user')) {
      const u = JSON.parse(localStorage.getItem('fs_user'));
      if (u && u.name) userName = u.name.trim().split(' ')[0];
    }
  } catch (_) {}
  const nameEl = $('#name');
  if (nameEl) nameEl.textContent = userName;

  function showLoading(show) {
    if (loadingEl) loadingEl.style.display = show ? 'flex' : 'none';
  }

  function showError(msg) {
    if (errorEl) {
      errorEl.style.display = 'flex';
      if (errorMsgEl) errorMsgEl.textContent = msg || 'Could not connect to the analytics service.';
    }
  }

  function hideError() {
    if (errorEl) errorEl.style.display = 'none';
  }

  function renderData(data) {
    hideError();

    const DB = (typeof window.DB !== 'undefined' && window.DB) ? window.DB : null;
    const localNotes = (DB && typeof DB.mine === 'function') ? DB.mine('notes') : [];
    const localMoods = (DB && typeof DB.mine === 'function') ? DB.mine('moods') : [];

    // Counts
    const counts = data.counts || {};
    const notesCount = Math.max(counts.notes ?? 0, localNotes.length);
    const openTasksCount = counts.open_tasks ?? counts.openTasks ?? 0;
    const moodsCount = Math.max(counts.moods ?? 0, localMoods.length);

    const sNotes = $('#sNotes'); if (sNotes) sNotes.textContent = notesCount;
    const sTasks = $('#sTasks'); if (sTasks) sTasks.textContent = openTasksCount;
    const sMoods = $('#sMoods'); if (sMoods) sMoods.textContent = moodsCount;

    // Cycle / Next period
    const cycle = data.cycle_stats || data.cycleStats || {};
    let nextDate = cycle.predicted_next_date || cycle.predictedNextDate;
    if (!nextDate && typeof getCyclePrediction === 'function') {
      getCyclePrediction().then(p => {
        if (p && p.predicted_next_date && $('#sNext')) {
          $('#sNext').textContent = fmt(p.predicted_next_date);
        }
      }).catch(() => {});
    }
    const sNext = $('#sNext');
    if (sNext) {
      sNext.textContent = nextDate ? fmt(nextDate) : '-';
    }

    // Task completion meter
    const tasks = data.task_stats || data.taskStats || {};
    const total = tasks.total ?? 0;
    const completed = tasks.completed ?? 0;
    const pct = tasks.completion_percentage ?? tasks.completionPercentage ?? 0;

    const pctEl = $('#dash-completion-pct');
    if (pctEl) pctEl.textContent = `${pct}%`;
    const meterBar = $('#dash-meter-bar');
    if (meterBar) meterBar.style.width = `${pct}%`;
    const cardEl = $('#task-completion-card');
    const meterWrapper = cardEl ? $('.meter', cardEl) : null;
    if (meterWrapper) meterWrapper.setAttribute('aria-valuenow', pct);
    const taskSub = $('#dash-task-sub');
    if (taskSub) taskSub.textContent = `${completed} of ${total} tasks completed`;

    // Welcome box for new users
    if (welcomeEl) {
      if (notesCount === 0 && total === 0 && moodsCount === 0) {
        welcomeEl.style.display = 'block';
      } else {
        welcomeEl.style.display = 'none';
      }
    }

    // Recent activity list
    const listEl = $('#recent');
    let activity = data.recent_activity || data.recentActivity || [];
    if (!activity.length && (localNotes.length || localMoods.length)) {
      activity = [
        ...localNotes.map(n => ({ type: 'NOTE', title: 'Note: ' + n.title, formattedDate: ymd(n.updatedAt || n.createdAt) })),
        ...localMoods.map(m => ({ type: 'WELLNESS', title: 'Wellness log for ' + fmt(m.date), formattedDate: m.date }))
      ].slice(0, 5);
    }

    if (listEl) {
      if (activity.length > 0) {
        listEl.innerHTML = activity.map(item => {
          const type = (item.type || 'ACTIVITY').toUpperCase();
          const typeClass = type === 'NOTE' ? 'type-note' : (type === 'TASK' ? 'type-task' : 'type-wellness');
          const typeLabel = type === 'NOTE' ? 'Note' : (type === 'TASK' ? 'Task' : 'Wellness');
          const dateStr = item.formatted_date || item.formattedDate || (item.timestamp ? String(item.timestamp).slice(0, 10) : '');

          return `
            <li class="activity-item">
              <span class="activity-badge ${typeClass}">${esc(typeLabel)}</span>
              <div class="activity-content">
                <strong>${esc(item.title || '')}</strong>
                ${item.description ? `<p>${esc(item.description)}</p>` : ''}
              </div>
              <time class="activity-date" datetime="${dateStr}">${fmt(dateStr)}</time>
            </li>
          `;
        }).join('');
      } else {
        listEl.innerHTML = '<li class="empty">No recent activity yet. Your newest notes, tasks, and wellness entries will appear here.</li>';
      }
    }
  }

  function renderOfflineFallback(tasksList = null) {
    const DB = (typeof window.DB !== 'undefined' && window.DB) ? window.DB : null;
    const notes = (DB && typeof DB.mine === 'function') ? (DB.mine('notes') || []) : [];
    const moods = (DB && typeof DB.mine === 'function') ? (DB.mine('moods') || []) : [];

    let tasks = tasksList;
    if (!tasks) {
      const uid = (Auth && typeof Auth.id === 'function') ? Auth.id() : 1;
      try {
        const raw = localStorage.getItem('flowspace_tasks_' + uid);
        if (raw) tasks = JSON.parse(raw);
        else if (DB && typeof DB.mine === 'function') tasks = DB.mine('tasks') || [];
      } catch (_) {
        tasks = (DB && typeof DB.mine === 'function') ? (DB.mine('tasks') || []) : [];
      }
    }
    tasks = tasks || [];

    const doneTasks = tasks.filter(t => t.status === 'DONE').length;
    const openTasks = tasks.filter(t => t.status !== 'DONE').length;
    const pct = tasks.length ? Math.round((doneTasks / tasks.length) * 100) : 0;
    const cycleFn = (typeof window.cycleInfo === 'function') ? window.cycleInfo : (typeof cycleInfo === 'function' ? cycleInfo : null);
    const c = cycleFn ? cycleFn() : null;

    renderData({
      counts: {
        notes: notes.length,
        tasks: tasks.length,
        open_tasks: openTasks,
        moods: moods.length,
        cycles: (DB && typeof DB.mine === 'function' ? (DB.mine('periods') || []).length : 0)
      },
      task_stats: {
        total: tasks.length,
        completed: doneTasks,
        pending: openTasks,
        completion_percentage: pct
      },
      cycle_stats: {
        has_data: !!c,
        predicted_next_date: c ? c.next : null
      },
      recent_activity: [
        ...notes.map(n => ({ type: 'NOTE', title: 'Note: ' + n.title, formattedDate: ymd(n.updatedAt || n.createdAt) })),
        ...tasks.map(t => ({ type: 'TASK', title: 'Task: ' + t.title, formattedDate: ymd(t.createdAt || Date.now()) })),
        ...moods.map(m => ({ type: 'WELLNESS', title: 'Wellness log for ' + fmt(m.date), formattedDate: m.date }))
      ].slice(0, 5)
    });
  }

  function loadAnalytics() {
    showLoading(true);
    hideError();

    // 1. First render instantly from local cache to avoid visual lag
    renderOfflineFallback();

    // 2. Fetch live analytics from backend
    fetch('/api/analytics', { credentials: 'include' })
      .then(async res => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json();
      })
      .then(data => {
        showLoading(false);
        renderData(data);
      })
      .catch(() => {
        // Fallback: query /api/tasks directly
        fetch('/api/tasks', { credentials: 'include' })
          .then(r => r.ok ? r.json() : [])
          .then(serverTasks => {
            showLoading(false);
            if (Array.isArray(serverTasks) && serverTasks.length > 0) {
              renderOfflineFallback(serverTasks);
            } else {
              renderOfflineFallback();
            }
          })
          .catch(() => {
            showLoading(false);
            renderOfflineFallback();
          });
      });
  }

  if (retryBtn) {
    retryBtn.onclick = loadAnalytics;
  }

  loadAnalytics();
};
