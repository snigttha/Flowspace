package com.flowspace.model;

import java.time.LocalDateTime;

/**
 * Domain model for a user note.
 *
 * <p>Extends {@link WorkspaceItem} (inheritance) and provides:
 * <ul>
 *   <li>Private fields with validated setters (encapsulation)</li>
 *   <li>Server-side validation: title ≤ 100 chars, content ≤ 2 000 chars</li>
 *   <li>{@link #validate()} required by the abstract parent</li>
 *   <li>{@link #summary()} for polymorphic global search</li>
 *   <li>{@link #edit(String, String, String)} and {@link #delete()} helpers</li>
 * </ul>
 *
 * <p>Field names match the SQL column names (PLAN.md API contract):
 * {@code note_id, user_id, title, content, category, created_at, updated_at}
 *
 * @author Member 2 – Notes module
 */
public class Note extends WorkspaceItem {

    // ── Allowed categories (kept in sync with notes.html <select>) ──────
    public enum Category {
        General, Study, Personal, Ideas, Health;

        public static Category fromString(String s) {
            if (s == null || s.isBlank()) return General;
            for (Category c : values()) {
                if (c.name().equalsIgnoreCase(s.trim())) return c;
            }
            throw new IllegalArgumentException("Unknown category: " + s);
        }
    }

    // ── Validation limits (mirror SQL CHECK constraints) ─────────────────
    public static final int MAX_TITLE_LENGTH   = 100;
    public static final int MAX_CONTENT_LENGTH = 2_000;

    // ── Private fields ───────────────────────────────────────────────────
    private String        title;
    private String        content;
    private Category      category;
    private LocalDateTime updatedAt;

    // ── Constructors ─────────────────────────────────────────────────────

    /** Default constructor for DAO hydration. */
    public Note() {}

    /**
     * Full constructor used when creating a new note.
     *
     * @param userId   owner's user id
     * @param title    note title (1 – 100 chars)
     * @param content  note body  (1 – 2 000 chars)
     * @param category one of the allowed Category values
     */
    public Note(int userId, String title, String content, Category category) {
        setUserId(userId);
        setTitle(title);
        setContent(content);
        setCategory(category);
        LocalDateTime now = LocalDateTime.now();
        setCreatedAt(now);
        this.updatedAt = now;
    }

    // ── WorkspaceItem contract ───────────────────────────────────────────

    /**
     * Server-side validation – called by {@link com.flowspace.service.NoteManager}
     * before any database write.
     *
     * @throws IllegalArgumentException with a user-facing message when
     *         a rule is violated
     */
    @Override
    public void validate() {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Title is required.");
        if (title.length() > MAX_TITLE_LENGTH)
            throw new IllegalArgumentException(
                "Title must be " + MAX_TITLE_LENGTH + " characters or fewer.");
        if (content == null || content.isBlank())
            throw new IllegalArgumentException("Note content is required.");
        if (content.length() > MAX_CONTENT_LENGTH)
            throw new IllegalArgumentException(
                "Note must be " + MAX_CONTENT_LENGTH + " characters or fewer.");
        if (category == null)
            throw new IllegalArgumentException("Category is required.");
    }

    /**
     * Short description for the global search result list (polymorphism).
     * Format:  {@code [Note] My Title — first 80 chars of content… (Category)}
     */
    @Override
    public String summary() {
        String preview = content == null ? "" :
            (content.length() > 80 ? content.substring(0, 77) + "…" : content);
        return "[Note] " + title + " — " + preview +
               " (" + (category != null ? category.name() : "General") + ")";
    }

    // ── Business methods ─────────────────────────────────────────────────

    /**
     * Applies in-place edits and stamps {@link #updatedAt}.
     * Validates after applying changes so the object remains consistent.
     *
     * @param newTitle    new title (must pass validation)
     * @param newContent  new content (must pass validation)
     * @param newCategory new category string (must be a valid Category name)
     */
    public void edit(String newTitle, String newContent, String newCategory) {
        setTitle(newTitle);
        setContent(newContent);
        setCategory(Category.fromString(newCategory));
        this.updatedAt = LocalDateTime.now();
        validate();
    }

    /**
     * Marks the note as logically deleted.
     * Physical deletion is handled by {@link com.flowspace.dao.NoteDAO#delete}.
     * This method exists as a hook so subclasses or decorators can react.
     */
    public void delete() {
        // No soft-delete column in schema; physical deletion is used.
        // Intentional no-op here; NoteDAO#delete does the actual SQL.
    }

    // ── Getters / validated setters ──────────────────────────────────────

    public String getTitle() { return title; }

    public void setTitle(String title) {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Title is required.");
        if (title.length() > MAX_TITLE_LENGTH)
            throw new IllegalArgumentException(
                "Title must be " + MAX_TITLE_LENGTH + " characters or fewer.");
        this.title = title.trim();
    }

    public String getContent() { return content; }

    public void setContent(String content) {
        if (content == null || content.isBlank())
            throw new IllegalArgumentException("Content is required.");
        if (content.length() > MAX_CONTENT_LENGTH)
            throw new IllegalArgumentException(
                "Content must be " + MAX_CONTENT_LENGTH + " characters or fewer.");
        this.content = content;
    }

    public Category getCategory() { return category; }

    public void setCategory(Category category) {
        if (category == null)
            throw new IllegalArgumentException("Category must not be null.");
        this.category = category;
    }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ── toString ─────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Note{id=" + getId() + ", userId=" + getUserId() +
               ", title='" + title + "', category=" + category + "}";
    }
}
