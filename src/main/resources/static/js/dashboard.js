/* Dashboard page. Needs core.js loaded first. */
pages.dashboard = function () {
  const notes = DB.mine('notes'), tasks = DB.mine('tasks'), moods = DB.mine('moods');
  $('#name').textContent = String(Auth.user().name || '').split(' ')[0];
  $('#sNotes').textContent = notes.length;
  $('#sTasks').textContent = tasks.filter(t => t.status !== 'DONE').length;
  $('#sMoods').textContent = moods.length;
  $('#sNext').textContent = 'Loading...';
  getCyclePrediction()
    .then(prediction => { $('#sNext').textContent = prediction.predicted_next_date ? fmt(prediction.predicted_next_date) : 'Not logged'; })
    .catch(() => { $('#sNext').textContent = 'Unavailable'; });
  const acts = [...notes.map(n => ({ at: n.updatedAt, t: 'Note: ' + n.title })), ...tasks.map(t => ({ at: t.createdAt, t: 'Task: ' + t.title })),
    ...moods.map(m => ({ at: m.createdAt, t: 'Wellness log for ' + fmt(m.date) }))].sort((a, b) => b.at - a.at).slice(0, 5);
  $('#recent').innerHTML = acts.length ? acts.map(a => `<li>${esc(a.t)} <span class="mute">(${fmt(ymd(a.at))})</span></li>`).join('') : '<li class="empty">Nothing here yet. Add a note, task or wellness log to get started.</li>';
};
