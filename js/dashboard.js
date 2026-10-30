/* Dashboard page. Needs core.js loaded first. */
pages.dashboard = function () {
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

  const renderTasksAndRecent = (tasksList) => {
    const sTasks = $('#sTasks');
    if (sTasks) {
      sTasks.textContent = tasksList.filter(t => t.status !== 'DONE').length;
    }

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

  // Render immediately with local/cached tasks
  renderTasksAndRecent(localTasks);

  // Asynchronously fetch live tasks from the Spring Boot API
  fetch('/api/tasks', { credentials: 'include' })
    .then(r => r.ok ? r.json() : Promise.reject())
    .then(apiTasks => {
      if (Array.isArray(apiTasks)) {
        renderTasksAndRecent(apiTasks);
        try {
          localStorage.setItem('flowspace_tasks_' + uid, JSON.stringify(apiTasks));
          if (typeof DB !== 'undefined' && DB.d && Array.isArray(DB.d.tasks)) {
            DB.d.tasks = DB.d.tasks.filter(t => t.userId !== uid).concat(apiTasks.map(t => ({
              ...t,
              id: t.task_id || t.id,
              userId: uid,
              createdAt: t.created_at ? Date.parse(t.created_at) : (t.createdAt || Date.now())
            })));
            if (typeof DB.save === 'function') DB.save();
          }
        } catch (_) {}
      }
    })
    .catch(() => {});

  const sNext = $('#sNext');
  if (sNext) {
    sNext.textContent = 'Loading...';
    if (typeof getCyclePrediction === 'function') {
      getCyclePrediction()
        .then(prediction => {
          sNext.textContent = prediction && prediction.predicted_next_date ? fmt(prediction.predicted_next_date) : 'Not logged';
        })
        .catch(() => {
          sNext.textContent = 'Unavailable';
        });
    } else {
      sNext.textContent = 'Not logged';
    }
  }
};
