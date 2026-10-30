package com.flowspace.service;

import com.flowspace.dao.MoodEntryDAO;
import com.flowspace.dao.NoteDAO;
import com.flowspace.dao.TaskDAO;
import com.flowspace.model.MoodEntry;
import com.flowspace.model.Note;
import com.flowspace.model.Task;
import com.flowspace.model.WorkspaceItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Service orchestrating Global Search across Notes, Tasks, and Mood Entries.
 * Core deliverable for Member 5 demonstrating OOP Polymorphism:
 * 
 * - Collects concrete subclasses (Note, Task, MoodEntry) into a single List<WorkspaceItem>.
 * - Iterates over the generic List<WorkspaceItem> calling item.summary() polymorphically.
 */
public class SearchService {
    private final NoteDAO noteDAO;
    private final TaskDAO taskDAO;
    private final MoodEntryDAO moodDAO;

    public SearchService() {
        this(new NoteDAO(), new TaskDAO(), new MoodEntryDAO());
    }

    public SearchService(NoteDAO noteDAO, TaskDAO taskDAO, MoodEntryDAO moodDAO) {
        this.noteDAO = noteDAO;
        this.taskDAO = taskDAO;
        this.moodDAO = moodDAO;
    }

    /**
     * Executes polymorphic global search.
     * 
     * @param userId Logged in user ID (tenant isolation)
     * @param query  Search term
     * @return List of SearchResult DTOs produced via polymorphic WorkspaceItem.summary()
     */
    public List<SearchResult> search(int userId, String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String cleanQuery = query.trim();

        // 1. Collect notes, tasks, and mood entries into ONE List<WorkspaceItem>
        List<WorkspaceItem> workspaceItems = new ArrayList<>();

        List<Note> notes = noteDAO.search(userId, cleanQuery);
        List<Task> tasks = taskDAO.search(userId, cleanQuery);
        List<MoodEntry> moods = moodDAO.search(userId, cleanQuery);

        workspaceItems.addAll(notes);
        workspaceItems.addAll(tasks);
        workspaceItems.addAll(moods);

        // 2. Polymorphic iteration: each item dynamically resolves its own summary()
        List<SearchResult> results = new ArrayList<>();
        for (WorkspaceItem item : workspaceItems) {
            String polymorphicSummary = item.summary(); // POLYMORPHISM IN ACTION

            String title;
            String date;
            String url;

            if (item instanceof Note) {
                Note n = (Note) item;
                title = n.getTitle();
                date = n.getUpdatedAt() != null ? n.getUpdatedAt().toLocalDate().toString() : "";
                url = "notes.html";
            } else if (item instanceof Task) {
                Task t = (Task) item;
                title = t.getTitle();
                date = t.getDueDate() != null ? t.getDueDate().toString() : "";
                url = "tasks.html";
            } else if (item instanceof MoodEntry) {
                MoodEntry m = (MoodEntry) item;
                title = (m.getMood() != null ? m.getMood().getDisplayName() : "Mood") + " on " + m.getEntryDate();
                date = m.getEntryDate() != null ? m.getEntryDate().toString() : "";
                url = "wellness.html";
            } else {
                title = "Item #" + item.getId();
                date = "";
                url = "dashboard.html";
            }

            results.add(new SearchResult(item.getItemType(), item.getId(), title, polymorphicSummary, date, url));
        }

        return results;
    }

    /**
     * Returns the raw polymorphic WorkspaceItem list (used in unit tests).
     */
    public List<WorkspaceItem> searchRawItems(int userId, String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<WorkspaceItem> items = new ArrayList<>();
        items.addAll(noteDAO.search(userId, query));
        items.addAll(taskDAO.search(userId, query));
        items.addAll(moodDAO.search(userId, query));
        return items;
    }

    /**
     * DTO for JSON serialization of search results.
     */
    public static class SearchResult {
        private final String type;
        private final int id;
        private final String title;
        private final String summary;
        private final String date;
        private final String url;

        public SearchResult(String type, int id, String title, String summary, String date, String url) {
            this.type = type;
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.date = date;
            this.url = url;
        }

        public String getType() { return type; }
        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getSummary() { return summary; }
        public String getDate() { return date; }
        public String getUrl() { return url; }

        @Override
        public String toString() {
            return "[" + type + "] " + title + " -> " + summary;
        }
    }
}
