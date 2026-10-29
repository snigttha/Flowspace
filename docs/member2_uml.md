# FlowSpace – Member 2: Notes Module – UML Documentation

## 1. Class Hierarchy (text form)

```
WorkspaceItem (abstract)
  - id          : int
  - userId      : int
  - createdAt   : LocalDateTime
  + validate()  : void   [abstract]
  + summary()   : String [abstract]

  ┌── Note extends WorkspaceItem
  │     - title     : String      (max 100 chars)
  │     - content   : String      (max 2000 chars)
  │     - category  : Category enum {General, Study, Personal, Ideas, Health}
  │     - updatedAt : LocalDateTime
  │     + Note()
  │     + Note(userId, title, content, category)
  │     + validate()   throws IllegalArgumentException
  │     + summary()    → "[Note] title — preview (category)"
  │     + edit(title, content, category)
  │     + delete()
  │
  ├── Task       extends WorkspaceItem  [Member 3]
  ├── MoodEntry  extends WorkspaceItem  [Member 5]
  └── PeriodLog  extends WorkspaceItem  [Member 4]

Dao<T> (interface)
  + create(T)              : T
  + findById(int, int)     : Optional<T>
  + findAll(int)           : List<T>
  + update(T)              : Optional<T>
  + delete(int, int)       : boolean

  └── NoteDAO implements Dao<Note>
        + search(userId, keyword)           : List<Note>
        + findByCategory(userId, Category)  : List<Note>
        + searchByCategory(userId, kw, cat) : List<Note>
        # openConnection()                  : Connection  [overridable for tests]

NoteManager (service layer)
  - dao : NoteDAO
  + createNote(userId, title, content, category) : Note
  + updateNote(noteId, userId, title, content, category) : Note
  + deleteNote(noteId, userId)           : boolean
  + getNotes(userId, query, category)    : List<Note>
  + getNote(noteId, userId)              : Note

NoteServlet (Jakarta Servlet)
  @WebServlet("/api/notes", "/api/notes/*")
  - manager : NoteManager
  + doGet    → GET  /api/notes[?q=&category=]
  + doPost   → POST /api/notes
  + doPut    → PUT  /api/notes/{id}
  + doDelete → DELETE /api/notes/{id}

DBConnection (shared infrastructure)
  + get() : Connection   [reads DB_URL, DB_USER, DB_PASSWORD env vars]
```

---

## 2. Relationships

| From | Relationship | To |
|------|-------------|-----|
| Note | **extends** | WorkspaceItem |
| Task | extends | WorkspaceItem |
| MoodEntry | extends | WorkspaceItem |
| PeriodLog | extends | WorkspaceItem |
| NoteDAO | **implements** | Dao\<Note\> |
| NoteManager | **uses** | NoteDAO |
| NoteServlet | **uses** | NoteManager |
| NoteDAO | **uses** | DBConnection |
| Note | **contains** | Category enum |

---

## 3. Use Case Diagram (text form)

```
                    ┌─────────────────────────────┐
                    │        Notes Module           │
[Logged-in User]────┤                              │
                    │  UC1: Create Note            │──────┐
                    │     <<include>> Validate     │      │
                    │                              │      │
                    │  UC2: Edit Note              │      │
                    │     <<include>> Own. Check   │      │
                    │     <<include>> Validate     │      │
                    │                              │      ▼
                    │  UC3: Delete Note            │  [Java Server]
                    │     <<include>> Own. Check   │      │
                    │                              │      ▼
                    │  UC4: Search Notes by KW     │  [SQLite/MySQL]
                    │     <<include>> Auth Check   │
                    │                              │
                    │  UC5: Filter by Category     │
                    │     <<include>> Auth Check   │
                    │                              │
                    │  UC6: View All Notes         │
                    │     <<include>> Auth Check   │
                    └─────────────────────────────┘
```

---

## 4. Sequence Diagram – Create Note

```
User          notes.js      NoteServlet     NoteManager      NoteDAO         DB
 │                │               │               │              │             │
 │──fill form──►  │               │               │              │             │
 │                │──FSV.check()─►│               │              │             │
 │   [fail]◄──────│               │               │              │             │
 │                │               │               │              │             │
 │ [pass]         │──POST /api/──►│               │              │             │
 │                │  notes {JSON} │               │              │             │
 │                │               │──requireSess─►│              │             │
 │                │  [no session] │               │              │             │
 │                │◄──────401─────│               │              │             │
 │                │               │──createNote──►│              │             │
 │                │               │               │──validate()  │             │
 │  [invalid]     │               │               │◄throw IAE    │             │
 │                │◄──400 {msg}───│               │              │             │
 │                │               │               │──dao.create─►│             │
 │                │               │               │              │──INSERT───►│
 │                │               │               │              │◄──note_id──│
 │                │               │               │              │──SELECT───►│
 │                │               │               │              │◄──row+ts───│
 │                │               │               │◄────Note─────│             │
 │                │               │◄────Note──────│              │             │
 │                │◄──201 {JSON}──│               │              │             │
 │                │──load()───────│               │              │             │
 │◄──toast+card───│               │               │              │             │
```

---

## 5. File Inventory

| File | Layer | Purpose |
|------|-------|---------|
| `notes.html` | Frontend | Page HTML: form, search, filter, delete dialog |
| `css/notes.css` | Frontend | Category colours, card styles, responsive layout |
| `js/notes.js` | Frontend | NotesAPI + NotesUI + NotesPage controller |
| `backend/pom.xml` | Build | Maven: Jakarta Servlet 6, Gson, SQLite JDBC, JUnit 5 |
| `.../model/WorkspaceItem.java` | Domain | Abstract base (shared with all members) |
| `.../model/Note.java` | Domain | Note entity with validated setters + edit()/delete() |
| `.../dao/Dao.java` | DAO | Generic Dao\<T\> interface (shared) |
| `.../dao/NoteDAO.java` | DAO | CRUD + keyword search + category filter queries |
| `.../service/NoteManager.java` | Service | Business logic, privacy enforcement, query routing |
| `.../api/NoteServlet.java` | API | REST: GET/POST/PUT/DELETE for /api/notes |
| `.../db/DBConnection.java` | Infra | JDBC helper (env-var, SQLite fallback) |
| `.../webapp/WEB-INF/web.xml` | Config | Session timeout, welcome file |
| `.../NoteValidationTest.java` | Tests | JUnit 5 – Note validation unit tests |
| `.../NoteDAOTest.java` | Tests | JUnit 5 – DAO integration + privacy tests |
| `docs/member2_uml.md` | Docs | This document |

---

## 6. REST API Contract

| Method | Endpoint | Auth | Body | Response | Errors |
|--------|----------|------|------|----------|--------|
| GET | `/api/notes` | Session | — | `200 [{note_id,…}]` | 401 |
| GET | `/api/notes?q=kw` | Session | — | `200 [{…}]` filtered | 401 |
| GET | `/api/notes?category=Study` | Session | — | `200 [{…}]` filtered | 401 |
| GET | `/api/notes?q=kw&category=Study` | Session | — | `200 [{…}]` | 401 |
| POST | `/api/notes` | Session | `{title,content,category}` | `201 {note}` | 400, 401 |
| PUT | `/api/notes/{id}` | Session | `{title,content,category}` | `200 {note}` | 400,401,403 |
| DELETE | `/api/notes/{id}` | Session | — | `200 {message}` | 401,403,404 |

JSON field names match SQL column names (`note_id`, `user_id`, `created_at`, `updated_at`).

---

## 7. Server-side Validation Rules

| Field | Rule | Error message |
|-------|------|---------------|
| `title` | Required, non-blank | "Title is required." |
| `title` | Max 100 characters | "Title must be 100 characters or fewer." |
| `content` | Required, non-blank | "Note content is required." |
| `content` | Max 2000 characters | "Note must be 2000 characters or fewer." |
| `category` | One of 5 valid values | "Invalid category: …" |

---

## 8. Database Index

```sql
-- Defined in database/schema_sqlite.sql and schema_mysql.sql
CREATE INDEX IF NOT EXISTS idx_notes_user_cat ON notes(user_id, category);
```

- `SELECT * FROM notes WHERE user_id=? AND category=?` uses this index (leading column `user_id` + covering `category`).
- Combined search+filter query also uses it for the category predicate.
- EXPLAIN QUERY PLAN in SQLite confirms index usage with `USING INDEX idx_notes_user_cat`.
