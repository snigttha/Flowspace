package com.flowspace.service;

import com.flowspace.dao.NoteDAO;
import com.flowspace.model.Note;
import com.flowspace.model.Note.Category;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for the Notes module.
 *
 * <p>Sits between the servlet (HTTP layer) and the DAO (persistence layer).
 * Responsible for:
 * <ul>
 *   <li>Running server-side validation before every write</li>
 *   <li>Choosing the right DAO method based on the request parameters</li>
 *   <li>Throwing {@link IllegalArgumentException} on validation failures
 *       (servlet converts these to 400 responses)</li>
 *   <li>Throwing {@link SecurityException} on privacy violations
 *       (servlet converts to 403)</li>
 * </ul>
 *
 * @author Member 2 – Notes module
 */
public class NoteManager {

    private final NoteDAO dao;

    public NoteManager() {
        this.dao = new NoteDAO();
    }

    /** Package-private constructor for unit-testing with a mock DAO. */
    NoteManager(NoteDAO dao) {
        this.dao = dao;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────

    /**
     * Creates a new note after validation.
     *
     * @param userId   the authenticated user's id
     * @param title    note title
     * @param content  note body
     * @param category category name (defaults to General if blank)
     * @return the persisted note with generated id and timestamps
     * @throws IllegalArgumentException if validation fails
     * @throws SQLException             on database errors
     */
    public Note createNote(int userId, String title, String content,
                           String category) throws SQLException {
        Category cat = parseCategoryOrDefault(category);
        Note note = new Note(userId, title, content, cat);
        note.validate(); // throws IAE on failure
        return dao.create(note);
    }

    /**
     * Updates an existing note's title, content and category.
     * Verifies that the note belongs to {@code userId}.
     *
     * @throws SecurityException        if the note belongs to another user
     * @throws IllegalArgumentException if the note is not found or validation fails
     */
    public Note updateNote(int noteId, int userId, String title,
                           String content, String category) throws SQLException {
        Note existing = dao.findById(noteId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Note not found."));

        if (existing.getUserId() != userId)
            throw new SecurityException("You do not own this note.");

        existing.edit(title, content, category); // validates internally
        return dao.update(existing)
            .orElseThrow(() -> new IllegalArgumentException("Update failed – note not found."));
    }

    /**
     * Deletes a note, verifying ownership first.
     *
     * @return true if a row was deleted
     * @throws SecurityException if the note belongs to another user
     */
    public boolean deleteNote(int noteId, int userId) throws SQLException {
        // Verify ownership before deletion
        Optional<Note> owned = dao.findById(noteId, userId);
        if (owned.isEmpty())
            throw new IllegalArgumentException("Note not found.");
        if (owned.get().getUserId() != userId)
            throw new SecurityException("You do not own this note.");

        return dao.delete(noteId, userId);
    }

    // ── List / search / filter ────────────────────────────────────────────

    /**
     * Returns notes for the given user, optionally filtered and searched.
     *
     * <p>The choice of DAO method is:
     * <table>
     *   <tr><th>q present</th><th>category present</th><th>method</th></tr>
     *   <tr><td>no</td><td>no</td><td>{@link NoteDAO#findAll}</td></tr>
     *   <tr><td>yes</td><td>no</td><td>{@link NoteDAO#search}</td></tr>
     *   <tr><td>no</td><td>yes</td><td>{@link NoteDAO#findByCategory}</td></tr>
     *   <tr><td>yes</td><td>yes</td><td>{@link NoteDAO#searchByCategory}</td></tr>
     * </table>
     *
     * @param userId   the authenticated user
     * @param query    keyword to search (null or blank = no keyword filter)
     * @param category category to filter (null or blank = all categories)
     */
    public List<Note> getNotes(int userId, String query,
                               String category) throws SQLException {
        boolean hasQ   = query    != null && !query.isBlank();
        boolean hasCat = category != null && !category.isBlank();

        if (!hasQ && !hasCat) return dao.findAll(userId);
        if (hasQ  && !hasCat) return dao.search(userId, query.trim());

        Category cat = parseCategoryOrDefault(category);
        if (!hasQ)  return dao.findByCategory(userId, cat);
        return dao.searchByCategory(userId, query.trim(), cat);
    }

    /**
     * Fetches a single note, enforcing ownership.
     *
     * @throws SecurityException        if the note belongs to another user
     * @throws IllegalArgumentException if not found
     */
    public Note getNote(int noteId, int userId) throws SQLException {
        return dao.findById(noteId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Note not found."));
    }

    // ── private helpers ───────────────────────────────────────────────────

    private Category parseCategoryOrDefault(String category) {
        if (category == null || category.isBlank()) return Category.General;
        try {
            return Category.fromString(category);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid category: " + category);
        }
    }
}
