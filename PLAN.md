# FlowSpace: build and launch plan (team of 6)

## Run what is already built
- Frontend: open `index.html` in a browser (or run `python3 -m http.server` in this folder). Register, then use every screen. Data is kept in the browser's localStorage until the Java backend replaces it.
- Database: `sqlite3 flowspace.db < database/schema_sqlite.sql` (or `mysql -u root -p < database/schema_mysql.sql`). Optional demo rows: `database/seed_demo.sql`.
- Validation lives in `js/validate.js`. Add `data-v="required|email|min:8|range:0:10|notfuture|match:password"` to any input.
- `css/style.css` is only a placeholder. The team builds the final design (see "Styling rules" and each member's styling tasks).

## Ownership
Each member owns one vertical slice: page, styling, Java classes, DAO, API endpoints and tests.

| # | Owner of | Table | Name |
|---|----------|-------|------|
| 1 | Foundation, accounts, security, deployment | users | |
| 2 | Notes | notes | |
| 3 | Tasks | tasks | |
| 4 | Cycle tracker and prediction | period_logs | |
| 5 | Mood tracker and global search | mood_entries | |
| 6 | Workspace, analytics, dashboard, integration lead | all (read) | |

## Team rules
- One Git repo, one branch per person (`feature/notes`), pull request reviewed by a teammate, merge to `main` only when it runs.
- Agree the API contract below on day 1.
- Every query filters by the logged-in `user_id` and uses `PreparedStatement`.
- Never commit passwords, database files or secrets.
- Browser validation is only for convenience. Every rule is checked again in Java.

## API contract
- Accounts: `POST /api/register`, `POST /api/login`, `POST /api/logout`, `GET|PUT /api/me`
- Notes: `GET|POST /api/notes`, `PUT|DELETE /api/notes/{id}`
- Tasks: `GET|POST /api/tasks`, `PUT|DELETE /api/tasks/{id}`, `PATCH /api/tasks/{id}/status`
- Cycle: `GET|POST /api/periods`, `GET /api/periods/prediction`
- Mood: `GET|POST /api/moods` (POST on an existing date updates it)
- Search: `GET /api/search?q=...`
- Analytics: `GET /api/analytics`
All JSON; field names match the SQL columns.

## Shared class design
Your proposal promises encapsulation, inheritance, polymorphism and abstraction, but the class diagram shows no inheritance. Use:
- Abstract `WorkspaceItem` (id, userId, createdAt, abstract `validate()` and `summary()`), extended by `Note`, `Task`, `MoodEntry`, `PeriodLog`.
- Generic `Dao<T>` interface, implemented by every DAO.
- Global search loops over a `List<WorkspaceItem>` and calls `summary()` (polymorphism demo).
- Private fields with validated setters (encapsulation).

## Styling (CSS) rules for everyone
- Member 1 builds the shared base in `css/style.css` by day 3: colour variables, fonts, buttons, form fields, cards, tags, layout with sidebar. Everyone reuses it and does not redefine it.
- Each member styles their own pages in their own file (`css/auth.css`, `css/notes.css`, `css/tasks.css`, `css/wellness.css`, `css/dashboard.css`) to avoid merge conflicts.
- Use the plum and sand colours from your review slides. At most two fonts and one spacing scale, agreed in the first meeting.
- Every page works at phone width (test at 375 px), has visible keyboard focus, readable contrast, and red errors next to the field.
- A teammate checks your page against the style guide before you merge.

---

## Member 1: Foundation, accounts, security, deployment
- Frontend: split `js/app.js` into `core.js` plus one script per page (day 2); connect login and register to the API; show server errors in forms; settings page (name, password); redirect to login when the session expires; logout calls `/api/logout`.
- Styling: shared design system in `css/style.css`; app shell (sidebar, phone top bar); `css/auth.css` for login and register; settings page; a one-page style guide; run the design review.
- Java: Maven or Gradle project and framework choice; `DBConnection` (reads URL and credentials from environment variables); `WorkspaceItem`; `Dao<T>`; `User`, `UserDAO`, `AuthService`; BCrypt or PBKDF2 hashing; session filter; serve the frontend as static files.
- Database: choose MySQL or SQLite for everyone; own `schema_*.sql` and approve schema changes.
- Security and deployment: check one user can never read another's data; runnable jar; `Dockerfile`; pick a host (check current free-plan limits, many reset the disk on restart); deploy and test the public link on a phone; keep a local run and a screen recording as backup.
- Docs: README that lets a teammate run the project from a clean clone.
- Done when: register, login and logout work end to end, every other endpoint is protected, and the app is live.

## Member 2: Notes
- Frontend (`notes.html`): replace browser storage with `fetch`; server-side search and category filter; empty state, delete confirmation, error messages.
- Styling (`css/notes.css`): note cards with a colour per category tag; search bar and filter row; empty state; visible edit mode on the form; one column on phones.
- Java: `Note` extends `WorkspaceItem` (title, content, category, createdAt, updatedAt) with `edit()` and `delete()`; `NoteManager`; `NoteDAO`; endpoints; keyword search and category filter; server-side validation (title up to 100, content up to 2000 characters).
- Database: write and test list, search and filter queries; check `idx_notes_user_cat` is used.
- Tests: `Note` validation; `NoteDAO` create, update, delete, search; another user cannot see the note.
- Docs: update the UML class diagram with `WorkspaceItem` and inheritance, and the use case diagram.
- Done when: notes can be created, edited, deleted, searched and filtered, and survive a refresh and a server restart.

## Member 3: Tasks
- Frontend (`tasks.html`): replace browser storage with `fetch`; keep the three-column board, sort by due date or priority, and the overdue marker; loading state and error messages.
- Styling (`css/tasks.css`): board with a different tint per column; task cards; colours for low, medium and high priority; an overdue style that does not rely on colour alone; stacked columns on phones; optional drag cues.
- Java: `Task` extends `WorkspaceItem` with `markComplete()`, `edit()`, `delete()`; enums for `Priority` and `Status`; `TaskManager`; `TaskDAO`; status endpoint (To do, In progress, Done, reopen); sorting and overdue logic; validation (title 3 to 100 characters, valid due date).
- Database: queries by status, sorted by due date and priority, and counts per status (Member 6 needs these).
- Tests: status transitions, validation, DAO create/update/delete, privacy between users.
- Docs: update the Add New Task sequence diagram to the real class names.
- Done when: tasks can be created, edited, moved across the board, completed and deleted, and overdue tasks are flagged.

## Member 4: Cycle tracker and prediction
- Frontend (cycle half of `wellness.html`): replace browser storage with `fetch`; month calendar; cycle form; show the server's prediction message; clear errors for bad dates and lengths.
- Styling (`css/wellness.css`, cycle part): calendar grid with distinct markers for a logged start and the predicted date plus a legend; prediction banner; month navigation buttons. Agree the wellness page layout with Member 5.
- Java: `PeriodLog` extends `WorkspaceItem`; `PeriodTracker` with `logCycle()` and `predictNextPeriod()`; prediction = average gap between logged start dates, limited to 15 to 60 days, added to the latest start date (one log: use the entered length); `PeriodLogDAO` (same start date updates the row); validation (date not in the future, length 15 to 60).
- Database: all cycles of a user by date; latest cycle; decide whether to store `predicted_next_date` or compute it, and tell Member 6.
- Tests: prediction with 0, 1, 2 and 5 logs and odd gaps; treat the data as sensitive and test that only the owner can read it.
- Docs: the project test plan.
- Done when: cycles can be logged and edited, the calendar shows them, and the prediction updates as cycles are added.

## Member 5: Mood tracker and global search
- Frontend (mood half of `wellness.html` plus search): replace browser storage with `fetch`; mood form and recent entries table; global search bar in the navigation showing notes, tasks and wellness results together.
- Styling (`css/wellness.css`, mood part): styled sliders for pain, sleep and water with live values; mood selector; recent entries table that works on phones; search bar and results list with a label per result type. Agree the wellness page layout with Member 4.
- Java: `MoodEntry` extends `WorkspaceItem`; mood enum (HAPPY, CALM, NEUTRAL, TIRED, ANXIOUS, IRRITABLE, SAD); `MoodEntryDAO` (one entry per date, a second one updates); validation (pain 0 to 10, sleep 0 to 24, water 0 to 30, date not in the future); global search endpoint that collects notes, tasks and mood entries into one `List<WorkspaceItem>` and calls `summary()`.
- Database: last N entries, entries between two dates, averages of sleep, water and pain.
- Tests: validation; upsert; search returns items of all three types.
- Docs: review presentation slides.
- Done when: mood entries save and edit per day, and search returns correct results from all three modules.

## Member 6: Workspace, analytics, dashboard, integration lead
- Frontend (`dashboard.html`, `analytics.html`): replace browser storage with the analytics endpoint; dashboard counts, next period, recent activity; insights page with task completion, mood trend, cycle statistics and written insights; helpful empty states for new users.
- Styling (`css/dashboard.css`): stat cards; recent activity list; completion meter; mood trend chart (bars or a chart library); cycle statistics card; final check of every page at 375, 768 and 1280 px and a consistency pass so all pages look like one app; check fonts and images load quickly.
- Java: `Workspace` (links one user's notes, tasks, cycles and moods; `addNote()`, `addTask()`, `getAnalytics()`); `Analytics` with `computeMoodTrends()`, `computeCycleStats()`, `computeTaskStats()`, `generateInsights()`; one analytics endpoint; insights only when there is enough data.
- Database: aggregate queries or views across tables, agreed with Members 3, 4 and 5.
- Integration lead: merge pull requests in order and keep `main` working; run the full journey test weekly; run the two-account privacy test; collect and assign bugs.
- Tests: each analytics method, including empty and one-record cases.
- Docs: final report and demo script.
- Done when: dashboard and insights match real data from all modules and the full journey passes on a clean install.

---

## Phases (relative; stretch to your review date)
1. Setup, days 1 to 3: tools installed, repo cloned, API contract agreed, database chosen. Member 1 publishes `DBConnection`, `WorkspaceItem`, `Dao<T>`, the split scripts and the shared stylesheet.
2. Build, weeks 1 to 2: each member finishes their vertical end to end (class, DAO, endpoints, frontend, styling, tests).
3. Integrate, week 3: Member 6 merges; full journey and privacy tests; owners fix bugs.
4. Harden, week 3: server-side validation checked, passwords hashed, friendly errors, phone and second-laptop tests.
5. Launch, week 4: Member 1 deploys; rehearse the demo; record a backup video; final report and slides ready.

## Dependencies
| Needs | From | By |
|-------|------|----|
| `DBConnection`, `WorkspaceItem`, `Dao<T>`, session filter | Member 1 | Day 3 |
| Shared stylesheet and style guide | Member 1 | Day 3 |
| Task counts and status queries | Member 3 | End of week 1 |
| Cycle data and prediction | Member 4 | End of week 1 |
| Mood averages and entries | Member 5 | End of week 1 |
| All endpoints working | Members 2 to 5 | Start of week 3 |

## Definition of done
Register, log in and out; notes and tasks support create, edit, delete, search, filter and sort; cycle and mood data can be logged with validation and the prediction works; dashboard and insights match the data; a second user cannot see the first user's data; every page uses the shared styles and works at phone width; the app runs from a clean clone using only the README steps and is live at a public link; the UML diagrams match the code, including inheritance from `WorkspaceItem`.
