/* Wellness page. Needs core.js loaded first. */
pages.wellness = function () {
  const cf = $('#cycle'), mf = $('#mood'); let cm = new Date(); cm.setDate(1);
  const cal = () => {
    const c = cycleInfo(), starts = new Set(DB.mine('periods').map(p => p.start)), y = cm.getFullYear(), m = cm.getMonth(), first = new Date(y, m, 1).getDay();
    let h = ['S', 'M', 'T', 'W', 'T', 'F', 'S'].map(d => `<b>${d}</b>`).join('') + '<span></span>'.repeat(first);
    for (let d = 1, n = new Date(y, m + 1, 0).getDate(); d <= n; d++) {
      const s = `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      h += `<span class="${starts.has(s) ? 'p' : c && s === c.next ? 'n' : ''}">${d}</span>`;
    }
    $('#cal').innerHTML = h; $('#calTitle').textContent = cm.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
    $('#pred').textContent = c ? `Next period expected around ${fmt(c.next)} (average cycle ${c.avg} days, ${c.count} logged).` : 'Log your first cycle to see a prediction.';
  };
  const list = () => {
    const m = DB.mine('moods').sort((a, b) => b.date.localeCompare(a.date)).slice(0, 7);
    $('#moods').innerHTML = m.length ? m.map(x => `<tr><td>${fmt(x.date)}</td><td>${label(x.mood)}</td><td>${x.pain}/10</td><td>${x.sleep} h</td><td>${x.water}</td><td>${esc(x.symptoms) || '-'}</td></tr>`).join('') : '<tr><td colspan="6" class="mute">No entries yet.</td></tr>';
  };
  onSubmit(cf, v => {
    const ex = DB.mine('periods').find(p => p.start === v.start);
    if (ex) DB.upd('periods', ex.id, { length: +v.length }); else DB.add('periods', { userId: Auth.id(), start: v.start, length: +v.length, createdAt: Date.now() });
    say('Cycle saved'); cal();
  });
  onSubmit(mf, v => {
    const row = { date: v.date, mood: v.mood, symptoms: v.symptoms.trim(), pain: +v.pain, sleep: +v.sleep, water: +v.water }, ex = DB.mine('moods').find(m => m.date === v.date);
    if (ex) DB.upd('moods', ex.id, row); else DB.add('moods', { ...row, userId: Auth.id(), createdAt: Date.now() });
    say('Entry saved'); list();
  });
  $('#prev').onclick = () => { cm.setMonth(cm.getMonth() - 1); cal(); };
  $('#next').onclick = () => { cm.setMonth(cm.getMonth() + 1); cal(); };
  mf.elements.date.value = FSV.today(); sliders(mf); cal(); list();
};
