# Member 2 – Notes Module

## Overview

This module implements the **Notes** vertical slice for FlowSpace, including:

- `notes.html` – Notes page (form, search bar, filter, delete dialog)
- `css/notes.css` – Notes-specific stylesheet (category colours, responsive layout)
- `js/notes.js` – Frontend controller (NotesAPI, NotesUI, NotesPage)
- `backend/` – Java Maven project with REST API, service, DAO, and tests

## How to run

### 1. Frontend only (localStorage demo)

Open `notes.html` in a browser, or:

```powershell
cd flowspace
python -m http.server 8000
# then open http://localhost:8000/notes.html
```

Notes are stored in `localStorage`. No Java required for the demo.

### 2. Full stack (Java backend + SQLite)

**Prerequisites**: JDK 11+ and Maven 3.x

```powershell
# 1. Create the database
sqlite3 flowspace.db < database/schema_sqlite.sql
sqlite3 flowspace.db < database/seed_demo.sql

# 2. Build and run the backend
cd backend
mvn jetty:run          # starts on http://localhost:8080
```

The servlet serves the frontend static files from the project root.  
Open `http://localhost:8080` to use the full app.

### 3. Environment variables (optional)

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:sqlite:flowspace.db` | JDBC URL |
| `DB_USER` | (none) | DB username (MySQL only) |
| `DB_PASSWORD` | (none) | DB password (MySQL only) |

## Running tests

```powershell
cd backend
mvn test
```

Tests run against an in-memory SQLite database so no external setup is needed.

## API endpoints

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/notes` | List all notes (auth required) |
| GET | `/api/notes?q=keyword` | Keyword search |
| GET | `/api/notes?category=Study` | Category filter |
| GET | `/api/notes?q=kw&category=Study` | Both filters |
| POST | `/api/notes` | Create note |
| PUT | `/api/notes/{id}` | Update note |
| DELETE | `/api/notes/{id}` | Delete note |

All endpoints require a valid session cookie (`userId` in `HttpSession`).

## Server-side validation

| Field | Rule |
|-------|------|
| `title` | Required, max 100 characters |
| `content` | Required, max 2000 characters |
| `category` | One of: General, Study, Personal, Ideas, Health |

## File structure

```
flowspace/
├── notes.html                        ← Notes page
├── css/notes.css                     ← Notes stylesheet
├── js/notes.js                       ← Frontend controller
└── backend/
    ├── pom.xml
    └── src/
        ├── main/java/com/flowspace/
        │   ├── model/
        │   │   ├── WorkspaceItem.java  ← Abstract base (shared)
        │   │   └── Note.java           ← Note entity
        │   ├── dao/
        │   │   ├── Dao.java            ← Generic interface (shared)
        │   │   └── NoteDAO.java        ← SQL queries
        │   ├── service/
        │   │   └── NoteManager.java    ← Business logic
        │   ├── api/
        │   │   └── NoteServlet.java    ← REST endpoints
        │   └── db/
        │       └── DBConnection.java   ← JDBC helper (shared)
        └── test/java/com/flowspace/
            ├── model/NoteValidationTest.java
            └── dao/NoteDAOTest.java
```
