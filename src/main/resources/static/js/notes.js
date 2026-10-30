/* Notes page. Needs core.js loaded first.
   Syncs with /api/notes backend API with responsive local state. */
pages.notes = function () {
  const f = $('#f');

  // Sync notes from server
  fetch('/api/notes', { credentials: 'include' })
    .then(r => r.ok ? r.json() : [])
    .then(serverNotes => {
      if (Array.isArray(serverNotes)) {
        const uid = Auth.id();
        DB.d.notes = DB.d.notes.filter(n => n.userId !== uid);
        serverNotes.forEach(sn => {
          DB.d.notes.push({
            id: sn.note_id,
            userId: uid,
            title: sn.title,
            content: sn.content,
            category: sn.category,
            createdAt: sn.created_at ? new Date(sn.created_at).getTime() : Date.now(),
            updatedAt: sn.updated_at ? new Date(sn.updated_at).getTime() : Date.now()
          });
        });
        DB.save();
        draw();
      }
    })
    .catch(() => {});

  const draw = () => {
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
      const nid = +v.id;
      DB.upd('notes', nid, row);
      say('Note updated');
      fetch('/api/notes/' + nid, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ title: row.title, category: row.category, content: row.content })
      }).catch(() => {});
    } else {
      const added = DB.add('notes', { ...row, userId: Auth.id(), createdAt: Date.now() });
      say('Note added');
      fetch('/api/notes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ title: row.title, category: row.category, content: row.content })
      })
      .then(r => r.ok ? r.json() : null)
      .then(serverNote => {
        if (serverNote && serverNote.note_id) {
          added.id = serverNote.note_id;
          DB.save();
        }
      })
      .catch(() => {});
    }
    resetForm(f, 'Add note');
    draw();
  });

  $('#q').oninput = $('#cat').onchange = draw;
  $('#list').onclick = e => {
    const b = e.target.closest('button');
    if (!b) return;
    const id = +b.dataset.id;
    if (b.dataset.act === 'del' && confirm('Delete this note?')) {
      DB.del('notes', id);
      say('Note deleted');
      draw();
      fetch('/api/notes/' + id, {
        method: 'DELETE',
        credentials: 'include'
      }).catch(() => {});
    }
    if (b.dataset.act === 'edit') {
      const n = DB.mine('notes').find(x => x.id === id);
      if (n) {
        f.elements.id.value = id;
        f.elements.title.value = n.title;
        f.elements.category.value = n.category;
        f.elements.content.value = n.content;
        $('button.btn', f).textContent = 'Save changes';
        f.elements.title.focus();
      }
    }
  };

  draw();
};
