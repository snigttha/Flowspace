-- ============================================================================
-- FlowSpace - Member 5: Mood Tracker & Global Search Queries
-- Target Tables: mood_entries (SQLite & MySQL compatible)
-- ============================================================================

-- 1. UPSERT ENTRY (One entry per date; a second one on the same date updates)
-- SQLite syntax:
INSERT INTO mood_entries (user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(user_id, entry_date) DO UPDATE SET
  mood = excluded.mood,
  symptoms = excluded.symptoms,
  pain_level = excluded.pain_level,
  sleep_hours = excluded.sleep_hours,
  water_intake = excluded.water_intake;

-- MySQL syntax:
-- INSERT INTO mood_entries (user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake)
-- VALUES (?, ?, ?, ?, ?, ?, ?)
-- ON DUPLICATE KEY UPDATE
--   mood = VALUES(mood),
--   symptoms = VALUES(symptoms),
--   pain_level = VALUES(pain_level),
--   sleep_hours = VALUES(sleep_hours),
--   water_intake = VALUES(water_intake);


-- 2. FETCH LAST N ENTRIES (for user's recent log view)
SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake
FROM mood_entries
WHERE user_id = ?
ORDER BY entry_date DESC
LIMIT ?;


-- 3. FETCH ENTRIES BETWEEN TWO DATES (for trend analysis & calendar range)
SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake
FROM mood_entries
WHERE user_id = ? 
  AND entry_date BETWEEN ? AND ?
ORDER BY entry_date ASC;


-- 4. AGGREGATES & AVERAGES (Provided to Member 6 for Analytics / Insights)
-- Calculates count, average sleep hours, water intake, and pain level
SELECT 
  COUNT(*) AS total_entries,
  COALESCE(ROUND(AVG(sleep_hours), 2), 0.0) AS avg_sleep,
  COALESCE(ROUND(AVG(water_intake), 2), 0.0) AS avg_water,
  COALESCE(ROUND(AVG(pain_level), 2), 0.0) AS avg_pain
FROM mood_entries
WHERE user_id = ?;


-- 5. SINGLE DATE LOOKUP (check existing log for pre-fill / editing)
SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake
FROM mood_entries
WHERE user_id = ? AND entry_date = ?
LIMIT 1;


-- 6. KEYWORD SEARCH IN MOOD ENTRIES (Used in Global Search)
SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake
FROM mood_entries
WHERE user_id = ? 
  AND (LOWER(mood) LIKE ? OR LOWER(COALESCE(symptoms, '')) LIKE ?)
ORDER BY entry_date DESC
LIMIT 20;


-- 7. DELETE ENTRY BY ID (guarded by user_id)
DELETE FROM mood_entries
WHERE entry_id = ? AND user_id = ?;
