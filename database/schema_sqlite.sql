-- FlowSpace database (SQLite 3). Run: sqlite3 flowspace.db < schema_sqlite.sql
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
  user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
  name          TEXT NOT NULL CHECK (length(name) BETWEEN 2 AND 60),
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  created_at    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notes (
  note_id    INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id    INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  title      TEXT NOT NULL CHECK (length(title) <= 100),
  content    TEXT NOT NULL,
  category   TEXT NOT NULL DEFAULT 'General',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notes_user_cat ON notes(user_id, category);
CREATE TRIGGER IF NOT EXISTS trg_notes_updated AFTER UPDATE ON notes
BEGIN UPDATE notes SET updated_at = CURRENT_TIMESTAMP WHERE note_id = NEW.note_id; END;

CREATE TABLE IF NOT EXISTS tasks (
  task_id    INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id    INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  title      TEXT NOT NULL CHECK (length(title) <= 100),
  due_date   TEXT NOT NULL,
  priority   TEXT NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW','MEDIUM','HIGH')),
  status     TEXT NOT NULL DEFAULT 'TODO'   CHECK (status IN ('TODO','IN_PROGRESS','DONE')),
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tasks_user_status ON tasks(user_id, status);

CREATE TABLE IF NOT EXISTS period_logs (
  log_id              INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id             INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  cycle_start_date    TEXT NOT NULL,
  cycle_length        INTEGER NOT NULL DEFAULT 28 CHECK (cycle_length BETWEEN 15 AND 60),
  predicted_next_date TEXT,
  UNIQUE (user_id, cycle_start_date)
);

CREATE TABLE IF NOT EXISTS mood_entries (
  entry_id     INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id      INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  entry_date   TEXT NOT NULL,
  mood         TEXT NOT NULL CHECK (mood IN ('HAPPY','CALM','NEUTRAL','TIRED','ANXIOUS','IRRITABLE','SAD')),
  symptoms     TEXT,
  pain_level   INTEGER NOT NULL DEFAULT 0 CHECK (pain_level BETWEEN 0 AND 10),
  sleep_hours  REAL    NOT NULL DEFAULT 0 CHECK (sleep_hours BETWEEN 0 AND 24),
  water_intake INTEGER NOT NULL DEFAULT 0 CHECK (water_intake BETWEEN 0 AND 30),
  UNIQUE (user_id, entry_date)
);
