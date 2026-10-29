# FlowSpace – Member 3: Tasks Module – UML Documentation
**Author:** Hishal Hamza (Member 3)  
**Module:** Tasks & Kanban Board Vertical Slice

---

## 1. Class Hierarchy (Text Form)

```
WorkspaceItem (abstract)
  - id          : int
  - userId      : int
  - createdAt   : LocalDateTime
  + validate()  : void   [abstract]
  + summary()   : String [abstract]

  ├── Note       extends WorkspaceItem  [Member 2]
  │
  ├── Task       extends WorkspaceItem  [Member 3]
  │     - title       : String          (3 to 100 chars)
  │     - dueDate     : LocalDate       (required)
  │     - priority    : Priority enum   {LOW, MEDIUM, HIGH}
  │     - status      : Status enum     {TODO, IN_PROGRESS, DONE}
  │     + Task()
  │     + Task(userId, title, dueDate, priority)
  │     + Task(id, userId, title, dueDate, priority, status, createdAt)
  │     + validate()                    throws IllegalArgumentException
  │     + summary()                     → "[Task] title (status, priority, due: date)"
  │     + updateStatus(newStatus)       enforces state machine: DONE -> only TODO
  │     + isOverdue()                   checks if due < today and status != DONE
  │     + isDueSoon(days)               checks if within upcoming threshold
  │
  ├── MoodEntry  extends WorkspaceItem  [Member 5]
  └── PeriodLog  extends WorkspaceItem  [Member 4]

Dao<T> (interface)
  + create(T)              : T
  + findById(int, int)     : Optional<T>
  + findAll(int)           : List<T>
  + update(T)              : Optional<T>
  + delete(int, int)       : boolean

  └── TaskDAO implements Dao<Task>
        + findByStatus(userId, Status)                  : List<Task>
        + findByPriority(userId, Priority)              : List<Task>
        + findOverdue(userId)                           : List<Task>
        + countByStatus(userId)                         : Map<Status, Integer>  [Used by Member 6 Analytics]
        + countOverdue(userId)                          : int                   [Used by Member 6 Analytics]
        + updateStatus(taskId, userId, Status)          : boolean
        # openConnection()                              : Connection  [overridable for tests]

TaskManager (service layer)
  - dao : TaskDAO
  + createTask(userId, title, dueDate, priority)             : Task
  + getTask(taskId, userId)                                  : Optional<Task>
  + getUserTasks(userId)                                     : List<Task>
  + getTasksByStatus(userId, Status)                         : List<Task>
  + getTasksByPriority(userId, Priority)                     : List<Task>
  + getOverdueTasks(userId)                                  : List<Task>
  + updateTask(taskId, userId, title, dueDate, priority, st) : Optional<Task>
  + changeStatus(taskId, userId, Status)                     : boolean
  + deleteTask(taskId, userId)                               : boolean
  + getStatusCounts(userId)                                  : Map<Status, Integer>
  + getOverdueCount(userId)                                  : int

TaskServlet (Jakarta Servlet)
  @WebServlet(urlPatterns = {"/api/tasks", "/api/tasks/*"})
  - manager : TaskManager
  + doGet    → GET    /api/tasks[?status=&priority=&overdue=true|counts]
  + doPost   → POST   /api/tasks
  + doPut    → PUT    /api/tasks/{id}
  + doPatch  → PATCH  /api/tasks/{id}/status
  + doDelete → DELETE /api/tasks/{id}

DBConnection (shared infrastructure)
  + get() : Connection   [reads DB_URL, DB_USER, DB_PASSWORD env vars]
```

---

## 2. Relationships

| From | Relationship | To | Description |
|------|-------------|----|-------------|
| Task | **extends** | WorkspaceItem | Inherits `id`, `userId`, `createdAt`, implements `validate()` and `summary()` |
| Task | **contains** | Priority enum | LOW, MEDIUM, HIGH |
| Task | **contains** | Status enum | TODO, IN_PROGRESS, DONE (with transition validator) |
| TaskDAO | **implements** | Dao\<Task\> | Implements generic CRUD contract with parameterized `task_id` queries |
| TaskManager | **uses** | TaskDAO | Orchestrates business validation, entity mutation, and persistence |
| TaskServlet | **uses** | TaskManager | Handles HTTP transport, JSON serialization, and error mapping |
| TaskDAO | **uses** | DBConnection | Connects to configured database (SQLite / MySQL) |
| Member 6 (Dashboard) | **queries** | TaskDAO / TaskManager | Uses `countByStatus(userId)` and `countOverdue(userId)` |

---

## 3. Use Case Diagram (Text Form)

```
                    ┌─────────────────────────────────────────┐
                    │               Tasks Module               │
[Logged-in User]────┤                                         │
                    │  UC1: Create New Task                   │──────┐
                    │     <<include>> Validate Length & Date  │      │
                    │                                         │      │
                    │  UC2: Update Task Details               │      │
                    │     <<include>> Ownership Check         │      │
                    │     <<include>> Validate               │      │
                    │                                         │      │
                    │  UC3: Change Status (Kanban / State)    │      │
                    │     <<include>> State Machine Rule      │      ▼
                    │                                         │  [Java Server]
                    │  UC4: Delete Task                       │  (TaskServlet &
                    │     <<include>> Ownership Check         │   TaskManager)
                    │                                         │      │
                    │  UC5: Filter Tasks (Status, Priority)   │      │
                    │     <<include>> Auth Check              │      ▼
                    │                                         │  [SQLite/MySQL]
                    │  UC6: View Analytics / Counters         │
                    │     <<include>> Auth Check              │
                    │     <<include>> Overdue Calculation     │
                    └─────────────────────────────────────────┘
```

---

## 4. Sequence Diagram – Create Task

```
User          tasks.js      TaskServlet     TaskManager      TaskDAO          DB
 │                │               │               │              │             │
 │──fill modal──► │               │               │              │             │
 │                │──validate────►│               │              │             │
 │   [fail]◄──────│ (client-side) │               │              │             │
 │                │               │               │              │             │
 │ [pass]         │──POST /api/──►│               │              │             │
 │                │  tasks {JSON} │               │              │             │
 │                │               │──requireSess─►│              │             │
 │                │               │  get userId   │              │             │
 │                │               │               │              │             │
 │                │               │──createTask──►│              │             │
 │                │               │               │──new Task()─►│             │
 │                │               │               │──validate()─►│             │
 │                │               │               │              │             │
 │                │               │               │──create()───►│             │
 │                │               │               │              │──INSERT────►│
 │                │               │               │              │◄─Gen Key────│
 │                │               │               │◄─Task Entity─│             │
 │                │               │◄─Task Entity──│              │             │
 │                │◄─201 Created──│               │              │             │
 │                │  {Task JSON}  │               │              │             │
 │◄─add card to───│               │               │              │             │
 │  DOM & update  │               │               │              │             │
 │  counters      │               │               │              │             │
```

---

## 5. State Machine Transition Diagram

Tasks follow a strict progression cycle with business rules:

```
  ┌───────────┐         start          ┌───────────────┐
  │   TODO    │───────────────────────►│  IN_PROGRESS  │
  └───────────┘                        └───────────────┘
        ▲                                      │
        │                complete              │
        │◄─────────────────────────────────────┘
        │                │
        │                ▼
        │        ┌───────────────┐
        └────────│     DONE      │
         reopen  └───────────────┘
```

- **Rule:** A task marked **DONE** cannot directly jump to **IN_PROGRESS**; it must be reopened to **TODO** first to ensure review integrity.

---

## 6. Database Schema Alignment

Mapped to `tasks` table in `database/schema_sqlite.sql`:

```sql
CREATE TABLE IF NOT EXISTS tasks (
    task_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL,
    title       TEXT NOT NULL,
    due_date    TEXT NOT NULL,
    priority    TEXT NOT NULL DEFAULT 'MEDIUM',
    status      TEXT NOT NULL DEFAULT 'TODO',
    created_at  TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
```
