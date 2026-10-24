-- Optional demo rows (work on MySQL and SQLite). The hash below is a placeholder, not a real login.
INSERT INTO users (name, email, password_hash) VALUES ('Demo User', 'demo@flowspace.test', 'PLACEHOLDER_HASH');
INSERT INTO notes (user_id, title, content, category) VALUES (1, 'OOP revision', 'Encapsulation, inheritance, polymorphism, abstraction.', 'Study');
INSERT INTO tasks (user_id, title, due_date, priority, status) VALUES (1, 'Finish ER diagram', '2026-11-01', 'HIGH', 'IN_PROGRESS');
INSERT INTO period_logs (user_id, cycle_start_date, cycle_length) VALUES (1, '2026-09-20', 28);
INSERT INTO mood_entries (user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake) VALUES (1, '2026-10-01', 'CALM', 'none', 1, 7.5, 8);
