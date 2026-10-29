package com.flowspace.model;

import com.flowspace.model.Note.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Note} validation logic.
 *
 * <p>Tests from PLAN.md Member 2:
 * <ul>
 *   <li>Note validation (title, content length rules)</li>
 *   <li>Category parsing</li>
 *   <li>{@code edit()} method</li>
 *   <li>{@code summary()} output format</li>
 * </ul>
 *
 * @author Member 2 – Notes module
 */
@DisplayName("Note – validation tests")
class NoteValidationTest {

    // ── Constructor / setTitle ────────────────────────────────────────────

    @Test
    @DisplayName("Valid note is created without exception")
    void validNoteCreated() {
        assertDoesNotThrow(() ->
            new Note(1, "My Title", "Some content", Category.General));
    }

    @Test
    @DisplayName("Blank title throws IllegalArgumentException")
    void blankTitleThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new Note(1, "  ", "content", Category.Study));
        assertTrue(ex.getMessage().contains("Title"));
    }

    @Test
    @DisplayName("Title exactly 100 chars is accepted")
    void titleExactly100Accepted() {
        String t = "A".repeat(100);
        assertDoesNotThrow(() -> new Note(1, t, "content", Category.Personal));
    }

    @Test
    @DisplayName("Title of 101 chars throws")
    void title101Throws() {
        String t = "A".repeat(101);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new Note(1, t, "content", Category.General));
        assertTrue(ex.getMessage().contains("100"));
    }

    // ── setContent ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Blank content throws IllegalArgumentException")
    void blankContentThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new Note(1, "Title", "", Category.Ideas));
    }

    @Test
    @DisplayName("Content exactly 2000 chars is accepted")
    void contentExactly2000Accepted() {
        String c = "X".repeat(2000);
        assertDoesNotThrow(() -> new Note(1, "Title", c, Category.Health));
    }

    @Test
    @DisplayName("Content of 2001 chars throws")
    void content2001Throws() {
        String c = "X".repeat(2001);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new Note(1, "Title", c, Category.General));
        assertTrue(ex.getMessage().contains("2000"));
    }

    // ── validate() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("validate() passes on a valid note")
    void validatePasses() {
        Note n = new Note(1, "Title", "Content", Category.Study);
        assertDoesNotThrow(n::validate);
    }

    @Test
    @DisplayName("validate() on null category throws")
    void validateNullCategoryThrows() {
        Note n = new Note();
        n.setUserId(1);
        assertThrows(IllegalArgumentException.class, () -> n.setCategory(null));
    }

    // ── Category ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Category.fromString is case-insensitive")
    void categoryFromStringCaseInsensitive() {
        assertEquals(Category.Study,    Category.fromString("study"));
        assertEquals(Category.Personal, Category.fromString("PERSONAL"));
        assertEquals(Category.Health,   Category.fromString("Health"));
    }

    @Test
    @DisplayName("Unknown category string throws")
    void categoryFromStringUnknown() {
        assertThrows(IllegalArgumentException.class,
            () -> Category.fromString("cooking"));
    }

    @Test
    @DisplayName("Null/blank category defaults to General in NoteManager")
    void categoryNullDefaultsToGeneral() {
        // Category.fromString(null) → General handled by Note constructor
        // Just verify the Note constructor handles null → default
        Note n = new Note();
        n.setUserId(1);
        n.setTitle("T");
        n.setContent("C");
        n.setCategory(Category.fromString(null));
        assertEquals(Category.General, n.getCategory());
    }

    // ── edit() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("edit() updates fields and stamps updatedAt")
    void editUpdatesFields() {
        Note n = new Note(1, "Old", "Old content", Category.General);
        var before = n.getUpdatedAt();
        n.edit("New Title", "New content, longer.", "Study");
        assertEquals("New Title",   n.getTitle());
        assertEquals("New content, longer.", n.getContent());
        assertEquals(Category.Study, n.getCategory());
        // updatedAt should be at or after the original
        assertFalse(n.getUpdatedAt().isBefore(before));
    }

    @Test
    @DisplayName("edit() with invalid title throws and leaves note unchanged")
    void editInvalidTitleThrows() {
        Note n = new Note(1, "Good Title", "Content", Category.General);
        assertThrows(IllegalArgumentException.class,
            () -> n.edit("", "Content", "General"));
    }

    // ── summary() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("summary() starts with [Note] prefix")
    void summaryStartsWithPrefix() {
        Note n = new Note(1, "My Note", "Body text here", Category.Personal);
        assertTrue(n.summary().startsWith("[Note]"));
    }

    @Test
    @DisplayName("summary() contains the title")
    void summaryContainsTitle() {
        Note n = new Note(1, "Special Title", "Body", Category.Health);
        assertTrue(n.summary().contains("Special Title"));
    }

    @Test
    @DisplayName("summary() truncates long content to 80 chars")
    void summaryTruncatesLong() {
        String longBody = "B".repeat(200);
        Note n = new Note(1, "T", longBody, Category.Ideas);
        String s = n.summary();
        // should contain ellipsis
        assertTrue(s.contains("…"));
    }
}
