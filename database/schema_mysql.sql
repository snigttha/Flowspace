-- FlowSpace database (MySQL 8+). Run: mysql -u root -p < schema_mysql.sql
CREATE DATABASE IF NOT EXISTS flowspace CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE flowspace;

CREATE TABLE IF NOT EXISTS users (
  user_id       INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(60)  NOT NULL,
  email         VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,            -- BCrypt/PBKDF2 hash, never plain text
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS notes (
  note_id    INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id    INT UNSIGNED NOT NULL,
  title      VARCHAR(100) NOT NULL,
  content    TEXT         NOT NULL,
  category   VARCHAR(30)  NOT NULL DEFAULT 'General',
  created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_notes_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  INDEX idx_notes_user_cat (user_id, category)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tasks (
  task_id  INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id  INT UNSIGNED NOT NULL,
  title    VARCHAR(100) NOT NULL,
  due_date DATE         NOT NULL,
  priority ENUM('LOW','MEDIUM','HIGH')           NOT NULL DEFAULT 'MEDIUM',
  status   ENUM('TODO','IN_PROGRESS','DONE')     NOT NULL DEFAULT 'TODO',
  created_at TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  INDEX idx_tasks_user_status (user_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS period_logs (
  log_id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id             INT UNSIGNED NOT NULL,
  cycle_start_date    DATE         NOT NULL,
  cycle_length        TINYINT UNSIGNED NOT NULL DEFAULT 28,
  predicted_next_date DATE         NULL,
  CONSTRAINT fk_period_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  CONSTRAINT chk_cycle_length CHECK (cycle_length BETWEEN 15 AND 60),
  UNIQUE KEY uq_period_user_start (user_id, cycle_start_date)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS mood_entries (
  entry_id     INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id      INT UNSIGNED NOT NULL,
  entry_date   DATE         NOT NULL,
  mood         ENUM('HAPPY','CALM','NEUTRAL','TIRED','ANXIOUS','IRRITABLE','SAD') NOT NULL,
  symptoms     VARCHAR(200) NULL,
  pain_level   TINYINT UNSIGNED NOT NULL DEFAULT 0,
  sleep_hours  DECIMAL(3,1) NOT NULL DEFAULT 0,
  water_intake TINYINT UNSIGNED NOT NULL DEFAULT 0,   -- glasses
  CONSTRAINT fk_mood_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  CONSTRAINT chk_pain  CHECK (pain_level BETWEEN 0 AND 10),
  CONSTRAINT chk_sleep CHECK (sleep_hours BETWEEN 0 AND 24),
  CONSTRAINT chk_water CHECK (water_intake BETWEEN 0 AND 30),
  UNIQUE KEY uq_mood_user_date (user_id, entry_date)
) ENGINE=InnoDB;
