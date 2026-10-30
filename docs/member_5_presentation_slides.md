# FlowSpace — Member 5 Presentation: Mood Tracker & Global Search

---

## Slide 1: Title & Role
- **Project:** FlowSpace — Integrated Personal Workspace & Wellness Platform
- **Member 5 Owner:** Mood Tracker & Global Search
- **Deliverables:**
  - Frontend: Interactive Mood Tracker UI & Navigation Global Search
  - Styling: `css/wellness.css` with responsive mobile support (375px)
  - Java Backend: `MoodEntry`, `WorkspaceItem` inheritance, `Dao<T>`, `MoodEntryDAO`, `SearchService`
  - Database: `mood_entries` schema, SQLite & MySQL upsert queries, statistical aggregations
  - Tests: Validation, Upsert, Polymorphism, Multi-tenant privacy

---

## Slide 2: Problem & Motivation
- **The Challenge:**
  - Most productivity suites isolate task management, note-taking, and health tracking into disconnected silos.
  - Users need a fast, unified way to log how they feel each day and locate information across all modules instantly.
- **Member 5 Objectives:**
  1. Provide a frictionless daily mood check-in (mood, pain, sleep, water, symptoms) with intelligent upsert (one log per day).
  2. Implement a high-performance **Global Search** spanning notes, tasks, and wellness entries with rich polymorphic previews.

---

## Slide 3: OOP Principles in Action
Our architecture explicitly fulfills core Object-Oriented principles:

1. **Abstraction:**
   - `WorkspaceItem`: Abstract base class defining `abstract void validate()` and `abstract String summary()`.
   - `Dao<T>`: Generic interface abstracting persistence operations (`create`, `update`, `delete`, `findById`, `findAll`).
2. **Inheritance:**
   - `MoodEntry extends WorkspaceItem`
   - `Note extends WorkspaceItem`
   - `Task extends WorkspaceItem`
3. **Polymorphism:**
   - In `SearchService`, results from all modules are aggregated into `List<WorkspaceItem>`.
   - Calling `item.summary()` dynamically executes the concrete class's summary logic at runtime, producing formatted previews without type coupling.
4. **Encapsulation:**
   - All state fields (`painLevel`, `sleepHours`, `waterIntake`, `entryDate`, etc.) are private.
   - Setters actively enforce domain invariants (e.g. pain level must be 0–10, sleep 0–24, dates not in future).

---

## Slide 4: Database Design & Upsert Mechanics
- **Table:** `mood_entries`
- **Key Constraints:**
  - `UNIQUE(user_id, entry_date)`: Guarantees exactly one entry per user per day.
  - `CHECK (pain_level BETWEEN 0 AND 10)`
  - `CHECK (sleep_hours BETWEEN 0 AND 24)`
  - `CHECK (water_intake BETWEEN 0 AND 30)`
- **Upsert Query (SQLite):**
  ```sql
  INSERT INTO mood_entries (user_id, entry_date, mood, symptoms, pain_level, sleep_hours, water_intake)
  VALUES (?, ?, ?, ?, ?, ?, ?)
  ON CONFLICT(user_id, entry_date) DO UPDATE SET
    mood = excluded.mood,
    symptoms = excluded.symptoms,
    pain_level = excluded.pain_level,
    sleep_hours = excluded.sleep_hours,
    water_intake = excluded.water_intake;
  ```
- **Member 6 Aggregation Query:**
  ```sql
  SELECT COUNT(*) as total_entries, AVG(sleep_hours) as avg_sleep,
         AVG(water_intake) as avg_water, AVG(pain_level) as avg_pain
  FROM mood_entries WHERE user_id = ?;
  ```

---

## Slide 5: Frontend Experience & Styling
- **Shared Palette:** Plum (`#6b2d45`), Rose (`#c98b9b`), Sand (`#ece3d4`), Blush (`#f7ebed`).
- **Interactive Controls:**
  - **Mood Chips:** 7 visual mood states (Happy 😊, Calm 😌, Neutral 😐, Tired 😴, Anxious 😰, Irritable 😣, Sad 😢) synchronized with select dropdown.
  - **Dynamic Sliders:** Custom slider tracks with live badges that dynamically change tint (e.g. green for mild pain, amber for moderate, deep red for severe).
  - **Responsive Entries Table:** Clean, accessible table with action buttons ("Edit" to pre-fill form, "Delete" with confirmation). Works on 375px mobile screens.
  - **Global Search Navigation:** Keyboard-friendly (`/` shortcut), debounced search with type badges (`NOTE`, `TASK`, `MOOD`) and polymorphic summary strings.

---

## Slide 6: Validation & Security
- **Defense-in-Depth:**
  - **Client-Side:** HTML5 attributes + `validate.js` instant visual cues.
  - **Server-Side:** Java `MoodEntry.validate()` and database SQL `CHECK` constraints.
- **Tenant Isolation:**
  - Every single query scopes by `user_id = ?`.
  - User A cannot view, edit, search, or delete User B's entries.
- **SQL Injection Prevention:**
  - 100% of database interactions utilize parameterized `PreparedStatement`.

---

## Slide 7: Verification & Testing
- **Automated Verification Suite:**
  - `MoodEntryTest.java` (JUnit 5 unit tests for validation & bounds).
  - `MoodEntryDAOTest.java` (In-memory SQLite CRUD, upsert verification, averages calculation).
  - `SearchServiceTest.java` (Polymorphic search across Notes, Tasks, and Moods).
  - `test_member5.js` (Automated verification: 19 test cases passed with 0 failures).
- **Definition of Done Check:**
  - [x] Mood entries save and edit per day (upsert confirmed).
  - [x] Search returns correct results from all three modules.
  - [x] Mobile responsive at 375px phone screen.
  - [x] Full OOP compliance (Encapsulation, Inheritance, Polymorphism, Abstraction).

---

## Slide 8: Team Integration & Hand-offs
- **Member 4 (Cycle Tracker):**
  - Unified wellness layout in `wellness.html` cleanly pairing cycle calendar and mood tracker side-by-side.
- **Member 6 (Analytics & Dashboard):**
  - Provided `getAverages(userId)` delivering `avg_sleep`, `avg_water`, and `avg_pain` for dashboard insight generation.
- **Global Search:**
  - Navigation component available across all app pages.

---
