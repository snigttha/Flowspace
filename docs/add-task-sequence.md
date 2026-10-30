# FlowSpace – Add New Task Sequence Diagram
**Owner:** Member 3 (Hishal Hamza)  
**Components:** Browser (`tasks.html` & `tasks.js`) → `TaskServlet` (`/api/tasks`) → `TaskManager` → `Task` → `TaskDAO` → SQLite/MySQL Database

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Browser as Browser (tasks.html / tasks.js)
    participant Servlet as TaskServlet (/api/tasks)
    participant Mgr as TaskManager
    participant Entity as Task (WorkspaceItem)
    participant DAO as TaskDAO
    participant DB as SQLite / MySQL DB

    User->>Browser: Enters Title, Priority, Due Date & clicks "Save Task"
    Browser->>Browser: Client-side validation (FSV / 3-100 chars, valid date)
    
    Browser->>Servlet: POST /api/tasks {title, dueDate, priority}
    Servlet->>Servlet: Extract user_id from session context
    
    Servlet->>Mgr: createTask(userId, title, dueDate, priority)
    Mgr->>Entity: new Task(userId, title, dueDate, priority)
    Entity->>Entity: validate() (Checks title length, priority != null, dueDate != null)
    
    alt Validation Failure
        Entity-->>Mgr: throws IllegalArgumentException
        Mgr-->>Servlet: Exception propagates
        Servlet-->>Browser: 400 Bad Request {error: "Title must be between 3 and 100 characters"}
        Browser-->>User: Displays error notification
    else Validation Success
        Mgr->>DAO: create(task)
        DAO->>DB: PreparedStatement INSERT INTO tasks (user_id, title, due_date, priority, status)\nVALUES (?, ?, ?, ?, ?)
        DB-->>DAO: Generated Key (task_id)
        DAO-->>Mgr: Task entity with id, createdAt, status=TODO
        Mgr-->>Servlet: Created Task
        Servlet-->>Browser: 201 Created (Task JSON)
        Browser->>Browser: Render task card in "To Do" column & refresh metric counters
        Browser-->>User: Close modal, show success toast
    end
```
