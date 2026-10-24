/* Tasks page. Needs core.js loaded first. */
pages.tasks = function () {
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
    if (v.id) { DB.upd('tasks', +v.id, row); say('Task updated'); }
    else { DB.add('tasks', { ...row, userId: Auth.id(), status: 'TODO', createdAt: Date.now() }); say('Task added'); }
    resetForm(f, 'Add task'); draw();
  });
  $('#sort').onchange = draw;
  $('.kan').onclick = e => {
    const b = e.target.closest('button'); if (!b) return; const id = +b.dataset.id, t = DB.mine('tasks').find(x => x.id === id);
    if (b.dataset.act === 'move') { DB.upd('tasks', id, { status: NEXT[t.status][0] }); draw(); }
    if (b.dataset.act === 'del' && confirm('Delete this task?')) { DB.del('tasks', id); draw(); }
    if (b.dataset.act === 'edit') { f.elements.id.value = id; f.elements.title.value = t.title; f.elements.due.value = t.due; f.elements.priority.value = t.priority; $('button.btn', f).textContent = 'Save changes'; f.elements.title.focus(); }
  };
  draw();
};
