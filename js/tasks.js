/* ============================================================
   tasks.js  –  Member 3: Tasks module frontend script
   
   Architecture:
   - TasksAPI   : wraps fetch('/api/tasks'). Falls back to DB
                  (localStorage) when the Java backend is absent
                  so the page always works standalone for demos.
   - TasksUI    : pure DOM helpers (Kanban cards, overdue markers, badges)
   - TasksPage  : state management, metrics, status transitions, sort.
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
  const API_BASE = '/api/tasks';
  let useBackend = false;

  async function detectBackend() {
    try {
      const r = await fetch(API_BASE, { method: 'GET', credentials: 'include' });
      // If 200 OK or 401 Unauthorized, the servlet is active!
      if (r.ok || r.status === 401) {
        useBackend = true;
      }
    } catch {
      useBackend = false;
    }
  }

  async function _errMsg(response) {
    try {
      const j = await response.json();
      return j.error || j.message || `HTTP ${response.status}`;
    } catch {
      return `HTTP ${response.status}`;
    }
  }

  /* ──────────────────────────────────────────────
     TasksAPI  – abstraction over fetch / localStorage
  ────────────────────────────────────────────── */
  const TasksAPI = {
    async list(status = '', sort = 'due_date') {
      if (useBackend) {
        const params = new URLSearchParams();
        if (status) params.set('status', status);
        if (sort)   params.set('sort', sort);
        const r = await fetch(`${API_BASE}?${params}`, { credentials: 'include' });
        if (!r.ok) throw new Error(await _errMsg(r));
        return r.json();
      }
      /* localStorage fallback */
      let rows = typeof DB !== 'undefined' ? DB.mine('tasks') : [];
      if (status) rows = rows.filter(t => t.status === status);
      if (sort === 'priority') {
        const ranks = { HIGH: 1, MEDIUM: 2, LOW: 3 };
        rows.sort((a, b) => (ranks[a.priority] || 4) - (ranks[b.priority] || 4));
      } else {
        rows.sort((a, b) => new Date(a.dueDate || a.due_date || 0) - new Date(b.dueDate || b.due_date || 0));
      }
      return rows;
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
      const now = new Date().toISOString();
      const task = {
        id: Date.now(),
        task_id: Date.now(),
        userId: typeof Auth !== 'undefined' ? Auth.id() : 1,
        title: payload.title,
        dueDate: payload.dueDate || payload.due_date,
        due_date: payload.dueDate || payload.due_date,
        priority: payload.priority || 'MEDIUM',
        status: 'TODO',
        createdAt: now
      };
      if (typeof DB !== 'undefined') DB.add('tasks', task);
      return task;
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
      if (typeof DB !== 'undefined') {
        const row = DB.get('tasks', id);
        if (row) {
          Object.assign(row, payload);
          DB.put('tasks', row);
          return row;
        }
      }
      return payload;
    },

    async updateStatus(id, newStatus) {
      if (useBackend) {
        const r = await fetch(`${API_BASE}/${id}/status`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ status: newStatus })
        });
        if (!r.ok) throw new Error(await _errMsg(r));
        return r.json();
      }
      if (typeof DB !== 'undefined') {
        const row = DB.get('tasks', id);
        if (row) {
          if (row.status === 'DONE' && newStatus === 'IN_PROGRESS') {
            throw new Error('Completed tasks must be reopened to To Do first');
          }
          row.status = newStatus;
          DB.put('tasks', row);
          return row;
        }
      }
      return { status: newStatus };
    },

    async delete(id) {
      if (useBackend) {
        const r = await fetch(`${API_BASE}/${id}`, {
          method: 'DELETE',
          credentials: 'include'
        });
        if (!r.ok) throw new Error(await _errMsg(r));
        return true;
      }
      if (typeof DB !== 'undefined') DB.remove('tasks', id);
      return true;
    }
  };

  /* ──────────────────────────────────────────────
     TasksUI  – pure DOM helpers
  ────────────────────────────────────────────── */
  const TasksUI = {
    checkIsOverdue(task) {
      const status = task.status;
      if (status === 'DONE') return false;
      if (task.overdue !== undefined && typeof task.overdue === 'boolean') {
        return task.overdue;
      }
      const dueStr = task.dueDate || task.due_date;
      if (!dueStr) return false;
      const dueTime = new Date(dueStr.includes('T') ? dueStr : dueStr + 'T23:59:59').getTime();
      return !isNaN(dueTime) && Date.now() > dueTime;
    },

    createCard(task) {
      const isOverdue = TasksUI.checkIsOverdue(task);
      const id = task.task_id || task.id;
      const priority = (task.priority || 'MEDIUM').toUpperCase();
      const priorityClass = `priority-${priority.toLowerCase()}`;
      const due = task.dueDate || task.due_date;

      let moveBtns = '';
      if (task.status === 'TODO') {
        moveBtns = `
          <button class="btn-card-action" data-action="status" data-id="${id}" data-target="IN_PROGRESS" title="Move to In Progress">Start &rarr;</button>
          <button class="btn-card-action" data-action="status" data-id="${id}" data-target="DONE" title="Mark Done">&#10003;</button>
        `;
      } else if (task.status === 'IN_PROGRESS') {
        moveBtns = `
          <button class="btn-card-action" data-action="status" data-id="${id}" data-target="TODO" title="Move to To Do">&larr; To Do</button>
          <button class="btn-card-action" data-action="status" data-id="${id}" data-target="DONE" title="Mark Done">&#10003;</button>
        `;
      } else if (task.status === 'DONE') {
        moveBtns = `
          <button class="btn-card-action" data-action="status" data-id="${id}" data-target="TODO" title="Reopen to To Do">&#8634; Reopen</button>
        `;
      }

      const card = document.createElement('div');
      card.className = `task-card ${isOverdue ? 'is-overdue' : ''}`;
      card.setAttribute('role', 'listitem');
      card.id = `task-card-${id}`;

      card.innerHTML = `
        <div class="card-header">
          <h3 class="card-title">${esc(task.title)}</h3>
          <div class="card-badges">
            <span class="priority-tag ${priorityClass}">${esc(priority)}</span>
            ${isOverdue ? '<span class="overdue-tag">&#9888; Overdue</span>' : ''}
          </div>
        </div>
        <div class="card-meta">
          <span class="card-due" title="Due date">
            <span>&#128197;</span> ${fmt(due)}
          </span>
          <div class="card-actions">
            ${moveBtns}
            <button class="btn-card-action" data-action="edit" data-id="${id}" title="Edit task">&#9998;</button>
            <button class="btn-card-action btn-card-delete" data-action="delete" data-id="${id}" title="Delete task">&#128465;</button>
          </div>
        </div>
      `;
      return card;
    }
  };

  /* ──────────────────────────────────────────────
     TasksPage  – state management and coordination
  ────────────────────────────────────────────── */
  const TasksPage = {
    tasks: [],
    pendingDeleteId: null,

    async init() {
      await detectBackend();
      this.bindEvents();
      await this.load();
    },

    bindEvents() {
      // Toggle form
      $('#btnNewTask')?.addEventListener('click', () => this.showForm());
      $('#btnCancelTask')?.addEventListener('click', () => this.hideForm());
      $('#btnCancelForm')?.addEventListener('click', () => this.hideForm());

      // Form submission
      $('#taskForm')?.addEventListener('submit', e => this.handleSubmit(e));

      // Sorting
      $('#taskSortSelect')?.addEventListener('change', () => this.load());

      // Retry button on error banner
      $('#btnRetry')?.addEventListener('click', () => this.load());

      // Delete dialog
      $('#cancelDelete')?.addEventListener('click', () => $('#deleteDialog')?.close());
      $('#confirmDelete')?.addEventListener('click', () => this.confirmDelete());

      // Delegate card clicks
      $('#kanbanBoard')?.addEventListener('click', e => {
        const btn = e.target.closest('[data-action]');
        if (!btn) return;
        const action = btn.dataset.action;
        const id = parseInt(btn.dataset.id, 10);

        if (action === 'status') {
          const target = btn.dataset.target;
          this.changeStatus(id, target);
        } else if (action === 'edit') {
          this.editTask(id);
        } else if (action === 'delete') {
          this.promptDelete(id);
        }
      });
    },

    async load() {
      this.setLoading(true);
      this.hideError();

      const sortBy = $('#taskSortSelect')?.value || 'due_date';
      try {
        this.tasks = await TasksAPI.list('', sortBy);
        this.render();
      } catch (err) {
        this.showError(`Failed to load tasks: ${err.message}`);
      } finally {
        this.setLoading(false);
      }
    },

    render() {
      const cTodo = $('#c-TODO');
      const cInProg = $('#c-IN_PROGRESS');
      const cDone = $('#c-DONE');

      if (!cTodo || !cInProg || !cDone) return;

      cTodo.innerHTML = '';
      cInProg.innerHTML = '';
      cDone.innerHTML = '';

      let todoCount = 0;
      let inProgCount = 0;
      let doneCount = 0;
      let overdueCount = 0;

      this.tasks.forEach(task => {
        const isOverdue = TasksUI.checkIsOverdue(task);
        if (isOverdue) overdueCount++;

        const card = TasksUI.createCard(task);
        if (task.status === 'DONE') {
          doneCount++;
          cDone.appendChild(card);
        } else if (task.status === 'IN_PROGRESS') {
          inProgCount++;
          cInProg.appendChild(card);
        } else {
          todoCount++;
          cTodo.appendChild(card);
        }
      });

      if (todoCount === 0) cTodo.innerHTML = '<div class="empty-col">No tasks in To Do</div>';
      if (inProgCount === 0) cInProg.innerHTML = '<div class="empty-col">No tasks in progress</div>';
      if (doneCount === 0) cDone.innerHTML = '<div class="empty-col">No completed tasks</div>';

      // Update column counter badges
      if ($('#n-TODO')) $('#n-TODO').textContent = todoCount;
      if ($('#n-IN_PROGRESS')) $('#n-IN_PROGRESS').textContent = inProgCount;
      if ($('#n-DONE')) $('#n-DONE').textContent = doneCount;

      // Update metrics bar (Member 6 dashboard sync)
      if ($('#metricTodoCount')) $('#metricTodoCount').textContent = todoCount;
      if ($('#metricInProgressCount')) $('#metricInProgressCount').textContent = inProgCount;
      if ($('#metricDoneCount')) $('#metricDoneCount').textContent = doneCount;
      if ($('#metricOverdueCount')) $('#metricOverdueCount').textContent = overdueCount;

      if ($('#taskResultsCount')) {
        $('#taskResultsCount').textContent = `${this.tasks.length} task${this.tasks.length === 1 ? '' : 's'} total`;
      }
    },

    showForm(task = null) {
      const formCard = $('#taskFormCard');
      const titleEl = $('#taskFormTitle');
      const idInput = $('#taskIdInput');
      const titleInput = $('#taskTitleInput');
      const dueInput = $('#taskDueDateInput');
      const prioSelect = $('#taskPrioritySelect');

      if (task) {
        titleEl.textContent = 'Edit task';
        idInput.value = task.task_id || task.id;
        titleInput.value = task.title || '';
        dueInput.value = task.dueDate || task.due_date || '';
        prioSelect.value = task.priority || 'MEDIUM';
      } else {
        titleEl.textContent = 'New task';
        idInput.value = '';
        titleInput.value = '';
        // Default tomorrow
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        dueInput.value = tomorrow.toISOString().split('T')[0];
        prioSelect.value = 'MEDIUM';
      }

      formCard.hidden = false;
      titleInput.focus();
    },

    hideForm() {
      const formCard = $('#taskFormCard');
      if (formCard) formCard.hidden = true;
    },

    async handleSubmit(e) {
      e.preventDefault();
      const id = $('#taskIdInput').value;
      const title = $('#taskTitleInput').value.trim();
      const dueDate = $('#taskDueDateInput').value;
      const priority = $('#taskPrioritySelect').value;

      if (title.length < 3 || title.length > 100) {
        this.notify('Title must be between 3 and 100 characters', 'error');
        return;
      }
      if (!dueDate) {
        this.notify('Due date is required', 'error');
        return;
      }

      try {
        if (id) {
          await TasksAPI.update(parseInt(id, 10), { title, dueDate, due_date: dueDate, priority });
          this.notify('Task updated successfully');
        } else {
          await TasksAPI.create({ title, dueDate, due_date: dueDate, priority });
          this.notify('Task created successfully');
        }
        this.hideForm();
        await this.load();
      } catch (err) {
        this.notify(err.message, 'error');
      }
    },

    async changeStatus(id, newStatus) {
      try {
        await TasksAPI.updateStatus(id, newStatus);
        this.notify(`Status changed to ${newStatus}`);
        await this.load();
      } catch (err) {
        this.notify(err.message, 'error');
      }
    },

    editTask(id) {
      const task = this.tasks.find(t => (t.task_id || t.id) === id);
      if (task) {
        this.showForm(task);
      }
    },

    promptDelete(id) {
      this.pendingDeleteId = id;
      $('#deleteDialog')?.showModal();
    },

    async confirmDelete() {
      if (!this.pendingDeleteId) return;
      try {
        await TasksAPI.delete(this.pendingDeleteId);
        this.notify('Task deleted');
        $('#deleteDialog')?.close();
        this.pendingDeleteId = null;
        await this.load();
      } catch (err) {
        this.notify(err.message, 'error');
      }
    },

    setLoading(loading) {
      const el = $('#loadingState');
      if (el) el.hidden = !loading;
    },

    showError(msg) {
      const banner = $('#apiBanner');
      const msgEl = $('#apiBannerMsg');
      if (banner && msgEl) {
        msgEl.textContent = msg;
        banner.hidden = false;
      }
    },

    hideError() {
      const banner = $('#apiBanner');
      if (banner) banner.hidden = true;
    },

    notify(msg, type = 'success') {
      if (typeof toast === 'function') {
        toast(msg);
      } else {
        const el = $('#toast');
        if (el) {
          el.textContent = msg;
          el.className = `toast ${type}`;
          el.hidden = false;
          setTimeout(() => { el.hidden = true; }, 3500);
        }
      }
    }
  };

  document.addEventListener('DOMContentLoaded', () => TasksPage.init());
})();
