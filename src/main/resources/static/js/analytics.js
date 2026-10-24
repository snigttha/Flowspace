/* Insights (analytics) page. Needs core.js loaded first. */
pages.analytics = function () {
  const t = DB.mine('tasks'), done = t.filter(x => x.status === 'DONE').length, pct = t.length ? Math.round(done / t.length * 100) : 0, today = FSV.today();
  $('#done').innerHTML = t.length ? `<p><b>${pct}%</b> complete (${done} of ${t.length})</p><div class="meter"><i style="width:${pct}%"></i></div>
    <p class="mute">To do ${t.filter(x => x.status === 'TODO').length} · In progress ${t.filter(x => x.status === 'IN_PROGRESS').length} · Overdue ${t.filter(x => x.status !== 'DONE' && x.due < today).length}</p>` : '<p class="empty">Add tasks to see completion stats.</p>';
  const m = DB.mine('moods').sort((a, b) => a.date.localeCompare(b.date)), last = m.slice(-7);
  $('#trend').innerHTML = last.length ? last.map(x => `<div class="col" title="${label(x.mood)}"><i style="height:${MOOD[x.mood] * 20}%"></i><small>${x.date.slice(5)}</small></div>`).join('') : '<p class="empty">Log your mood to see a trend.</p>';
  const c = cycleInfo();
  $('#cyc').innerHTML = c ? `<p><b>${c.avg} days</b> average cycle</p><p class="mute">${c.count} cycle${c.count > 1 ? 's' : ''} logged. Next expected around ${fmt(c.next)}.</p>` : '<p class="empty">Log a cycle to see statistics.</p>';
  const ins = [];
  if (m.length >= 3) {
    ins.push(`Across ${m.length} entries you average ${avg(m.map(x => x.sleep)).toFixed(1)} hours of sleep, ${avg(m.map(x => x.water)).toFixed(1)} glasses of water and pain level ${avg(m.map(x => x.pain)).toFixed(1)}/10.`);
    const a = m.filter(x => x.sleep >= 7), b = m.filter(x => x.sleep < 7);
    if (a.length && b.length) ins.push(`Your mood score averages ${avg(a.map(x => MOOD[x.mood])).toFixed(1)}/5 on days with 7+ hours of sleep versus ${avg(b.map(x => MOOD[x.mood])).toFixed(1)}/5 on shorter nights.`);
  }
  if (t.length) ins.push(`${done} of ${t.length} tasks are done.`);
  $('#ins').innerHTML = ins.length ? ins.map(i => `<li>${esc(i)}</li>`).join('') : '<li class="empty">Insights appear once you have a few wellness entries and tasks.</li>';
};
