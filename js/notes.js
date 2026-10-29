/* ============================================================
   notes.js  –  Member 2: Notes module frontend script
   
   Architecture:
   - NotesAPI   : wraps fetch('/api/notes'). Falls back to DB
                  (localStorage) when the Java backend is absent
                  so the page always works standalone for demos.
   - NotesUI    : pure DOM helpers (render, highlight, etc.)
   - NotesPage  : state management, wires everything together.
   
   JAVA INTEGRATION: remove the "useFetch" fallback block once
   the Java servlet is deployed and SESSION cookie is set.
   ============================================================ */

(function () {
  'use strict';

  /* ── helpers re-used from app.js ── */
  const $ = s => document.querySelector(s);
  const esc = s => String(s == null ? '' : s)
    .replace(/[&<>"']/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c]));
  const fmt = d => d ? new Date(d.includes('T') ? d : d + 'T00:00:00')
    .toLocaleDateString('en-IN', { day:'numeric', month:'short', year:'numeric' }) : '–';

  /* ── detect whether the Java API is reachable ── */
  const API_BASE = '/api/notes';
  let useBackend = false;  // set to true when the servlet responds

  /* ──────────────────────────────────────────────
     NotesAPI  – abstraction over fetch / localStorage
  ────────────────────────────────────────────── */
  const NotesAPI = {
    /* Try the Java REST API first; fall back to localStorage DB */

    async list(q = '', category = '') {
      if (useBackend) {
        const params = new URLSearchParams();
        if (q)        params.set('q', q);
        if (category) params.set('category', category);
        const r = await fetch(`${API_BASE}?${params}`, { credentials: 'include' });
        if (!r.ok) throw new Error(await _errMsg(r));
        return r.json();
      }
      /* localStorage fallback */
      let rows = DB.mine('notes');
      if (category) rows = rows.filter(n => n.category === category);
      if (q)        rows = rows.filter(n =>
        (n.title + ' ' + n.content).toLowerCase().includes(q.toLowerCase()));
      return rows.sort((a, b) => (b.updatedAt || b.createdAt) - (a.updatedAt || a.createdAt));
    },

    async create(payload) {
      if (useBackend) {
        const r = await fetch(API_BASE, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify(payload)
        });
        if (!r.ok) throw new Error(await _errMsg(r));
        return r.json();
      }
      const now = Date.now();
      return DB.add('notes', {
        ...payload,
        userId: Auth.id(),
        createdAt: now,
        updatedAt: now
      });
    },

    async update(id, payload) {
      if (useBackend) {
        const r = await fetch(`${API_BASE}/${id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify(payload)
        });
        if (!r.ok) throw new Error(await _errMsg(r));
        return r.json();
      }
      DB.upd('notes', id, { ...payload, updatedAt: Date.now() });
      return { ...DB.mine('notes').find(n => n.id === id) };
    },

    async remove(id) {
      if (useBackend) {
        const r = await fetch(`${API_BASE}/${id}`, {
          method: 'DELETE',
          credentials: 'include'
        });
        if (!r.ok) throw new Error(await _errMsg(r));
        return;
      }
      DB.del('notes', id);
    }
  };

  async function _errMsg(response) {
    try {
      const j = await response.clone().json();
      return j.message || j.error || response.statusText;
    } catch (_) {
      return response.statusText || `HTTP ${response.status}`;
    }
  }

  /* ──────────────────────────────────────────────
     NotesUI  – rendering helpers
  ────────────────────────────────────────────── */
  const NotesUI = {
    CATEGORY_ICONS: {
      General:'📝', Study:'📚', Personal:'👤', Ideas:'💡', Health:'❤️'
    },

    /** Build a note card article element */
    buildCard(note, query) {
      const art = document.createElement('article');
      art.className  = 'card note-card';
      art.dataset.id  = note.id || note.note_id;
      art.dataset.cat = note.category || 'General';
      art.setAttribute('role', 'listitem');
      art.setAttribute('tabindex', '0');

      const id    = note.id ?? note.note_id;
      const title = note.title || '';
      const body  = note.content || '';
      const cat   = note.category || 'General';
      const icon  = this.CATEGORY_ICONS[cat] || '📝';
      const upd   = note.updatedAt || note.updated_at;
      const crt   = note.createdAt || note.created_at;
      const dateStr = upd
        ? 'Updated ' + (typeof upd === 'number' ? fmt(new Date(upd).toISOString().slice(0,10)) : fmt(upd))
        : crt
        ? 'Created ' + (typeof crt === 'number' ? fmt(new Date(crt).toISOString().slice(0,10)) : fmt(crt))
        : '';

      const hlTitle = query ? this._highlight(title, query) : esc(title);
      const preview = body.slice(0, 180) + (body.length > 180 ? '…' : '');
      const hlPreview = query ? this._highlight(preview, query) : esc(preview);

      art.innerHTML = `
        <div class="note-card-head">
          <h3>${hlTitle}</h3>
          <span class="note-tag note-tag-${esc(cat)}" title="${esc(cat)}">
            ${icon} ${esc(cat)}
          </span>
        </div>
        <p class="note-preview">${hlPreview}</p>
        <p class="note-meta">${esc(dateStr)}</p>
        <div class="note-actions">
          <button class="btn ghost btn-sm"
                  data-act="edit" data-id="${id}"
                  aria-label="Edit note: ${esc(title)}">Edit</button>
          <button class="btn ghost btn-sm danger"
                  data-act="del"  data-id="${id}"
                  aria-label="Delete note: ${esc(title)}">Delete</button>
        </div>`;
      return art;
    },

    /** Wrap matched text with <mark> */
    _highlight(text, query) {
      if (!query) return esc(text);
      const safe = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      return esc(text).replace(new RegExp(`(${safe})`, 'gi'),
        '<mark>$1</mark>');
    },

    renderList(notes, query, container) {
      container.innerHTML = '';
      if (!notes.length) {
        container.appendChild(this.emptyState(query));
        return;
      }
      const frag = document.createDocumentFragment();
      notes.forEach(n => frag.appendChild(this.buildCard(n, query)));
      container.appendChild(frag);
    },

    emptyState(query) {
      const div = document.createElement('div');
      div.className = 'empty-state';
      if (query) {
        div.innerHTML = `
          <div class="empty-icon">🔍</div>
          <h3>No results for "${esc(query)}"</h3>
          <p>Try different keywords or clear the search.</p>`;
      } else {
        div.innerHTML = `
          <div class="empty-icon">📓</div>
          <h3>No notes yet</h3>
          <p>Tap <strong>New note</strong> to capture your first thought.</p>`;
      }
      return div;
    }
  };

  /* ──────────────────────────────────────────────
     NotesPage  – main controller
  ────────────────────────────────────────────── */
  const NotesPage = {
    state: {
      notes: [],
      query: '',
      category: '',
      sortBy: 'updated',
      editingId: null,
      pendingDeleteId: null,
      loading: false
    },

    /* DOM refs */
    el: {},

    init() {
      this.el = {
        btnNew:        $('#btnNewNote'),
        formCard:      $('#noteFormCard'),
        formTitle:     $('#formTitle'),
        form:          $('#f'),
        btnCancel:     $('#btnCancelNote'),
        btnCancel2:    $('#btnCancelNote2'),
        btnSubmit:     $('#btnSubmitNote'),
        serverErr:     $('#form-server-error'),
        list:          $('#list'),
        q:             $('#q'),
        cat:           $('#cat'),
        sortBy:        $('#sortBy'),
        btnClearQ:     $('#btnClearSearch'),
        resultsCount:  $('#resultsCount'),
        loadingState:  $('#loadingState'),
        apiBanner:     $('#apiBanner'),
        apiBannerMsg:  $('#apiBannerMsg'),
        btnRetry:      $('#btnRetry'),
        dlg:           $('#deleteDialog'),
        confirmDel:    $('#confirmDelete'),
        cancelDel:     $('#cancelDelete'),
        titleInput:    $('#note-title'),
        contentInput:  $('#note-content'),
        charTitle:     $('#char-title'),
        charContent:   $('#char-content')
      };

      this._bindEvents();
      this._initCharCounters();
      this._checkBackend().then(() => this.load());
    },

    /* Silently probe the backend; if it responds, switch to fetch mode */
    async _checkBackend() {
      try {
        const r = await fetch(API_BASE, { credentials: 'include', signal: AbortSignal.timeout(2000) });
        if (r.status === 200 || r.status === 401) {
          useBackend = (r.status === 200);
        }
      } catch (_) { /* no backend yet */ }
    },

    _bindEvents() {
      const { btnNew, formCard, form, btnCancel, btnCancel2, btnSubmit,
              q, cat, sortBy, btnClearQ, list, dlg,
              confirmDel, cancelDel, btnRetry } = this.el;

      /* open / close form */
      btnNew.addEventListener('click', () => this.openForm());
      btnCancel.addEventListener('click', () => this.closeForm());
      btnCancel2.addEventListener('click', () => this.closeForm());

      /* form submit */
      FSV.live(form);
      form.addEventListener('submit', e => {
        e.preventDefault();
        if (!FSV.check(form)) return;
        this.saveNote(Object.fromEntries(new FormData(form)));
      });

      /* search & filter */
      q.addEventListener('input', () => {
        this.state.query = q.value.trim();
        btnClearQ.hidden = !this.state.query;
        this._debounceLoad();
      });
      btnClearQ.addEventListener('click', () => {
        q.value = ''; this.state.query = '';
        btnClearQ.hidden = true;
        this.load();
      });
      cat.addEventListener('change', () => {
        this.state.category = cat.value;
        this.load();
      });
      sortBy.addEventListener('change', () => {
        this.state.sortBy = sortBy.value;
        this._sortAndRender();
      });

      /* note list actions (edit / delete) via delegation */
      list.addEventListener('click', e => {
        const btn = e.target.closest('button[data-act]');
        if (!btn) return;
        const id = btn.dataset.id;
        if (btn.dataset.act === 'edit') this.startEdit(id);
        if (btn.dataset.act === 'del')  this.confirmDelete(id);
      });
      list.addEventListener('keydown', e => {
        if (e.key === 'Enter' || e.key === ' ') {
          const btn = e.target.closest('button[data-act]');
          if (btn) btn.click();
        }
      });

      /* delete dialog */
      confirmDel.addEventListener('click', () => this.executeDelete());
      cancelDel.addEventListener('click',  () => dlg.close());
      dlg.addEventListener('click', e => { if (e.target === dlg) dlg.close(); });

      /* retry button */
      btnRetry.addEventListener('click', () => this.load());
    },

    /* Character counters for title and content */
    _initCharCounters() {
      const attach = (input, counter, max) => {
        const update = () => {
          const len  = input.value.length;
          const left = max - len;
          counter.textContent = `${len} / ${max}`;
          counter.classList.toggle('near', left <= 20 && left > 0);
          counter.classList.toggle('over', left < 0);
        };
        input.addEventListener('input', update);
        update();
      };
      attach(this.el.titleInput,   this.el.charTitle,   100);
      attach(this.el.contentInput, this.el.charContent, 2000);
    },

    /* ── load notes (with search + filter applied server-side) ── */
    async load() {
      this._setLoading(true);
      this._hideBanner();
      try {
        const notes = await NotesAPI.list(this.state.query, this.state.category);
        this.state.notes = notes;
        this._sortAndRender();
      } catch (err) {
        this._showBanner('Could not load notes: ' + err.message);
      } finally {
        this._setLoading(false);
      }
    },

    _debounceTimer: null,
    _debounceLoad() {
      clearTimeout(this._debounceTimer);
      this._debounceTimer = setTimeout(() => this.load(), 280);
    },

    _sortAndRender() {
      const { notes, sortBy, query } = this.state;
      const sorted = [...notes].sort((a, b) => {
        if (sortBy === 'title') return (a.title||'').localeCompare(b.title||'');
        if (sortBy === 'created') {
          const ac = a.createdAt || a.created_at || 0;
          const bc = b.createdAt || b.created_at || 0;
          return (typeof bc === 'number' ? bc : Date.parse(bc)) -
                 (typeof ac === 'number' ? ac : Date.parse(ac));
        }
        /* default: last updated */
        const au = a.updatedAt || a.updated_at || a.createdAt || a.created_at || 0;
        const bu = b.updatedAt || b.updated_at || b.createdAt || b.created_at || 0;
        return (typeof bu === 'number' ? bu : Date.parse(bu)) -
               (typeof au === 'number' ? au : Date.parse(au));
      });
      NotesUI.renderList(sorted, query, this.el.list);
      const total = sorted.length;
      this.el.resultsCount.textContent = total === 0
        ? ''
        : `${total} note${total !== 1 ? 's' : ''}${query ? ` matching "${query}"` : ''}`;
    },

    /* ── form open / close ── */
    openForm(note = null) {
      const { formCard, formTitle, form, btnNew } = this.el;
      formCard.hidden = false;
      formCard.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      btnNew.setAttribute('aria-expanded', 'true');

      if (note) {
        const id = note.id ?? note.note_id;
        this.state.editingId = id;
        formTitle.textContent = 'Edit note';
        formCard.classList.add('editing-mode');
        form.elements['id'].value        = id;
        form.elements['title'].value     = note.title    || '';
        form.elements['category'].value  = note.category || 'General';
        form.elements['content'].value   = note.content  || '';
        this.el.btnSubmit.textContent    = 'Save changes';
        /* trigger char counters */
        ['title','content'].forEach(n =>
          form.elements[n].dispatchEvent(new Event('input')));
      } else {
        this.state.editingId = null;
        formTitle.textContent = 'New note';
        formCard.classList.remove('editing-mode');
        form.reset();
        form.elements['id'].value     = '';
        this.el.btnSubmit.textContent = 'Save note';
        FSV.clear(form);
      }
      this.el.titleInput.focus();
    },

    closeForm() {
      const { formCard, form, btnNew } = this.el;
      formCard.hidden = true;
      formCard.classList.remove('editing-mode');
      form.reset();
      FSV.clear(form);
      this.state.editingId = null;
      btnNew.setAttribute('aria-expanded', 'false');
      btnNew.focus();
    },

    /* ── save (create or update) ── */
    async saveNote(v) {
      const payload = {
        title:    v.title.trim(),
        content:  v.content.trim(),
        category: v.category
      };

      /* Client-side length guard (mirrors server-side validation) */
      if (payload.title.length > 100) {
        FSV.fail(this.el.form, 'title', 'Title must be 100 characters or fewer.');
        return;
      }
      if (payload.content.length > 2000) {
        FSV.fail(this.el.form, 'content', 'Note must be 2000 characters or fewer.');
        return;
      }

      this.el.btnSubmit.disabled = true;
      this.el.btnSubmit.textContent = 'Saving…';
      this._hideServerError();

      try {
        const editingId = this.state.editingId;
        if (editingId) {
          await NotesAPI.update(+editingId, payload);
          say('Note updated ✓');
        } else {
          await NotesAPI.create(payload);
          say('Note saved ✓');
        }
        this.closeForm();
        await this.load();
      } catch (err) {
        this._showServerError(err.message);
      } finally {
        this.el.btnSubmit.disabled = false;
        this.el.btnSubmit.textContent =
          this.state.editingId ? 'Save changes' : 'Save note';
      }
    },

    /* ── edit ── */
    startEdit(id) {
      const note = this.state.notes.find(
        n => String(n.id ?? n.note_id) === String(id)
      );
      if (note) this.openForm(note);
    },

    /* ── delete ── */
    confirmDelete(id) {
      this.state.pendingDeleteId = id;
      this.el.dlg.showModal();
      this.el.confirmDel.focus();
    },

    async executeDelete() {
      const id = this.state.pendingDeleteId;
      if (!id) return;
      this.el.confirmDel.disabled = true;
      this.el.confirmDel.textContent = 'Deleting…';
      try {
        await NotesAPI.remove(+id);
        this.el.dlg.close();
        say('Note deleted');
        await this.load();
      } catch (err) {
        this.el.dlg.close();
        this._showBanner('Could not delete note: ' + err.message);
      } finally {
        this.state.pendingDeleteId = null;
        this.el.confirmDel.disabled = false;
        this.el.confirmDel.textContent = 'Yes, delete it';
      }
    },

    /* ── UI state helpers ── */
    _setLoading(on) {
      this.state.loading         = on;
      this.el.loadingState.hidden = !on;
      this.el.list.hidden         = on;
    },
    _showBanner(msg) {
      this.el.apiBannerMsg.textContent = msg;
      this.el.apiBanner.hidden = false;
    },
    _hideBanner()  { this.el.apiBanner.hidden = true; },
    _showServerError(msg) {
      this.el.serverErr.textContent = msg;
      this.el.serverErr.hidden = false;
    },
    _hideServerError() { this.el.serverErr.hidden = true; }
  };

  /* ── boot ── */
  /* notes.js is loaded at end of <body>, so DOM is already parsed.
     DOMContentLoaded has already fired — call init() directly.
     Guard: only run on the notes page (data-page="notes"). */
  if (document.body.dataset.page === 'notes') {
    NotesPage.init();
  }

  /* Expose for debugging / integration tests */
  window._NotesAPI  = NotesAPI;
  window._NotesPage = NotesPage;

})();
