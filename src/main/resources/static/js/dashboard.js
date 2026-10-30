/* FlowSpace Dashboard Controller (Member 6)
   Consumes GET /api/analytics as the authoritative data source.
   Provides robust loading, error, retry, and new user welcome states. */
/* global window, document, fetch, Promise, localStorage, setTimeout, clearTimeout */

// ── Global Helper Resolution ───────────────────────────────────────────
window.pages = window.pages || {};
const pages = window.pages;

const $ = window.$ || ((s, r = document) => r.querySelector(s));
const $$ = window.$$ || ((s, r = document) => [...r.querySelectorAll(s)]);
const esc = window.esc || (s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])));
const fmt = window.fmt || (d => d ? new Date(d + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '-');
const ymd = window.ymd || (ms => new Date(ms).toLocaleDateString('en-CA'));

pages.dashboard = function () {
  const loadingEl  = $('#dash-loading');
  const errorEl    = $('#dash-error');
  const errorMsgEl = $('#dash-error-msg');
  const retryBtn   = $('#dash-retry-btn');
  const welcomeEl  = $('#welcome-state');

  // Greet user
  const Auth = (typeof window.Auth !== 'undefined' && window.Auth) ? window.Auth : null;
  const user = (Auth && typeof Auth.user === 'function') ? Auth.user() : null;
  if (user && user.name) {
    const nameEl = $('#name');
    if (nameEl) nameEl.textContent = String(user.name).trim().split(' ')[0];
  }

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

    // Counts
    const counts = data.counts || {};
    const notesCount = counts.notes ?? 0;
    const openTasksCount = counts.open_tasks ?? counts.openTasks ?? 0;
    const moodsCount = counts.moods ?? 0;
    const cyclesCount = counts.cycles ?? 0;

    const sNotes = $('#sNotes'); if (sNotes) sNotes.textContent = notesCount;
    const sTasks = $('#sTasks'); if (sTasks) sTasks.textContent = openTasksCount;
    const sMoods = $('#sMoods'); if (sMoods) sMoods.textContent = moodsCount;

    // Cycle / Next period
    const cycle = data.cycle_stats || data.cycleStats || {};
    const nextDate = cycle.predicted_next_date || cycle.predictedNextDate;
    const sNext = $('#sNext');
    if (sNext) {
      if (nextDate) {
        sNext.textContent = fmt(nextDate);
      } else {
        sNext.textContent = '-';
      }
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
    const activity = data.recent_activity || data.recentActivity || [];
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

  function loadAnalytics() {
    showLoading(true);
    hideError();

    fetch('/api/analytics', { credentials: 'include' })
      .then(async res => {
        if (!res.ok) {
          const err = await res.json().catch(() => ({}));
          throw new Error(err.message || err.error || `Server error (${res.status})`);
        }
        return res.json();
      })
      .then(data => {
        showLoading(false);
        renderData(data);
      })
      .catch(err => {
        showLoading(false);
        // Fallback to offline local storage if API is not accessible
        const DB = (typeof window.DB !== 'undefined' && window.DB) ? window.DB : null;
        if (DB && typeof DB.mine === 'function') {
          try {
            renderOfflineFallback();
            return;
          } catch (e) {}
        }
        showError(err.message);
      });
  }

  function renderOfflineFallback() {
    const DB = (typeof window.DB !== 'undefined' && window.DB) ? window.DB : null;
    if (!DB || typeof DB.mine !== 'function') return;
    const notes = DB.mine('notes') || [];
    const tasks = DB.mine('tasks') || [];
    const doneTasks = tasks.filter(t => t.status === 'DONE').length;
    const moods = DB.mine('moods') || [];
    const pct = tasks.length ? Math.round((doneTasks / tasks.length) * 100) : 0;
    const cycleFn = (typeof window.cycleInfo === 'function') ? window.cycleInfo : (typeof cycleInfo === 'function' ? cycleInfo : null);
    const c = cycleFn ? cycleFn() : null;

    renderData({
      counts: {
        notes: notes.length,
        tasks: tasks.length,
        open_tasks: tasks.filter(t => t.status !== 'DONE').length,
        moods: moods.length,
        cycles: (DB.mine('periods') || []).length
      },
      task_stats: {
        total: tasks.length,
        completed: doneTasks,
        pending: tasks.length - doneTasks,
        completion_percentage: pct
      },
      cycle_stats: {
        has_data: !!c,
        predicted_next_date: c ? c.next : null
      },
      recent_activity: [...notes.map(n => ({ type: 'NOTE', title: 'Note: ' + n.title, formattedDate: ymd(n.updatedAt || n.createdAt) })),
                        ...tasks.map(t => ({ type: 'TASK', title: 'Task: ' + t.title, formattedDate: ymd(t.createdAt) })),
                        ...moods.map(m => ({ type: 'WELLNESS', title: 'Wellness log for ' + fmt(m.date), formattedDate: m.date }))].slice(0, 5)
    });
  }

  if (retryBtn) {
    retryBtn.onclick = loadAnalytics;
  }

  loadAnalytics();
};
