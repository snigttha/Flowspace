/* Tasks page. Needs core.js loaded first.
   Syncs with /api/tasks backend API while maintaining localStorage caching. */
pages.tasks = function () {
  const f = $('#f'), NEXT = { TODO: ['IN_PROGRESS', 'Start'], IN_PROGRESS: ['DONE', 'Complete'], DONE: ['TODO', 'Reopen'] }, PR = { HIGH: 0, MEDIUM: 1, LOW: 2 };

  // Fetch tasks from backend to sync local store
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
    })
    .catch(() => { });

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
      const tid = +v.id;
      DB.upd('tasks', tid, row);
      say('Task updated');
      fetch('/api/tasks/' + tid, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ title: row.title, due_date: row.due, priority: row.priority })
      }).catch(() => { });
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
        .then(created => {
          if (created && created.task_id) {
            added.id = created.task_id;
            DB.save();
          }
        })
        .catch(() => { });
    }
    resetForm(f, 'Add task');
    draw();
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
      }).catch(() => { });
      draw();
    }
    if (b.dataset.act === 'del' && confirm('Delete this task?')) {
      DB.del('tasks', id);
      fetch('/api/tasks/' + id, {
        method: 'DELETE',
        credentials: 'include'
      }).catch(() => { });
      draw();
    }
    if (b.dataset.act === 'edit') {
      f.elements.id.value = id;
      f.elements.title.value = t.title;
      f.elements.due.value = t.due;
      f.elements.priority.value = t.priority;
      $('button.btn', f).textContent = 'Save changes';
      f.elements.title.focus();
    }
  };

  draw();
};
