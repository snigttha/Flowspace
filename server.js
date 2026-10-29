/**
 * FlowSpace - Lightweight Full-Stack Development Server
 * File: server.js
 * 
 * Provides:
 * 1. Static file hosting for FlowSpace UI (index.html, wellness.html, etc.)
 * 2. Real REST API endpoints as defined in PLAN.md:
 *    - GET /api/moods: fetch user entries or averages
 *    - POST /api/moods: upsert mood entry (one entry per date, second one updates)
 *    - DELETE /api/moods: remove entry by id/date
 *    - GET /api/search?q=...: global search across notes, tasks, moods with polymorphic summaries
 * 3. Backed by native SQLite (flowspace.db) with schema & demo seeds
 * 
 * Run with: node server.js
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { DatabaseSync } = require('node:sqlite');

const PORT = process.env.PORT || 3000;
const DB_FILE = path.join(__dirname, 'flowspace.db');

// Initialize database
const db = new DatabaseSync(DB_FILE);
const schemaSql = fs.readFileSync(path.join(__dirname, 'database', 'schema_sqlite.sql'), 'utf-8');
db.exec(schemaSql);

// Seed initial user if none exists
const userCheck = db.prepare('SELECT COUNT(*) as c FROM users').get();
if (userCheck.c === 0) {
  const seedSql = fs.readFileSync(path.join(__dirname, 'database', 'seed_demo.sql'), 'utf-8');
  db.exec(seedSql);
  console.log('Seeded demo user, notes, tasks, and mood entry.');
}

// MIME types
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json',
  '.sql': 'text/plain',
  '.md': 'text/markdown',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml'
};

const server = http.createServer((req, res) => {
  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = parsedUrl.pathname;

  // Set CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-User-Id');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  // --- API Endpoints ---
  const userId = parseInt(req.headers['x-user-id'] || '1', 10);

  // 1. GET /api/moods
  if (pathname === '/api/moods' && req.method === 'GET') {
    const isAverages = parsedUrl.searchParams.get('averages') === 'true';
    const limit = parseInt(parsedUrl.searchParams.get('limit') || '15', 10);
    const start = parsedUrl.searchParams.get('start');
    const end = parsedUrl.searchParams.get('end');

    if (isAverages) {
      const avg = db.prepare(`
        SELECT COUNT(*) AS total_entries,
               ROUND(AVG(sleep_hours), 2) AS avg_sleep,
               ROUND(AVG(water_intake), 2) AS avg_water,
               ROUND(AVG(pain_level), 2) AS avg_pain
        FROM mood_entries WHERE user_id = ?
      `).get(userId);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(avg || { total_entries: 0, avg_sleep: 0, avg_water: 0, avg_pain: 0 }));
      return;
    }

    if (start && end) {
      const rows = db.prepare(`
        SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake
        FROM mood_entries WHERE user_id = ? AND entry_date BETWEEN ? AND ?
        ORDER BY entry_date ASC
      `).all(userId, start, end);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(rows));
      return;
    }

    const rows = db.prepare(`
      SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake
      FROM mood_entries WHERE user_id = ?
      ORDER BY entry_date DESC LIMIT ?
    `).all(userId, limit);

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(rows));
    return;
  }

  // 2. POST /api/moods (Upsert)
  if (pathname === '/api/moods' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try {
        const data = JSON.parse(body);
        const date = data.date || data.entry_date;
        const mood = (data.mood || '').toUpperCase();
        const symptoms = (data.symptoms || '').trim();
        const pain = parseInt(data.pain ?? data.pain_level ?? 0, 10);
        const sleep = parseFloat(data.sleep ?? data.sleep_hours ?? 0);
        const water = parseInt(data.water ?? data.water_intake ?? 0, 10);

        // Validation
        if (!date || isNaN(Date.parse(date))) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Valid date is required' }));
          return;
        }
        if (date > new Date().toISOString().slice(0, 10)) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Date cannot be in the future' }));
          return;
        }
        if (!['HAPPY', 'CALM', 'NEUTRAL', 'TIRED', 'ANXIOUS', 'IRRITABLE', 'SAD'].includes(mood)) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Valid mood is required' }));
          return;
        }
        if (pain < 0 || pain > 10) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Pain level must be between 0 and 10' }));
          return;
        }
        if (sleep < 0 || sleep > 24) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Sleep hours must be between 0 and 24' }));
          return;
        }
        if (water < 0 || water > 30) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Water intake must be between 0 and 30' }));
          return;
        }

        // Upsert
        db.prepare(`
          INSERT INTO mood_entries (user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT(user_id, entry_date) DO UPDATE SET
            mood = excluded.mood,
            symptoms = excluded.symptoms,
            pain_level = excluded.pain_level,
            sleep_hours = excluded.sleep_hours,
            water_intake = excluded.water_intake;
        `).run(userId, date, mood, symptoms, pain, sleep, water);

        const saved = db.prepare('SELECT * FROM mood_entries WHERE user_id = ? AND entry_date = ?').get(userId, date);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(saved));
      } catch (err) {
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: err.message }));
      }
    });
    return;
  }

  // 3. DELETE /api/moods
  if (pathname === '/api/moods' && req.method === 'DELETE') {
    const id = parsedUrl.searchParams.get('id');
    const date = parsedUrl.searchParams.get('date');
    if (id) {
      db.prepare('DELETE FROM mood_entries WHERE entry_id = ? AND user_id = ?').run(parseInt(id, 10), userId);
    } else if (date) {
      db.prepare('DELETE FROM mood_entries WHERE entry_date = ? AND user_id = ?').run(date, userId);
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true }));
    return;
  }

  // 3a. GET /api/notes & POST /api/notes & DELETE /api/notes
  if (pathname === '/api/notes') {
    if (req.method === 'GET') {
      const rows = db.prepare('SELECT note_id as id, title, content, category, updated_at FROM notes WHERE user_id = ? ORDER BY updated_at DESC').all(userId);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(rows));
      return;
    }
    if (req.method === 'POST') {
      let body = '';
      req.on('data', chunk => { body += chunk; });
      req.on('end', () => {
        try {
          const data = JSON.parse(body);
          const title = (data.title || '').trim();
          const content = (data.content || '').trim();
          const category = (data.category || 'General').trim();
          if (!title) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'Title is required' }));
            return;
          }
          if (data.id) {
            db.prepare('UPDATE notes SET title = ?, content = ?, category = ?, updated_at = CURRENT_TIMESTAMP WHERE note_id = ? AND user_id = ?')
              .run(title, content, category, parseInt(data.id, 10), userId);
          } else {
            db.prepare('INSERT INTO notes (user_id, title, content, category) VALUES (?, ?, ?, ?)')
              .run(userId, title, content, category);
          }
          const latest = db.prepare('SELECT note_id as id, title, content, category, updated_at FROM notes WHERE user_id = ? ORDER BY note_id DESC LIMIT 1').get(userId);
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify(latest));
        } catch (e) {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: e.message }));
        }
      });
      return;
    }
    if (req.method === 'DELETE') {
      const id = parsedUrl.searchParams.get('id');
      if (id) {
        db.prepare('DELETE FROM notes WHERE note_id = ? AND user_id = ?').run(parseInt(id, 10), userId);
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true }));
      return;
    }
  }

  // 3b. GET /api/tasks & POST /api/tasks & DELETE /api/tasks
  if (pathname === '/api/tasks') {
    if (req.method === 'GET') {
      const rows = db.prepare('SELECT task_id as id, title, due_date, priority, status FROM tasks WHERE user_id = ? ORDER BY due_date ASC').all(userId);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(rows));
      return;
    }
    if (req.method === 'POST') {
      let body = '';
      req.on('data', chunk => { body += chunk; });
      req.on('end', () => {
        try {
          const data = JSON.parse(body);
          const title = (data.title || '').trim();
          const due = data.due || data.due_date;
          const priority = data.priority || 'MEDIUM';
          const status = data.status || 'TODO';
          if (data.id) {
            db.prepare('UPDATE tasks SET title = ?, due_date = ?, priority = ?, status = ? WHERE task_id = ? AND user_id = ?')
              .run(title, due, priority, status, parseInt(data.id, 10), userId);
          } else {
            db.prepare('INSERT INTO tasks (user_id, title, due_date, priority, status) VALUES (?, ?, ?, ?, ?)')
              .run(userId, title, due, priority, status);
          }
          const latest = db.prepare('SELECT task_id as id, title, due_date, priority, status FROM tasks WHERE user_id = ? ORDER BY task_id DESC LIMIT 1').get(userId);
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify(latest));
        } catch (e) {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: e.message }));
        }
      });
      return;
    }
    if (req.method === 'DELETE') {
      const id = parsedUrl.searchParams.get('id');
      if (id) {
        db.prepare('DELETE FROM tasks WHERE task_id = ? AND user_id = ?').run(parseInt(id, 10), userId);
      }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true }));
      return;
    }
  }

  // 4. GET /api/search?q=... (Global Search across Notes, Tasks, and Moods)
  if (pathname === '/api/search' && req.method === 'GET') {
    const q = (parsedUrl.searchParams.get('q') || '').trim();
    if (!q) {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end('[]');
      return;
    }

    const term = `%${q.toLowerCase()}%`;
    const results = [];

    // Search Notes
    try {
      const notes = db.prepare(`
        SELECT note_id as id, title, content, category, updated_at
        FROM notes WHERE user_id = ? AND (LOWER(title) LIKE ? OR LOWER(content) LIKE ? OR LOWER(category) LIKE ?)
        ORDER BY updated_at DESC LIMIT 10
      `).all(userId, term, term, term);

      notes.forEach(n => {
        results.push({
          type: 'NOTE',
          id: n.id,
          title: n.title,
          summary: `Note [${n.category}]: ${n.title} - ${n.content.slice(0, 60)}...`,
          date: n.updated_at ? n.updated_at.slice(0, 10) : '',
          url: 'notes.html'
        });
      });
    } catch (e) {}

    // Search Tasks
    try {
      const tasks = db.prepare(`
        SELECT task_id as id, title, due_date, priority, status
        FROM tasks WHERE user_id = ? AND (LOWER(title) LIKE ? OR LOWER(priority) LIKE ? OR LOWER(status) LIKE ?)
        ORDER BY due_date ASC LIMIT 10
      `).all(userId, term, term, term);

      tasks.forEach(t => {
        results.push({
          type: 'TASK',
          id: t.id,
          title: t.title,
          summary: `Task (${t.priority}): ${t.title} - Due ${t.due_date} [${t.status}]`,
          date: t.due_date,
          url: 'tasks.html'
        });
      });
    } catch (e) {}

    // Search Mood Entries
    try {
      const moods = db.prepare(`
        SELECT entry_id as id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake
        FROM mood_entries WHERE user_id = ? AND (LOWER(mood) LIKE ? OR LOWER(COALESCE(symptoms, '')) LIKE ? OR entry_date LIKE ?)
        ORDER BY entry_date DESC LIMIT 10
      `).all(userId, term, term, term);

      moods.forEach(m => {
        const sym = m.symptoms ? ` | Symptoms: ${m.symptoms}` : '';
        results.push({
          type: 'MOOD',
          id: m.id,
          title: `${m.mood} on ${m.entry_date}`,
          summary: `Mood Entry (${m.entry_date}): ${m.mood} | Sleep: ${m.sleep_hours}h | Water: ${m.water_intake} cups | Pain: ${m.pain_level}/10${sym}`,
          date: m.entry_date,
          url: 'wellness.html'
        });
      });
    } catch (e) {}

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(results));
    return;
  }

  // --- Static File Serving ---
  let safePath = path.normalize(pathname).replace(/^(\.\.[\/\\])+/, '');
  if (safePath === '/' || safePath === '\\') safePath = 'index.html';

  const fullPath = path.join(__dirname, safePath);

  fs.stat(fullPath, (err, stats) => {
    if (err || !stats.isFile()) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('404 Not Found: ' + pathname);
      return;
    }

    const ext = path.extname(fullPath).toLowerCase();
    const contentType = MIME[ext] || 'application/octet-stream';

    res.writeHead(200, { 'Content-Type': contentType });
    fs.createReadStream(fullPath).pipe(res);
  });
});

server.listen(PORT, () => {
  console.log(`FlowSpace server running at http://localhost:${PORT}`);
  console.log(`Member 5 Endpoints ready:`);
  console.log(`  - GET/POST/DELETE /api/moods`);
  console.log(`  - GET /api/search?q=...`);
  console.log(`Open http://localhost:${PORT}/wellness.html to test!`);
});
