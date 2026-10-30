package com.flowspace;

import com.flowspace.dao.MoodEntryDAO;
import com.flowspace.dao.NoteDAO;
import com.flowspace.dao.TaskDAO;
import com.flowspace.model.*;
import com.flowspace.service.SearchService;
import com.flowspace.util.DBConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests for SearchService.
 * Validates Member 5 requirement:
 * - Collects notes, tasks, and mood entries into one List<WorkspaceItem>
 * - Demonstrates polymorphism via item.summary()
 * - Search returns items of all three types
 */
public class SearchServiceTest {
    private SearchService searchService;
    private NoteDAO noteDAO;
    private TaskDAO taskDAO;
    private MoodEntryDAO moodDAO;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        DBConnection.setCustomJdbcUrl("jdbc:sqlite::memory:");
        conn = DBConnection.getConnection();
        DBConnection.initializeSchema(conn);

        noteDAO = new NoteDAO();
        taskDAO = new TaskDAO();
        moodDAO = new MoodEntryDAO();
        searchService = new SearchService(noteDAO, taskDAO, moodDAO);

        int userId = 1;

        // Seed 1 Note
        noteDAO.create(new Note(0, userId, "Java OOP Revision", "Polymorphism and inheritance study notes", "Study", LocalDateTime.now(), LocalDateTime.now()));

        // Seed 1 Task
        taskDAO.create(new Task(0, userId, "Complete OOP Assignment", LocalDate.of(2026, 11, 5), "HIGH", "TODO", LocalDateTime.now()));

        // Seed 1 Mood Entry
        moodDAO.create(new MoodEntry(0, userId, LocalDate.of(2026, 10, 20), Mood.HAPPY, "Studied OOP successfully", 1, 8.0, 8, LocalDateTime.now()));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
        DBConnection.setCustomJdbcUrl(null);
    }

    @Test
    @DisplayName("Search returns items of all three types (NOTE, TASK, MOOD)")
    void testSearchReturnsAllThreeTypes() {
        int userId = 1;
        // Search keyword "OOP" appears in all three seeded items
        List<SearchService.SearchResult> results = searchService.search(userId, "OOP");

        assertEquals(3, results.size(), "Search should find 1 note, 1 task, and 1 mood entry");

        Set<String> types = results.stream().map(SearchService.SearchResult::getType).collect(Collectors.toSet());
        assertTrue(types.contains("NOTE"), "Results should contain a NOTE");
        assertTrue(types.contains("TASK"), "Results should contain a TASK");
        assertTrue(types.contains("MOOD"), "Results should contain a MOOD");

        // Verify summaries are generated via polymorphic summary()
        for (SearchService.SearchResult r : results) {
            assertNotNull(r.getSummary());
            assertFalse(r.getSummary().isEmpty());
            System.out.println("Polymorphic summary: " + r.getSummary());
        }
    }

    @Test
    @DisplayName("Raw polymorphic WorkspaceItem collection and summary() invocation")
    void testPolymorphicListProcessing() {
        int userId = 1;
        List<WorkspaceItem> items = searchService.searchRawItems(userId, "OOP");

        assertEquals(3, items.size());

        // Polymorphic check: every element is a subclass of WorkspaceItem
        for (WorkspaceItem item : items) {
            String summary = item.summary(); // Dynamic dispatch (Polymorphism)
            assertNotNull(summary);
            assertTrue(item instanceof Note || item instanceof Task || item instanceof MoodEntry);
        }
    }

    @Test
    @DisplayName("Tenant isolation: User 2 cannot see User 1's search results")
    void testSearchUserIsolation() {
        List<SearchService.SearchResult> u2Results = searchService.search(2, "OOP");
        assertTrue(u2Results.isEmpty(), "User 2 should not find User 1's items");
    }
}
