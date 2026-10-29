/* FlowSpace Dashboard Controller (Member 6)
   Consumes GET /api/analytics as the authoritative data source.
   Provides robust loading, error, retry, and new user welcome states. */

pages.dashboard = function () {
  const loadingEl  = $('#dash-loading');
  const errorEl    = $('#dash-error');
  const errorMsgEl = $('#dash-error-msg');
  const retryBtn   = $('#dash-retry-btn');
  const welcomeEl  = $('#welcome-state');

  // Greet user
  if (Auth.user() && Auth.user().name) {
    $('#name').textContent = String(Auth.user().name).trim().split(' ')[0];
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

    $('#sNotes').textContent = notesCount;
    $('#sTasks').textContent = openTasksCount;
    $('#sMoods').textContent = moodsCount;

    // Cycle / Next period
    const cycle = data.cycle_stats || data.cycleStats || {};
    const nextDate = cycle.predicted_next_date || cycle.predictedNextDate;
    if (nextDate) {
      $('#sNext').textContent = fmt(nextDate);
      $('#sNext').style.fontSize = '1.25rem';
    } else if (cycle.has_data || cycle.hasData) {
      $('#sNext').textContent = 'Tracking';
      $('#sNext').style.fontSize = '1.3rem';
    } else {
      $('#sNext').textContent = 'Not logged';
      $('#sNext').style.fontSize = '1.1rem';
    }

    // Welcome State for empty accounts
    const isBrandNew = (notesCount === 0 && (counts.tasks ?? 0) === 0 && moodsCount === 0 && cyclesCount === 0);
    if (welcomeEl) {
      welcomeEl.style.display = isBrandNew ? 'block' : 'none';
    }

    // Task Completion Meter
    const tasks = data.task_stats || data.taskStats || {};
    const totalTasks = tasks.total ?? 0;
    const completedTasks = tasks.completed ?? 0;
    const pct = tasks.completion_percentage ?? tasks.completionPercentage ?? 0;
    const todo = tasks.todo ?? 0;
    const inProgress = tasks.in_progress ?? tasks.inProgress ?? 0;
    const overdue = tasks.overdue ?? 0;

    $('#dash-completion-pct').textContent = pct + '%';
    const meterBar = $('#dash-meter-bar');
    if (meterBar) meterBar.style.width = pct + '%';

    const meterProgress = $('.meter');
    if (meterProgress) meterProgress.setAttribute('aria-valuenow', pct);

    const subEl = $('#dash-task-sub');
    if (subEl) {
      if (totalTasks === 0) {
        subEl.textContent = 'No tasks yet. Create a task to track your completion progress.';
      } else {
        let txt = `${completedTasks} of ${totalTasks} tasks completed · To do: ${todo} · In progress: ${inProgress}`;
        if (overdue > 0) {
          txt += ` · ${overdue} overdue`;
        }
        subEl.textContent = txt;
      }
    }

    // Recent Activity List
    const acts = data.recent_activity || data.recentActivity || [];
    const recentEl = $('#recent');
    if (recentEl) {
      if (acts && acts.length > 0) {
        recentEl.innerHTML = acts.map(a => {
          const type = esc(a.type || 'NOTE');
          const title = esc(a.title || '');
          const dateStr = a.formatted_date || a.formattedDate || (a.timestamp ? a.timestamp.slice(0, 10) : '');
          return `<li class="activity-item">
            <div class="activity-left">
              <span class="activity-badge badge-${type}">${type}</span>
              <span class="activity-title">${title}</span>
            </div>
            <span class="activity-date">${fmt(dateStr)}</span>
          </li>`;
        }).join('');
      } else {
        recentEl.innerHTML = '<li class="empty">Nothing here yet. Add a note, task, or wellness log to get started.</li>';
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
          throw new Error(err.message || err.error || `Server returned ${res.status}`);
        }
        return res.json();
      })
      .then(data => {
        showLoading(false);
        renderData(data);
      })
      .catch(err => {
        showLoading(false);
        // Fallback to local storage if API is not available or offline
        if (typeof DB !== 'undefined' && DB.mine) {
          try {
            renderOfflineFallback();
            return;
          } catch (e) {}
        }
        showError(err.message);
      });
  }

  function renderOfflineFallback() {
    const notes = DB.mine('notes');
    const tasks = DB.mine('tasks');
    const moods = DB.mine('moods');
    const c = (typeof cycleInfo === 'function') ? cycleInfo() : null;
    const doneTasks = tasks.filter(t => t.status === 'DONE').length;
    const pct = tasks.length ? Math.round(doneTasks / tasks.length * 100) : 0;

    renderData({
      counts: {
        notes: notes.length,
        tasks: tasks.length,
        open_tasks: tasks.filter(t => t.status !== 'DONE').length,
        moods: moods.length,
        cycles: DB.mine('periods').length
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

