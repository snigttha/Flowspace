/**
 * FlowSpace - Member 5: Global Search Module
 * File: js/search.js
 * 
 * Features:
 * - Real-time global search across Notes, Tasks, and Mood Entries (Polymorphic summaries).
 * - Dual-layer search: Merges backend `/api/search` and browser local storage.
 * - Smart Relevance Ranking:
 *     * Score 100: Title starts with search query (e.g. "a" -> "ambience" ranks #1)
 *     * Score 80: Word in title starts with search query
 *     * Score 60: Title contains search query
 *     * Score 35: Category / tag / priority contains search query
 *     * Score 10: Body content / symptoms contains search query
 * - Deduplicates results across server and local storage.
 * - Keyboard navigation (ArrowUp, ArrowDown, Enter, Esc).
 * - Global shortcut: press '/' or 'Ctrl+K' to focus search.
 */

const GlobalSearch = (() => {
  let searchTimeout = null;
  let activeIndex = -1;
  let currentResults = [];

  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  // Relevance scorer to prioritize exact/prefix matches in titles
  function computeRelevanceScore(item, q) {
    const title = (item.title || '').toLowerCase().trim();
    const summary = (item.summary || '').toLowerCase();
    const type = (item.type || '').toLowerCase();

    // 1. Title starts with search term (e.g. query "a" matches "ambience") -> Highest priority!
    if (title.startsWith(q)) {
      return 100;
    }

    // 2. A word inside title starts with search term
    const words = title.split(/\s+/);
    if (words.some(w => w.startsWith(q))) {
      return 80;
    }

    // 3. Title contains search term
    if (title.includes(q)) {
      return 60;
    }

    // 4. Type or tag contains search term
    if (type.includes(q)) {
      return 35;
    }

    // 5. Summary / body content contains search term
    if (summary.includes(q)) {
      return 15;
    }

    return 1;
  }

  // Query server API with fast timeout
  async function queryApiSearch(q) {
    if (typeof window !== 'undefined' && window.location.protocol === 'file:') {
      return [];
    }

    try {
      const uid = (typeof Auth !== 'undefined' && Auth.id && Auth.id()) || +localStorage.getItem('fs_session') || 1;
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 600); // 600ms quick timeout

      const res = await fetch(`/api/search?q=${encodeURIComponent(q)}`, {
        headers: { 
          'Accept': 'application/json',
          'X-User-Id': String(uid)
        },
        signal: controller.signal
      });
      clearTimeout(timeoutId);

      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data)) {
          return data;
        }
      }
    } catch (e) {
      // Backend offline or timeout -> will use local search
    }

    return [];
  }

  // Client-side search across localStorage and in-memory DB
  function queryLocalSearch(query) {
    const q = query.toLowerCase().trim();
    if (!q) return [];

    let db = { notes: [], tasks: [], moods: [] };
    try {
      db = JSON.parse(localStorage.getItem('flowspace')) || db;
    } catch (e) {}

    // Include in-memory DB from app.js if present
    if (typeof DB !== 'undefined' && DB.d) {
      if (!db.notes || !db.notes.length) db.notes = DB.d.notes || [];
      if (!db.tasks || !db.tasks.length) db.tasks = DB.d.tasks || [];
      if (!db.moods || !db.moods.length) db.moods = DB.d.moods || [];
    }

    const uid = (typeof Auth !== 'undefined' && Auth.id && Auth.id()) || +localStorage.getItem('fs_session') || 1;
    const results = [];

    // 1. Search Notes (implements Note.summary())
    (db.notes || [])
      .filter(n => (!n.userId || n.userId === uid) && (
        (n.title && n.title.toLowerCase().includes(q)) ||
        (n.content && n.content.toLowerCase().includes(q)) ||
        (n.category && n.category.toLowerCase().includes(q))
      ))
      .forEach(n => {
        const cat = n.category || 'General';
        const snippet = n.content ? (n.content.length > 70 ? n.content.slice(0, 67) + '...' : n.content) : '';
        results.push({
          type: 'NOTE',
          id: n.id,
          title: n.title,
          summary: `Note [${cat}]: ${n.title} — ${snippet}`,
          date: n.updatedAt ? (typeof ymd === 'function' ? ymd(n.updatedAt) : new Date(n.updatedAt).toISOString().slice(0, 10)) : '',
          url: 'notes.html'
        });
      });

    // 2. Search Tasks (implements Task.summary())
    (db.tasks || [])
      .filter(t => (!t.userId || t.userId === uid) && (
        (t.title && t.title.toLowerCase().includes(q)) ||
        (t.priority && t.priority.toLowerCase().includes(q)) ||
        (t.status && t.status.toLowerCase().includes(q))
      ))
      .forEach(t => {
        results.push({
          type: 'TASK',
          id: t.id,
          title: t.title,
          summary: `Task (${t.priority}): ${t.title} — Due ${t.due || '-'} [Status: ${t.status || 'TODO'}]`,
          date: t.due || '',
          url: 'tasks.html'
        });
      });

    // 3. Search Mood Entries (implements MoodEntry.summary())
    (db.moods || [])
      .filter(m => (!m.userId || m.userId === uid) && (
        (m.mood && m.mood.toLowerCase().includes(q)) ||
        (m.symptoms && m.symptoms.toLowerCase().includes(q)) ||
        (m.date && m.date.includes(q)) ||
        (m.entry_date && m.entry_date.includes(q))
      ))
      .forEach(m => {
        const d = m.date || m.entry_date;
        const pain = m.pain ?? m.pain_level ?? 0;
        const sleep = m.sleep ?? m.sleep_hours ?? 0;
        const water = m.water ?? m.water_intake ?? 0;
        const symptoms = m.symptoms ? ` | Notes: ${m.symptoms}` : '';
        results.push({
          type: 'MOOD',
          id: m.id || m.entry_id,
          title: `${m.mood} on ${d}`,
          summary: `Mood Entry (${d}): ${m.mood} | Sleep: ${sleep}h | Water: ${water} cups | Pain: ${pain}/10${symptoms}`,
          date: d,
          url: 'wellness.html'
        });
      });

    return results;
  }

  // Unified Search: Merges Local + Server Results and sorts by Relevance!
  async function executeSearch(query) {
    const q = query.toLowerCase().trim();
    if (!q) return [];

    // Query both local storage and server simultaneously
    const localItems = queryLocalSearch(q);
    const serverItems = await queryApiSearch(q);

    // Merge & Deduplicate (Local items take precedence as user's latest browser state)
    const combined = [];
    const seen = new Set();

    function pushItem(item) {
      if (!item || !item.title) return;
      const key = `${item.type}_${item.title.trim().toLowerCase()}`;
      if (!seen.has(key)) {
        seen.add(key);
        // Calculate relevance score
        item._score = computeRelevanceScore(item, q);
        combined.push(item);
      }
    }

    // Add local items first
    localItems.forEach(pushItem);

    // Add server items next
    serverItems.forEach(pushItem);

    // Sort by relevance score descending!
    // Items starting with 'q' appear at the top!
    combined.sort((a, b) => (b._score || 0) - (a._score || 0));

    return combined;
  }

  function renderDropdown(items, dropdown, query) {
    currentResults = items;
    activeIndex = -1;

    if (!dropdown) return;

    if (!items.length) {
      dropdown.innerHTML = `
        <div class="search-empty">
          <p style="font-weight:600;margin-bottom:0.25rem;">No results found for "${esc(query)}"</p>
          <small class="mute">Try searching for notes, tasks, or wellness logs.</small>
        </div>`;
      dropdown.classList.add('active');
      return;
    }

    const typeCounts = items.reduce((acc, it) => {
      acc[it.type] = (acc[it.type] || 0) + 1;
      return acc;
    }, {});

    const countSummary = Object.entries(typeCounts)
      .map(([k, v]) => `${v} ${k.toLowerCase()}${v > 1 ? 's' : ''}`)
      .join(', ');

    let html = `
      <div class="search-results-header">
        <span>Found ${items.length} items (${countSummary})</span>
        <button type="button" class="search-close-tag" id="closeSearchBtn" aria-label="Close search">&times; Close</button>
      </div>
    `;

    items.forEach((item, index) => {
      const tagClass = item.type.toLowerCase();
      html += `
        <a class="search-result-item" data-index="${index}" href="${item.url}">
          <div class="search-result-top">
            <span class="search-result-title">${esc(item.title)}</span>
            <span class="search-tag ${tagClass}">${item.type}</span>
          </div>
          <div class="search-result-summary">${esc(item.summary)}</div>
          ${item.date ? `<small class="search-result-date">Date: ${esc(item.date)}</small>` : ''}
        </a>
      `;
    });

    dropdown.innerHTML = html;
    dropdown.classList.add('active');

    const closeBtn = document.getElementById('closeSearchBtn');
    if (closeBtn) {
      closeBtn.onclick = (e) => {
        e.stopPropagation();
        closeDropdown();
      };
    }

    // Attach click events
    dropdown.querySelectorAll('.search-result-item').forEach(el => {
      el.addEventListener('mouseenter', () => {
        highlightItem(parseInt(el.dataset.index, 10));
      });
    });
  }

  function highlightItem(index) {
    const dropdown = document.getElementById('searchResultsDropdown');
    if (!dropdown) return;
    const items = dropdown.querySelectorAll('.search-result-item');
    items.forEach(it => it.classList.remove('selected'));

    activeIndex = index;
    if (activeIndex >= 0 && activeIndex < items.length) {
      items[activeIndex].classList.add('selected');
      items[activeIndex].scrollIntoView({ block: 'nearest' });
    }
  }

  function closeDropdown() {
    const dropdown = document.getElementById('searchResultsDropdown');
    if (dropdown) dropdown.classList.remove('active');
    activeIndex = -1;
  }

  function bindEvents() {
    if (typeof document === 'undefined' || !document.getElementById) return;
    const input = document.getElementById('globalSearchInput');
    const dropdown = document.getElementById('searchResultsDropdown');
    const clearBtn = document.getElementById('searchClearBtn');

    if (!input || !dropdown) return;

    if (input._bound) return;
    input._bound = true;

    input.addEventListener('input', () => {
      clearTimeout(searchTimeout);
      const val = input.value.trim();
      if (!val) {
        closeDropdown();
        return;
      }

      searchTimeout = setTimeout(async () => {
        const results = await executeSearch(val);
        renderDropdown(results, dropdown, val);
      }, 120);
    });

    if (clearBtn) {
      clearBtn.addEventListener('click', (e) => {
        e.preventDefault();
        input.value = '';
        closeDropdown();
        input.focus();
      });
    }

    // Keyboard navigation
    input.addEventListener('keydown', e => {
      if (!dropdown.classList.contains('active')) {
        if (e.key === 'ArrowDown' && input.value.trim()) {
          input.dispatchEvent(new Event('input'));
        }
        return;
      }

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        const next = (activeIndex + 1) % currentResults.length;
        highlightItem(next);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        const prev = (activeIndex - 1 + currentResults.length) % currentResults.length;
        highlightItem(prev);
      } else if (e.key === 'Enter') {
        if (activeIndex >= 0 && currentResults[activeIndex]) {
          e.preventDefault();
          window.location.href = currentResults[activeIndex].url;
        }
      } else if (e.key === 'Escape') {
        closeDropdown();
      }
    });

    // Close when clicking outside
    document.addEventListener('click', e => {
      const container = document.getElementById('globalSearchContainer');
      if (container && !container.contains(e.target)) {
        closeDropdown();
      }
    });
  }

  function init() {
    bindEvents();

    // Global keyboard shortcut '/' or 'Ctrl+K' to focus search
    document.addEventListener('keydown', e => {
      const isInput = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName);
      if ((e.key === '/' && !isInput) || ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k')) {
        e.preventDefault();
        const input = document.getElementById('globalSearchInput');
        if (input) {
          input.focus();
          input.select();
        }
      }
    });
  }

  return {
    init,
    bindEvents,
    executeSearch,
    queryApiSearch,
    queryLocalSearch
  };
})();

// Auto-initialize
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', GlobalSearch.init);
} else {
  GlobalSearch.init();
}

if (typeof window !== 'undefined') window.GlobalSearch = GlobalSearch;
if (typeof module !== 'undefined' && module.exports) module.exports = GlobalSearch;
if (typeof global !== 'undefined') global.GlobalSearch = GlobalSearch;
