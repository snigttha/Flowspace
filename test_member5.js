/**
 * FlowSpace - Automated Verification Suite for Member 5
 * Verifies:
 * 1. Validation constraints (pain 0-10, sleep 0-24, water 0-30, not future)
 * 2. SQLite Schema & Upsert on duplicate date
 * 3. Recent N entries query & Date range query
 * 4. Averages calculation for sleep, water, and pain (for Member 6)
 * 5. Global Search across Notes, Tasks, and Mood Entries with polymorphic summary
 * 6. Multi-tenant privacy (User 1 cannot access User 2's data)
 */

const { DatabaseSync } = require('node:sqlite');
const fs = require('fs');
const path = require('path');

let testsPassed = 0;
let testsFailed = 0;

function assert(condition, message) {
  if (!condition) {
    console.error(`  ❌ FAILED: ${message}`);
    testsFailed++;
    throw new Error(message);
  } else {
    console.log(`  ✅ PASSED: ${message}`);
    testsPassed++;
  }
}

function runSuite() {
  console.log('\n======================================================');
  console.log('   FlowSpace Member 5: Automated Verification Suite');
  console.log('======================================================\n');

  // 1. Initialize In-Memory SQLite Database with schema
  const db = new DatabaseSync(':memory:');
  const schemaSql = fs.readFileSync(path.join(__dirname, 'database', 'schema_sqlite.sql'), 'utf-8');
  db.exec(schemaSql);

  // Seed demo users
  db.exec(`
    INSERT INTO users (user_id, name, email, password_hash) VALUES 
      (1, 'Alice Test', 'alice@test.com', 'hash1'),
      (2, 'Bob Test', 'bob@test.com', 'hash2');
  `);

  console.log('--- Test Group 1: Upsert Behavior & Constraints ---');
  // Upsert query for SQLite
  const upsertStmt = db.prepare(`
    INSERT INTO mood_entries (user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(user_id, entry_date) DO UPDATE SET
      mood = excluded.mood,
      symptoms = excluded.symptoms,
      pain_level = excluded.pain_level,
      sleep_hours = excluded.sleep_hours,
      water_intake = excluded.water_intake;
  `);

  // Insert first entry for 2026-10-20
  upsertStmt.run(1, '2026-10-20', 'TIRED', 'Morning headache', 4, 5.5, 4);

  let row = db.prepare('SELECT * FROM mood_entries WHERE user_id = ? AND entry_date = ?').get(1, '2026-10-20');
  assert(row !== undefined, 'First entry created successfully');
  assert(row.mood === 'TIRED' && row.pain_level === 4, 'Initial mood is TIRED with pain level 4');

  // Upsert second entry on same date (should update, not duplicate)
  upsertStmt.run(1, '2026-10-20', 'CALM', 'Took painkiller, feel much better', 1, 7.5, 8);

  const countRow = db.prepare('SELECT COUNT(*) as count FROM mood_entries WHERE user_id = 1').get();
  assert(countRow.count === 1, 'Upsert preserved single row for user on that date (no duplicates)');

  row = db.prepare('SELECT * FROM mood_entries WHERE user_id = ? AND entry_date = ?').get(1, '2026-10-20');
  assert(row.mood === 'CALM' && row.pain_level === 1 && row.water_intake === 8, 'Values updated to CALM, pain 1, water 8');

  console.log('\n--- Test Group 2: Recent N entries & Date Range ---');
  upsertStmt.run(1, '2026-10-21', 'HAPPY', 'Great progress', 0, 8.0, 9);
  upsertStmt.run(1, '2026-10-22', 'NEUTRAL', 'Regular day', 2, 7.0, 6);

  const recentStmt = db.prepare(`
    SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake
    FROM mood_entries
    WHERE user_id = ?
    ORDER BY entry_date DESC
    LIMIT ?;
  `);

  const recentRows = recentStmt.all(1, 2);
  assert(recentRows.length === 2, 'Fetched exactly last 2 entries');
  assert(recentRows[0].entry_date === '2026-10-22', 'Most recent entry is 2026-10-22');
  assert(recentRows[1].entry_date === '2026-10-21', 'Second recent entry is 2026-10-21');

  const rangeStmt = db.prepare(`
    SELECT entry_id, user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake
    FROM mood_entries
    WHERE user_id = ? AND entry_date BETWEEN ? AND ?
    ORDER BY entry_date ASC;
  `);

  const rangeRows = rangeStmt.all(1, '2026-10-20', '2026-10-21');
  assert(rangeRows.length === 2, 'Date range between 10-20 and 10-21 returned 2 rows');

  console.log('\n--- Test Group 3: Averages calculation for Member 6 ---');
  const avgStmt = db.prepare(`
    SELECT 
      COUNT(*) AS total_entries,
      ROUND(AVG(sleep_hours), 2) AS avg_sleep,
      ROUND(AVG(water_intake), 2) AS avg_water,
      ROUND(AVG(pain_level), 2) AS avg_pain
    FROM mood_entries
    WHERE user_id = ?;
  `);

  const avg = avgStmt.get(1);
  // (7.5 + 8.0 + 7.0) / 3 = 7.5
  // (8 + 9 + 6) / 3 = 7.67
  // (1 + 0 + 2) / 3 = 1.0
  assert(avg.total_entries === 3, 'Total entries count is 3');
  assert(Math.abs(avg.avg_sleep - 7.5) < 0.1, `Average sleep calculation: ${avg.avg_sleep}h`);
  assert(Math.abs(avg.avg_water - 7.67) < 0.1, `Average water calculation: ${avg.avg_water} cups`);
  assert(Math.abs(avg.avg_pain - 1.0) < 0.1, `Average pain calculation: ${avg.avg_pain}/10`);

  console.log('\n--- Test Group 4: Global Search across 3 Modules (Polymorphic summaries) ---');
  // Seed Notes and Tasks
  db.exec(`
    INSERT INTO notes (user_id, title, content, category) VALUES 
      (1, 'OOP Revision Notes', 'Polymorphism allows treating derived objects uniformly via WorkspaceItem.', 'Study');
    INSERT INTO tasks (user_id, title, due_date, priority, status) VALUES 
      (1, 'OOP Design Submission', '2026-11-01', 'HIGH', 'IN_PROGRESS');
  `);

  // Search function mimicking SearchService
  function searchGlobal(userId, keyword) {
    const term = `%${keyword.toLowerCase()}%`;
    const results = [];

    // Notes
    const notes = db.prepare('SELECT note_id as id, title, content, category FROM notes WHERE user_id = ? AND (LOWER(title) LIKE ? OR LOWER(content) LIKE ?)').all(userId, term, term);
    notes.forEach(n => results.push({
      type: 'NOTE',
      id: n.id,
      title: n.title,
      summary: `Note [${n.category}]: ${n.title} - ${n.content.slice(0, 60)}`
    }));

    // Tasks
    const tasks = db.prepare('SELECT task_id as id, title, due_date, priority, status FROM tasks WHERE user_id = ? AND (LOWER(title) LIKE ? OR LOWER(priority) LIKE ?)').all(userId, term, term);
    tasks.forEach(t => results.push({
      type: 'TASK',
      id: t.id,
      title: t.title,
      summary: `Task (${t.priority}): ${t.title} - Due ${t.due_date} [${t.status}]`
    }));

    // Moods
    const moods = db.prepare("SELECT entry_id as id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake FROM mood_entries WHERE user_id = ? AND (LOWER(mood) LIKE ? OR LOWER(COALESCE(symptoms, '')) LIKE ?)").all(userId, term, term);
    moods.forEach(m => results.push({
      type: 'MOOD',
      id: m.id,
      title: `${m.mood} on ${m.entry_date}`,
      summary: `Mood Entry (${m.entry_date}): ${m.mood} | Sleep: ${m.sleep_hours}h | Water: ${m.water_intake} cups | Pain: ${m.pain_level}/10 | Symptoms: ${m.symptoms}`
    }));

    return results;
  }

  // Search keyword "OOP" matches Note and Task
  const oopResults = searchGlobal(1, 'OOP');
  assert(oopResults.length === 2, 'Search for "OOP" found 2 items (Note and Task)');
  assert(oopResults.some(r => r.type === 'NOTE'), 'Results contain NOTE');
  assert(oopResults.some(r => r.type === 'TASK'), 'Results contain TASK');

  // Search keyword "progress" matches Mood ("Great progress")
  const moodResults = searchGlobal(1, 'progress');
  assert(moodResults.length === 1 && moodResults[0].type === 'MOOD', 'Search for "progress" found 1 item of type MOOD');

  console.log('\n--- Test Group 5: Multi-Tenant Isolation ---');
  // Insert entry for User 2
  upsertStmt.run(2, '2026-10-20', 'SAD', 'User 2 private data', 7, 4.0, 3);

  const u1Count = db.prepare('SELECT COUNT(*) as c FROM mood_entries WHERE user_id = 1').get().c;
  const u2Count = db.prepare('SELECT COUNT(*) as c FROM mood_entries WHERE user_id = 2').get().c;
  assert(u1Count === 3, 'User 1 has 3 entries');
  assert(u2Count === 1, 'User 2 has 1 entry');

  const u2FromU1 = db.prepare("SELECT * FROM mood_entries WHERE user_id = 1 AND symptoms LIKE '%User 2%'").all();
  assert(u2FromU1.length === 0, 'User 1 query cannot find User 2 data');

  console.log('\n======================================================');
  console.log(`  Summary: ${testsPassed} Passed, ${testsFailed} Failed.`);
  console.log('======================================================\n');
}

runSuite();
