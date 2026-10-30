/* Wellness page. Needs core.js loaded first. */
pages.wellness = function () {
  const cf = $('#cycle'), mf = $('#mood'); let cm = new Date(); cm.setDate(1);
  let periods = [], prediction = null;
  const cycleError = $('#cycleError');
  const loadCycles = async () => {
    $('#cycleLoading').textContent = 'Loading cycle data...';
    try {
      const responses = await Promise.all([
        fetch(`${API}/periods`, { credentials: 'include' }),
        fetch(`${API}/periods/prediction`, { credentials: 'include' })
      ]);
      const data = await Promise.all(responses.map(async response => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body.error || 'Could not load cycle data.');
        return body;
      }));
      periods = data[0]; prediction = data[1];
      $('#cycleLoading').textContent = periods.length ? '' : 'No cycles logged yet.';
      cycleError.textContent = '';
      cal();
    } catch (error) {
      $('#cycleLoading').textContent = 'Cycle data could not be loaded.';
      cycleError.textContent = error.message || 'Could not load cycle data.';
    }
  };
  const cal = () => {
    const starts = new Set(periods.map(period => period.cycle_start_date));
    const y = cm.getFullYear(), m = cm.getMonth(), first = new Date(y, m, 1).getDay();
    let h = ['S', 'M', 'T', 'W', 'T', 'F', 'S'].map(d => `<b role="columnheader">${d}</b>`).join('') + '<span aria-hidden="true"></span>'.repeat(first);
    for (let d = 1, n = new Date(y, m + 1, 0).getDate(); d <= n; d++) {
      const s = `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const marker = starts.has(s) ? 'cycle-start' : prediction && s === prediction.predicted_next_date ? 'cycle-predicted' : '';
      h += `<span class="cycle-date ${marker}" role="gridcell" aria-label="${s}${marker ? `, ${marker === 'cycle-start' ? 'period start' : 'predicted next period'}` : ''}">${d}</span>`;
    }
    $('#cal').innerHTML = h; $('#calTitle').textContent = cm.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
    $('#pred').textContent = prediction ? prediction.message : 'Log your first cycle to see a prediction.';
  };
  const list = () => {
    const m = DB.mine('moods').sort((a, b) => b.date.localeCompare(a.date)).slice(0, 7);
    $('#moods').innerHTML = m.length ? m.map(x => `<tr><td>${fmt(x.date)}</td><td>${label(x.mood)}</td><td>${x.pain}/10</td><td>${x.sleep} h</td><td>${x.water}</td><td>${esc(x.symptoms) || '-'}</td></tr>`).join('') : '<tr><td colspan="6" class="mute">No entries yet.</td></tr>';
  };
  onSubmit(cf, async v => {
    const button = $('button.btn', cf);
    button.disabled = true;
    cycleError.textContent = '';
    try {
      const response = await fetch(`${API}/periods`, {
        method: 'POST', credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ cycle_start_date: v.start, cycle_length: Number(v.length) })
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) {
        const field = /length/i.test(result.error || '') ? 'length' : /date/i.test(result.error || '') ? 'start' : null;
        if (field) FSV.fail(cf, field, result.error);
        else cycleError.textContent = result.error || 'Could not save this cycle.';
        return;
      }
      await loadCycles();
      say('Cycle saved');
    } catch (error) {
      cycleError.textContent = error.message || 'Could not save this cycle.';
    } finally {
      button.disabled = false;
    }
  });
  onSubmit(mf, v => {
    const row = { date: v.date, mood: v.mood, symptoms: v.symptoms.trim(), pain: +v.pain, sleep: +v.sleep, water: +v.water }, ex = DB.mine('moods').find(m => m.date === v.date);
    if (ex) DB.upd('moods', ex.id, row); else DB.add('moods', { ...row, userId: Auth.id(), createdAt: Date.now() });
    say('Entry saved'); list();
  });
  $('#prev').onclick = () => { cm.setMonth(cm.getMonth() - 1); cal(); };
  $('#next').onclick = () => { cm.setMonth(cm.getMonth() + 1); cal(); };
  mf.elements.date.value = FSV.today(); sliders(mf); cal(); list(); loadCycles();
};
