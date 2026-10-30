/* FlowSpace Insights / Analytics Controller (Member 6)
   Consumes GET /api/analytics as the authoritative data source.
   Renders completion metrics, cycle stats, accessible mood trend chart,
   and personalized data-driven insights with empty and error states. */
/* global window, document, fetch, Promise, localStorage, setTimeout, clearTimeout */

// ── Global Helper Resolution ───────────────────────────────────────────
window.pages = window.pages || {};
const pages = window.pages;

const $ = window.$ || ((s, r = document) => r.querySelector(s));
const $$ = window.$$ || ((s, r = document) => [...r.querySelectorAll(s)]);
const esc = window.esc || (s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])));
const fmt = window.fmt || (d => d ? new Date(d + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '-');
const ymd = window.ymd || (ms => new Date(ms).toLocaleDateString('en-CA'));
const label = window.label || (s => s ? s.charAt(0) + s.slice(1).toLowerCase().replace('_', ' ') : '');
const avg = window.avg || (a => a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0);
const MOOD = window.MOOD || { HAPPY: 5, CALM: 4, NEUTRAL: 3, TIRED: 2, ANXIOUS: 2, IRRITABLE: 2, SAD: 1 };

pages.analytics = function () {
  const loadingEl  = $('#analytics-loading');
  const errorEl    = $('#analytics-error');
  const errorMsgEl = $('#analytics-error-msg');
  const retryBtn   = $('#analytics-retry-btn');
  const legendEl   = $('#trend-legend');

  function showLoading(show) {
    if (loadingEl) loadingEl.style.display = show ? 'flex' : 'none';
  const t = DB.mine('tasks'), done = t.filter(x => x.status === 'DONE').length, pct = t.length ? Math.round(done / t.length * 100) : 0, today = FSV.today();
  $('#done').innerHTML = t.length ? `<p><b>${pct}%</b> complete (${done} of ${t.length})</p><div class="meter"><i style="width:${pct}%"></i></div>
    <p class="mute">To do ${t.filter(x => x.status === 'TODO').length} · In progress ${t.filter(x => x.status === 'IN_PROGRESS').length} · Overdue ${t.filter(x => x.status !== 'DONE' && x.due < today).length}</p>` : '<p class="empty">Add tasks to see completion stats.</p>';
  const m = DB.mine('moods').sort((a, b) => a.date.localeCompare(b.date)), last = m.slice(-7);
  $('#trend').innerHTML = last.length ? last.map(x => `<div class="col" title="${label(x.mood)}"><i style="height:${MOOD[x.mood] * 20}%"></i><small>${x.date.slice(5)}</small></div>`).join('') : '<p class="empty">Log your mood to see a trend.</p>';
  $('#cyc').innerHTML = '<p class="mute">Loading cycle statistics...</p>';
  getCyclePrediction()
    .then(c => {
      $('#cyc').innerHTML = c.logged_cycles
        ? `<p><b>${c.average_cycle_days} days</b> average cycle</p><p class="mute">${c.logged_cycles} cycle${c.logged_cycles > 1 ? 's' : ''} logged. Next expected around ${fmt(c.predicted_next_date)}.</p>`
        : '<p class="empty">Log a cycle to see statistics.</p>';
    })
    .catch(() => { $('#cyc').innerHTML = '<p class="empty">Cycle statistics are unavailable.</p>'; });
  const ins = [];
  if (m.length >= 3) {
    ins.push(`Across ${m.length} entries you average ${avg(m.map(x => x.sleep)).toFixed(1)} hours of sleep, ${avg(m.map(x => x.water)).toFixed(1)} glasses of water and pain level ${avg(m.map(x => x.pain)).toFixed(1)}/10.`);
    const a = m.filter(x => x.sleep >= 7), b = m.filter(x => x.sleep < 7);
    if (a.length && b.length) ins.push(`Your mood score averages ${avg(a.map(x => MOOD[x.mood])).toFixed(1)}/5 on days with 7+ hours of sleep versus ${avg(b.map(x => MOOD[x.mood])).toFixed(1)}/5 on shorter nights.`);
  }

  function showError(msg) {
    if (errorEl) {
      errorEl.style.display = 'flex';
      if (errorMsgEl) errorMsgEl.textContent = msg || 'Could not load insights.';
    }
  }

  function hideError() {
    if (errorEl) errorEl.style.display = 'none';
  }

  function renderData(data) {
    hideError();

    // ── 1. Task Completion Stats ─────────────────────────────────────────
    const tasks = data.task_stats || data.taskStats || {};
    const total = tasks.total ?? 0;
    const completed = tasks.completed ?? 0;
    const pct = tasks.completion_percentage ?? tasks.completionPercentage ?? 0;
    const todo = tasks.todo ?? 0;
    const inProgress = tasks.in_progress ?? tasks.inProgress ?? 0;
    const overdue = tasks.overdue ?? 0;

    const doneEl = $('#done');
    if (doneEl) {
      if (total > 0) {
        doneEl.innerHTML = `
          <div class="completion-info">
            <span class="completion-pct">${pct}% complete</span>
            <span class="mute">(${completed} of ${total} done)</span>
          </div>
          <div class="meter" role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100" aria-label="Task completion ${pct}%">
            <i style="width: ${pct}%"></i>
          </div>
          <p class="mute meter-sub">To do: ${todo} · In progress: ${inProgress}${overdue > 0 ? ` · <strong class="late">${overdue} overdue</strong>` : ''}</p>
        `;
      } else {
        doneEl.innerHTML = '<p class="empty">No tasks yet. Add tasks from the board to start tracking completion stats.</p>';
      }
    }

    // ── 2. Cycle Statistics ──────────────────────────────────────────────
    const cycle = data.cycle_stats || data.cycleStats || {};
    const hasCycle = cycle.has_data || cycle.hasData;
    const cycEl = $('#cyc');
    if (cycEl) {
      if (hasCycle) {
        const avgDays = cycle.average_cycle_length ?? cycle.averageCycleLength ?? 28;
        const count = cycle.cycle_count ?? cycle.cycleCount ?? 1;
        const nextPeriod = cycle.predicted_next_date || cycle.predictedNextDate;

        let html = `
          <div class="cycle-stat-highlight">
            <span class="cycle-stat-days">${avgDays}</span>
            <span class="mute">days average cycle</span>
          </div>
          <p class="mute">${count} cycle${count > 1 ? 's' : ''} logged on record.</p>
        `;
        if (nextPeriod) {
          html += `<div class="cycle-pill">Next period expected around ${fmt(nextPeriod)}</div>`;
        }
        if (count === 1) {
          html += `<p class="mute" style="font-size:0.83rem;margin-top:0.4rem;">Prediction based on your first entered cycle. Trend accuracy improves after 2+ logs.</p>`;
        }
        cycEl.innerHTML = html;
      } else {
        cycEl.innerHTML = '<p class="empty">No cycle data recorded. Log your first cycle in Wellness to see averages and predictions.</p>';
      }
    }

    // ── 3. Mood Trend (Last 7 Entries) ──────────────────────────────────
    const rawTrends = data.mood_trends || data.moodTrends || [];
    const trendEl = $('#trend');
    if (trendEl) {
      if (rawTrends.length > 0) {
        if (legendEl) legendEl.style.display = 'flex';
        const recentPoints = rawTrends.slice(-7);

        trendEl.innerHTML = recentPoints.map(p => {
          const moodStr = String(p.mood || 'NEUTRAL');
          const moodName = label(moodStr);
          const score = p.score || 3;
          const heightPct = Math.max(12, Math.min(100, (score / 5.0) * 100));
          const dateLabel = p.date ? String(p.date).slice(5) : '';
          const sleep = (p.sleep_hours ?? p.sleepHours ?? 0);
          const pain = (p.pain_level ?? p.painLevel ?? 0);

          return `
            <div class="col" title="${p.date}: ${moodName} (Score: ${score}/5, Sleep: ${sleep}h, Pain: ${pain}/10)">
              <span class="trend-score-pill">${moodName}</span>
              <div class="trend-bar-wrapper">
                <i style="height: ${heightPct}%;" tabindex="0" role="img" aria-label="${p.date}: ${moodName}, mood score ${score} out of 5"></i>
              </div>
              <small>${dateLabel}</small>
            </div>
          `;
        }).join('');
      } else {
        if (legendEl) legendEl.style.display = 'none';
        trendEl.innerHTML = '<p class="empty" style="width:100%;text-align:center;">Log your mood in Wellness to view your 7-entry trend chart.</p>';
      }
    }

    // ── 4. Written Insights ─────────────────────────────────────────────
    const insList = data.insights || [];
    const insEl = $('#ins');
    if (insEl) {
      if (insList.length > 0) {
        insEl.innerHTML = insList.map(text => `<li>${esc(text)}</li>`).join('');
      } else {
        insEl.innerHTML = '<li class="empty">Personalized insights appear once you log a few tasks or wellness entries.</li>';
      }
    }
  }

  function loadInsights() {
    showLoading(true);
    hideError();

    fetch('/api/analytics', { credentials: 'include' })
      .then(async res => {
        if (!res.ok) {
          const err = await res.json().catch(() => ({}));
          throw new Error(err.message || err.error || `Server error (${res.status})`);
        }
        return res.json();
      })
      .then(data => {
        showLoading(false);
        renderData(data);
      })
      .catch(err => {
        showLoading(false);
        // Fallback to local storage if API is offline
        const DB = (typeof window.DB !== 'undefined' && window.DB) ? window.DB : null;
        if (DB && typeof DB.mine === 'function') {
          try {
            renderOfflineFallback();
            return;
          } catch (e) {}
        }
        showError(err.message);
      });
  }

  function renderOfflineFallback() {
    const DB = (typeof window.DB !== 'undefined' && window.DB) ? window.DB : null;
    if (!DB || typeof DB.mine !== 'function') return;
    const tasks = DB.mine('tasks') || [];
    const done = tasks.filter(x => x.status === 'DONE').length;
    const pct = tasks.length ? Math.round(done / tasks.length * 100) : 0;
    const FSV = (typeof window.FSV !== 'undefined' && window.FSV) ? window.FSV : null;
    const today = (FSV && typeof FSV.today === 'function') ? FSV.today() : new Date().toISOString().slice(0, 10);
    const overdue = tasks.filter(x => x.status !== 'DONE' && x.due < today).length;

    const moods = (DB.mine('moods') || []).sort((a, b) => a.date.localeCompare(b.date));
    const moodPoints = moods.map(m => ({
      date: m.date,
      mood: m.mood,
      score: (MOOD && MOOD[m.mood]) ? MOOD[m.mood] : 3,
      painLevel: m.pain || 0,
      sleepHours: m.sleep || 0,
      waterIntake: m.water || 0
    }));

    const cycleFn = (typeof window.cycleInfo === 'function') ? window.cycleInfo : (typeof cycleInfo === 'function' ? cycleInfo : null);
    const c = cycleFn ? cycleFn() : null;
    const ins = [];
    if (moods.length >= 3) {
      ins.push(`Across ${moods.length} entries you average ${avg(moods.map(x => x.sleep)).toFixed(1)} hours of sleep, ${avg(moods.map(x => x.water)).toFixed(1)} glasses of water and pain level ${avg(moods.map(x => x.pain)).toFixed(1)}/10.`);
    }
    if (tasks.length) {
      ins.push(`${done} of ${tasks.length} tasks are done (${pct}%).`);
    }

    renderData({
      task_stats: {
        total: tasks.length,
        completed: done,
        pending: tasks.length - done,
        todo: tasks.filter(x => x.status === 'TODO').length,
        in_progress: tasks.filter(x => x.status === 'IN_PROGRESS').length,
        overdue: overdue,
        completion_percentage: pct
      },
      cycle_stats: {
        has_data: !!c,
        average_cycle_length: c ? c.avg : null,
        cycle_count: c ? c.count : 0,
        predicted_next_date: c ? c.next : null
      },
      mood_trends: moodPoints,
      insights: ins
    });
  }

  if (retryBtn) {
    retryBtn.onclick = loadInsights;
  }

  loadInsights();
};
