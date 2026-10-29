# Member 3 – Tasks Module

**Author:** Hishal Hamza (Member 3)  
**Slice:** Tasks & Kanban Board Vertical Slice

## Overview

This module implements the **Tasks & Kanban Board** vertical slice for FlowSpace, including:

- `tasks.html` – Kanban board view (To Do, In Progress, Done columns, real-time metrics, new task modal)
- `css/tasks.css` – Tasks-specific stylesheet (priority badges, column tints, overdue indicators, responsive grid)
- `js/tasks.js` – Frontend controller (dual-mode: communicates with REST backend `/api/tasks` when online, falls back gracefully to localStorage)
- `backend/` – Java Maven module with REST API, service layer, DAO layer, domain models, and JUnit tests

## Features & Implementation

1. **Kanban & List State Management:**
   - 3 Column workflow: `To Do` → `In Progress` → `Done`
   - State-machine rule: A completed (`Done`) task can only be reopened to `To Do`.
2. **Metrics & Counters:**
   - Real-time counters for Total, In Progress, Completed, and Overdue tasks.
   - Specific calculation helper `countByStatus` and `countOverdue` exposed for Member 6's dashboard analytics.
3. **Multi-Criteria Filtering & Sorting:**
   - Filter by status (`TODO`, `IN_PROGRESS`, `DONE`) and priority (`LOW`, `MEDIUM`, `HIGH`).
   - Sort by due date (soonest first) or priority (high to low).
4. **Security & Row-Level Privacy:**
   - All queries filter strictly by authenticated `user_id`.
   - Ownership verification on updates, deletions, and status changes.

## How to Run

### 1. Frontend Demo (Standalone / LocalStorage)

Open `tasks.html` directly in your browser or run a lightweight local static server:

```powershell
python -m http.server 8000
# open http://localhost:8000/tasks.html
```

### 2. Full Stack (Jetty Server + SQLite)

**Prerequisites**: JDK 11+ (JDK 17 recommended) and Maven 3.x

```powershell
# 1. Initialize DB with schema and seed data
sqlite3 flowspace.db < database/schema_sqlite.sql
sqlite3 flowspace.db < database/seed_demo.sql

# 2. Start the backend
cd backend
mvn jetty:run
```

The application will be live at `http://localhost:8080/tasks.html`.

## Running Tests

All unit and integration tests run against an in-memory SQLite database:

```powershell
cd backend
mvn test
```

### Test Coverage for Member 3:
- `TaskValidationTest.java`: 13 test cases (title length boundaries, invalid user ID, null priority, state transition rules, overdue calculations).
- `TaskDAOTest.java`: 9 test cases (CRUD operations, ownership isolation between users, status counts for Member 6, overdue filtering).

## API Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/tasks` | List all tasks for current user |
| GET | `/api/tasks?status=TODO` | Filter by status (`TODO`, `IN_PROGRESS`, `DONE`) |
| GET | `/api/tasks?priority=HIGH` | Filter by priority (`LOW`, `MEDIUM`, `HIGH`) |
| GET | `/api/tasks?overdue=true` | Filter overdue tasks |
| GET | `/api/tasks?counts=true` | Returns status counts map + overdue count for dashboard |
| GET | `/api/tasks/{id}` | Get specific task details (ownership verified) |
| POST | `/api/tasks` | Create new task |
| PUT | `/api/tasks/{id}` | Update title, due date, priority, or status |
| PATCH | `/api/tasks/{id}/status` | Move card / update status |
| DELETE | `/api/tasks/{id}` | Delete task |

All endpoints require session authentication (`userId` stored in `HttpSession`).

## Server-Side Validation Rules

| Field | Rule |
|-------|------|
| `title` | Required, 3 to 100 characters |
| `dueDate` | Required, valid ISO `YYYY-MM-DD` date |
| `priority` | Required, one of: `LOW`, `MEDIUM`, `HIGH` |
| `status` | One of: `TODO`, `IN_PROGRESS`, `DONE` (DONE cannot directly transition to IN_PROGRESS) |
| `userId` | Required, must be a positive integer matching active session |

## File Structure

```
flowspace/
├── tasks.html                                ← Tasks Kanban view
├── css/tasks.css                             ← Tasks styles
├── js/tasks.js                               ← Tasks client controller
├── docs/
│   ├── member3_uml.md                        ← UML architecture & use cases
│   └── add-task-sequence.md                  ← Sequence diagram
└── backend/
    ├── pom.xml
    └── src/
        ├── main/java/com/flowspace/
        │   ├── model/
        │   │   ├── Priority.java             ← Priority enum (LOW, MEDIUM, HIGH)
        │   │   ├── Status.java               ← Status enum + state machine logic
        │   │   └── Task.java                 ← Task entity (extends WorkspaceItem)
        │   ├── dao/
        │   │   └── TaskDAO.java              ← PreparedStatement DAO with SQL queries
        │   ├── service/
        │   │   └── TaskManager.java          ← Business logic layer
        │   └── api/
        │       └── TaskServlet.java          ← REST endpoint servlet
        └── test/java/com/flowspace/
            ├── model/TaskValidationTest.java ← Validation unit tests
            └── dao/TaskDAOTest.java          ← In-memory SQLite DAO tests
```
