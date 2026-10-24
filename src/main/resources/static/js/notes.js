/* Notes page. Needs core.js loaded first. */
pages.notes = function () {
  const f = $('#f'), draw = () => {
    const q = $('#q').value.trim().toLowerCase(), c = $('#cat').value;
    const rows = DB.mine('notes').filter(n => (!c || n.category === c) && (n.title + ' ' + n.content).toLowerCase().includes(q)).sort((a, b) => b.updatedAt - a.updatedAt);
    $('#list').innerHTML = rows.length ? rows.map(n => `<article class="card"><div class="row"><h3>${esc(n.title)}</h3><span class="tag">${esc(n.category)}</span></div>
      <p>${esc(n.content.slice(0, 160))}${n.content.length > 160 ? '...' : ''}</p><small class="mute">Updated ${fmt(ymd(n.updatedAt))}</small>
      <div class="actions"><button class="btn ghost" data-act="edit" data-id="${n.id}">Edit</button><button class="btn ghost" data-act="del" data-id="${n.id}">Delete</button></div></article>`).join('')
      : '<p class="empty">No notes found. Write one with the form.</p>';
  };
  onSubmit(f, v => {
    const row = { title: v.title.trim(), category: v.category, content: v.content.trim(), updatedAt: Date.now() };
    if (v.id) { DB.upd('notes', +v.id, row); say('Note updated'); }
    else { DB.add('notes', { ...row, userId: Auth.id(), createdAt: Date.now() }); say('Note added'); }
    resetForm(f, 'Add note'); draw();
  });
  $('#q').oninput = $('#cat').onchange = draw;
  $('#list').onclick = e => {
    const b = e.target.closest('button'); if (!b) return; const id = +b.dataset.id;
    if (b.dataset.act === 'del' && confirm('Delete this note?')) { DB.del('notes', id); say('Note deleted'); draw(); }
    if (b.dataset.act === 'edit') { const n = DB.mine('notes').find(x => x.id === id); f.elements.id.value = id; f.elements.title.value = n.title; f.elements.category.value = n.category; f.elements.content.value = n.content; $('button.btn', f).textContent = 'Save changes'; f.elements.title.focus(); }
  };
  draw();
};
