/**
 * FlowSpace - Member 5: Mood Tracker Module
 * File: js/mood.js
 * 
 * Features:
 * - Full REST API integration via fetch('/api/moods') with automatic offline/localStorage fallback.
 * - Interactive mood chips + dropdown sync.
 * - Custom sliders for pain, sleep, water with live value formatting and color badges.
 * - Upsert support: saving on an existing date updates the record.
 * - Edit mode: clicking 'Edit' in recent entries pre-fills the form for that date.
 * - Server & client side validation (date not in future, pain 0-10, sleep 0-24, water 0-30).
 */

const MoodModule = (() => {
  const MOOD_EMOJIS = {
    HAPPY: '😊',
    CALM: '😌',
    NEUTRAL: '😐',
    TIRED: '😴',
    ANXIOUS: '😰',
    IRRITABLE: '😣',
    SAD: '😢'
  };

  let editModeDate = null;

  // Detect whether backend API is reachable
  let apiAvailable = null;

  async function checkApi() {
    if (apiAvailable !== null) return apiAvailable;
    try {
      const res = await fetch('/api/moods', { method: 'GET', headers: { 'Accept': 'application/json' } });
      apiAvailable = res.ok || res.status === 401; // API exists
    } catch (e) {
      apiAvailable = false;
    }
    return apiAvailable;
  }

  // --- Data Access Layer ---
  async function fetchRecentMoods(limit = 10) {
    const isOnline = await checkApi();
    if (isOnline) {
      try {
        const res = await fetch(`/api/moods?limit=${limit}`);
        if (res.ok) {
          const data = await res.json();
          // Keep local cache in sync
          syncLocalCache(data);
          return data;
        }
      } catch (err) {
        console.warn('API fetch failed, falling back to local cache', err);
      }
    }
    // Local fallback
    return getLocalMoods().slice(0, limit);
  }

  async function saveMoodEntry(entry) {
    const isOnline = await checkApi();
    if (isOnline) {
      try {
        const res = await fetch('/api/moods', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(entry)
        });
        if (res.ok) {
          const saved = await res.json();
          saveLocalMood(saved);
          return { success: true, entry: saved };
        } else {
          const errData = await res.json().catch(() => ({}));
          return { success: false, error: errData.error || 'Server validation error' };
        }
      } catch (err) {
        console.warn('API post failed, falling back to local store', err);
      }
    }

    // Local fallback save
    const saved = saveLocalMood(entry);
    return { success: true, entry: saved };
  }

  async function deleteMoodEntry(date, id) {
    const isOnline = await checkApi();
    if (isOnline && id) {
      try {
        await fetch(`/api/moods?id=${id}&date=${date}`, { method: 'DELETE' });
      } catch (e) {
        console.warn('API delete failed', e);
      }
    }
    deleteLocalMood(date);
    return { success: true };
  }

  // --- Local Storage Helpers ---
  function getLocalMoods() {
    try {
      const data = JSON.parse(localStorage.getItem('flowspace')) || { moods: [] };
      const uid = +localStorage.getItem('fs_session') || 1;
      return (data.moods || [])
        .filter(m => !m.userId || m.userId === uid)
        .sort((a, b) => b.date.localeCompare(a.date));
    } catch (e) {
      return [];
    }
  }

  function saveLocalMood(row) {
    const data = JSON.parse(localStorage.getItem('flowspace')) || { moods: [], seq: 1 };
    const uid = +localStorage.getItem('fs_session') || 1;
    if (!data.moods) data.moods = [];

    // Normalize date and fields
    const date = row.date || row.entry_date;
    const mood = row.mood;
    const pain = +(row.pain ?? row.pain_level ?? 0);
    const sleep = +(row.sleep ?? row.sleep_hours ?? 0);
    const water = +(row.water ?? row.water_intake ?? 0);
    const symptoms = (row.symptoms || '').trim();

    const normalized = {
      id: row.id || row.entry_id || (data.seq++),
      userId: uid,
      date,
      entry_date: date,
      mood,
      pain,
      pain_level: pain,
      sleep,
      sleep_hours: sleep,
      water,
      water_intake: water,
      symptoms,
      createdAt: row.createdAt || Date.now()
    };

    const exIndex = data.moods.findIndex(m => (!m.userId || m.userId === uid) && (m.date === date || m.entry_date === date));
    if (exIndex >= 0) {
      data.moods[exIndex] = { ...data.moods[exIndex], ...normalized };
    } else {
      data.moods.push(normalized);
    }

    localStorage.setItem('flowspace', JSON.stringify(data));
    return normalized;
  }

  function deleteLocalMood(date) {
    const data = JSON.parse(localStorage.getItem('flowspace')) || { moods: [] };
    const uid = +localStorage.getItem('fs_session') || 1;
    data.moods = (data.moods || []).filter(m => !( (!m.userId || m.userId === uid) && (m.date === date || m.entry_date === date) ));
    localStorage.setItem('flowspace', JSON.stringify(data));
  }

  function syncLocalCache(serverMoods) {
    if (!Array.isArray(serverMoods)) return;
    const data = JSON.parse(localStorage.getItem('flowspace')) || { moods: [], seq: 1 };
    serverMoods.forEach(sm => {
      saveLocalMood(sm);
    });
  }

  // --- UI Updates & Sliders ---
  function updatePainBadge(val) {
    const badge = document.getElementById('painVal');
    if (!badge) return;
    val = parseInt(val, 10);
    badge.textContent = `${val} / 10`;
    badge.className = 'slider-val-badge';
    if (val <= 3) badge.classList.add('pain-low');
    else if (val <= 6) badge.classList.add('pain-med');
    else badge.classList.add('pain-high');
  }

  function updateSleepBadge(val) {
    const badge = document.getElementById('sleepVal');
    if (!badge) return;
    badge.textContent = `${parseFloat(val).toFixed(1)} hrs`;
  }

  function updateWaterBadge(val) {
    const badge = document.getElementById('waterVal');
    if (!badge) return;
    badge.textContent = `${parseInt(val, 10)} glasses`;
  }

  function syncSliders(form) {
    if (!form) return;
    const painInput = form.elements.namedItem('pain');
    if (painInput) updatePainBadge(painInput.value);

    const sleepInput = form.elements.namedItem('sleep');
    if (sleepInput) updateSleepBadge(sleepInput.value);

    const waterInput = form.elements.namedItem('water');
    if (waterInput) updateWaterBadge(waterInput.value);
  }

  function syncMoodButtons(form, selectedMood) {
    const buttons = document.querySelectorAll('.mood-btn');
    buttons.forEach(btn => {
      if (btn.dataset.mood === selectedMood) {
        btn.classList.add('active');
        btn.setAttribute('aria-pressed', 'true');
      } else {
        btn.classList.remove('active');
        btn.setAttribute('aria-pressed', 'false');
      }
    });

    const select = form.elements.namedItem('mood');
    if (select && select.value !== selectedMood) {
      select.value = selectedMood;
    }
  }

  function setEditMode(form, entry) {
    editModeDate = entry.date || entry.entry_date;
    form.elements.date.value = editModeDate;
    form.elements.mood.value = entry.mood;
    form.elements.symptoms.value = entry.symptoms || '';
    form.elements.pain.value = entry.pain ?? entry.pain_level ?? 0;
    form.elements.sleep.value = entry.sleep ?? entry.sleep_hours ?? 7;
    form.elements.water.value = entry.water ?? entry.water_intake ?? 6;

    syncMoodButtons(form, entry.mood);
    syncSliders(form);

    let banner = document.getElementById('moodEditBanner');
    if (!banner) {
      banner = document.createElement('div');
      banner.id = 'moodEditBanner';
      banner.className = 'edit-banner';
      form.insertBefore(banner, form.firstChild);
    }
    banner.innerHTML = `<span>Editing entry for <b>${editModeDate}</b></span><button type="button" class="cancel-edit-btn" id="cancelEditBtn">Cancel</button>`;
    
    document.getElementById('cancelEditBtn').onclick = () => {
      cancelEditMode(form);
    };

    const submitBtn = form.querySelector('button[type=submit], button.btn');
    if (submitBtn) submitBtn.textContent = 'Update entry';

    form.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function cancelEditMode(form) {
    editModeDate = null;
    const banner = document.getElementById('moodEditBanner');
    if (banner) banner.remove();

    form.reset();
    form.elements.date.value = (typeof FSV !== 'undefined' && FSV.today) ? FSV.today() : new Date().toISOString().slice(0, 10);
    form.elements.pain.value = 0;
    form.elements.sleep.value = 7;
    form.elements.water.value = 6;
    syncSliders(form);
    syncMoodButtons(form, '');

    const submitBtn = form.querySelector('button[type=submit], button.btn');
    if (submitBtn) submitBtn.textContent = 'Save entry';
  }

  async function renderTable() {
    const tbody = document.getElementById('moods');
    if (!tbody) return;

    const entries = await fetchRecentMoods(15);
    if (!entries.length) {
      tbody.innerHTML = '<tr><td colspan="7" class="mute" style="text-align:center;padding:1.5rem">No entries recorded yet. Log your first mood above!</td></tr>';
      return;
    }

    tbody.innerHTML = entries.map(x => {
      const d = x.date || x.entry_date;
      const formattedDate = (typeof fmt === 'function') ? fmt(d) : d;
      const moodLabel = x.mood ? (x.mood.charAt(0) + x.mood.slice(1).toLowerCase()) : '-';
      const emoji = MOOD_EMOJIS[x.mood] || '';
      const pain = x.pain ?? x.pain_level ?? 0;
      const sleep = x.sleep ?? x.sleep_hours ?? 0;
      const water = x.water ?? x.water_intake ?? 0;
      const symptoms = (x.symptoms && x.symptoms.trim()) ? (typeof esc === 'function' ? esc(x.symptoms) : x.symptoms) : '-';
      const id = x.id || x.entry_id || '';

      return `
        <tr data-date="${d}">
          <td><strong>${formattedDate}</strong></td>
          <td><span class="mood-pill ${x.mood}">${emoji} ${moodLabel}</span></td>
          <td>${pain} / 10</td>
          <td>${sleep} hrs</td>
          <td>${water} cups</td>
          <td title="${symptoms}">${symptoms}</td>
          <td>
            <div class="table-actions">
              <button type="button" class="btn ghost edit-mood-btn" data-date="${d}" aria-label="Edit entry for ${d}">Edit</button>
              <button type="button" class="btn ghost del-mood-btn" data-date="${d}" data-id="${id}" aria-label="Delete entry for ${d}">Delete</button>
            </div>
          </td>
        </tr>
      `;
    }).join('');

    // Attach row events
    tbody.querySelectorAll('.edit-mood-btn').forEach(btn => {
      btn.onclick = async () => {
        const date = btn.dataset.date;
        const entry = entries.find(e => (e.date || e.entry_date) === date);
        const form = document.getElementById('mood');
        if (entry && form) {
          setEditMode(form, entry);
        }
      };
    });

    tbody.querySelectorAll('.del-mood-btn').forEach(btn => {
      btn.onclick = async () => {
        const date = btn.dataset.date;
        const id = btn.dataset.id;
        if (confirm(`Delete the wellness entry for ${date}?`)) {
          await deleteMoodEntry(date, id);
          if (typeof say === 'function') say('Entry deleted');
          renderTable();
          if (editModeDate === date) {
            cancelEditMode(document.getElementById('mood'));
          }
        }
      };
    });
  }

  // --- Initializer ---
  function init() {
    const form = document.getElementById('mood');
    if (!form) return;

    // Set today's date if empty
    if (!form.elements.date.value) {
      form.elements.date.value = (typeof FSV !== 'undefined' && FSV.today) ? FSV.today() : new Date().toISOString().slice(0, 10);
    }

    // Attach live slider listeners
    form.addEventListener('input', e => {
      if (['pain', 'sleep', 'water'].includes(e.target.name)) {
        syncSliders(form);
      }
    });

    // Attach mood buttons listener
    document.querySelectorAll('.mood-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const mood = btn.dataset.mood;
        syncMoodButtons(form, mood);
      });
    });

    // Sync select dropdown changes to mood buttons
    const select = form.elements.namedItem('mood');
    if (select) {
      select.addEventListener('change', () => {
        syncMoodButtons(form, select.value);
      });
    }

    // Initial slider values
    syncSliders(form);

    // Form submission with upsert logic & server fetch
    form.addEventListener('submit', async e => {
      e.preventDefault();

      if (typeof FSV !== 'undefined' && !FSV.check(form)) {
        return;
      }

      const fd = new FormData(form);
      const entryDate = fd.get('date');
      const mood = fd.get('mood');

      if (!entryDate) {
        alert('Please choose a date.');
        return;
      }
      if (!mood) {
        alert('Please select a mood.');
        return;
      }

      const entry = {
        date: entryDate,
        entry_date: entryDate,
        mood: mood,
        symptoms: (fd.get('symptoms') || '').trim(),
        pain: parseInt(fd.get('pain') || '0', 10),
        pain_level: parseInt(fd.get('pain') || '0', 10),
        sleep: parseFloat(fd.get('sleep') || '0'),
        sleep_hours: parseFloat(fd.get('sleep') || '0'),
        water: parseInt(fd.get('water') || '0', 10),
        water_intake: parseInt(fd.get('water') || '0', 10)
      };

      const result = await saveMoodEntry(entry);
      if (result.success) {
        if (typeof say === 'function') {
          say(editModeDate ? 'Entry updated' : 'Wellness entry saved');
        }
        cancelEditMode(form);
        await renderTable();
      } else {
        alert('Failed to save entry: ' + (result.error || 'Unknown error'));
      }
    });

    // Initial table render
    renderTable();
  }

  return {
    init,
    renderTable,
    fetchRecentMoods,
    saveMoodEntry,
    deleteMoodEntry
  };
})();

// Auto-initialize when on wellness page
document.addEventListener('DOMContentLoaded', () => {
  if (document.body.dataset.page === 'wellness') {
    MoodModule.init();
  }
});
