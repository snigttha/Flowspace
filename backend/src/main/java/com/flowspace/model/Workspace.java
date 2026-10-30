package com.flowspace.model;

import com.flowspace.dao.MoodEntryDAO;
import com.flowspace.dao.NoteDAO;
import com.flowspace.dao.PeriodLogDAO;
import com.flowspace.dao.TaskDAO;
import com.flowspace.model.AnalyticsData.*;
import com.flowspace.model.MoodEntry.Mood;
import com.flowspace.model.Note.Category;
import com.flowspace.model.Task.Priority;
import com.flowspace.service.AnalyticsService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Personal Workspace aggregate for an authenticated user.
 *
 * <p>Encapsulates all domain items belonging to a single user:
 * <ul>
 *   <li>Notes (via {@link NoteDAO})</li>
 *   <li>Tasks (via {@link TaskDAO})</li>
 *   <li>Cycle logs (via {@link PeriodLogDAO})</li>
 *   <li>Mood / wellness entries (via {@link MoodEntryDAO})</li>
 * </ul>
 *
 * <p>Enforces strict user isolation: all operations and queries are bound
 * to the authenticated {@code userId} assigned during instantiation.
 *
 * @author Member 6 (Workspace & Integration Lead)
 */
public class Workspace {

    private final int userId;
    private final NoteDAO noteDAO;
    private final TaskDAO taskDAO;
    private final PeriodLogDAO periodLogDAO;
    private final MoodEntryDAO moodEntryDAO;
    private final AnalyticsService analyticsService;

    /**
     * Creates a Workspace for the given user with default DAOs and AnalyticsService.
     *
     * @param userId authenticated user id (must be > 0)
     */
    public Workspace(int userId) {
        this(userId, new NoteDAO(), new TaskDAO(), new PeriodLogDAO(), new MoodEntryDAO(), new AnalyticsService());
    }

    /**
     * Dependency-injected constructor for testing and customization.
     */
    public Workspace(int userId, NoteDAO noteDAO, TaskDAO taskDAO,
                     PeriodLogDAO periodLogDAO, MoodEntryDAO moodEntryDAO,
                     AnalyticsService analyticsService) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        this.userId = userId;
        this.noteDAO = Objects.requireNonNull(noteDAO, "noteDAO must not be null");
        this.taskDAO = Objects.requireNonNull(taskDAO, "taskDAO must not be null");
        this.periodLogDAO = Objects.requireNonNull(periodLogDAO, "periodLogDAO must not be null");
        this.moodEntryDAO = Objects.requireNonNull(moodEntryDAO, "moodEntryDAO must not be null");
        this.analyticsService = Objects.requireNonNull(analyticsService, "analyticsService must not be null");
    }

    public int getUserId() {
        return userId;
    }

    // ── Member 6 contract methods ────────────────────────────────────────

    /**
     * Adds a note to this workspace for the authenticated user.
     *
     * @param title    title (1..100 chars)
     * @param content  content (1..2000 chars)
     * @param category category name (General, Study, Personal, Ideas, Health)
     * @return persisted Note with db-assigned id and timestamps
     */
    public Note addNote(String title, String content, String category) throws SQLException {
        Category cat = Category.fromString(category);
        Note note = new Note(userId, title, content, cat);
        return noteDAO.create(note);
    }

    /**
     * Adds a note object to this workspace, ensuring it is assigned to this user.
     */
    public Note addNote(Note note) throws SQLException {
        if (note == null) {
            throw new IllegalArgumentException("Note cannot be null");
        }
        note.setUserId(this.userId);
        return noteDAO.create(note);
    }

    /**
     * Adds a task to this workspace for the authenticated user.
     *
     * @param title    title (3..100 chars)
     * @param dueDate  valid due date
     * @param priority LOW, MEDIUM, or HIGH
     * @return persisted Task with db-assigned id and status TODO
     */
    public Task addTask(String title, LocalDate dueDate, Priority priority) throws SQLException {
        Task task = new Task(userId, title, dueDate, priority);
        return taskDAO.create(task);
    }

    /**
     * Adds a task object to this workspace, ensuring it is assigned to this user.
     */
    public Task addTask(Task task) throws SQLException {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        task.setUserId(this.userId);
        return taskDAO.create(task);
    }

    /**
     * Logs a menstrual cycle for the authenticated user.
     */
    public PeriodLog addCycle(LocalDate startDate, int length) throws SQLException {
        PeriodLog log = new PeriodLog(userId, startDate, length);
        return periodLogDAO.create(log);
    }

    /**
     * Logs a daily mood / wellness entry for the authenticated user.
     */
    public MoodEntry addMood(LocalDate date, Mood mood, String symptoms,
                             int painLevel, double sleepHours, int waterIntake) throws SQLException {
        MoodEntry entry = new MoodEntry(userId, date, mood, symptoms, painLevel, sleepHours, waterIntake);
        return moodEntryDAO.create(entry);
    }

    /**
     * Returns complete analytics and dashboard metrics for the authenticated user.
     * Fetches all real records from the database and runs the analytics engine.
     *
     * @return AnalyticsData container with counts, stats, mood trends, recent activity, and insights
     */
    public AnalyticsData getAnalytics() throws SQLException {
        List<Note> notes = noteDAO.findAll(userId);
        List<Task> tasks = taskDAO.findAll(userId);
        List<PeriodLog> cycles = periodLogDAO.findAll(userId);
        List<MoodEntry> moods = moodEntryDAO.findAll(userId);

        AnalyticsData data = new AnalyticsData(userId);

        // Compute statistics
        TaskStats taskStats = analyticsService.computeTaskStats(tasks);
        CycleStats cycleStats = analyticsService.computeCycleStats(cycles);
        List<MoodTrendPoint> moodTrends = analyticsService.computeMoodTrends(moods);
        List<String> insights = analyticsService.generateInsights(taskStats, cycleStats, moods);

        // Dashboard counts
        int totalNotes = notes.size();
        int totalTasks = tasks.size();
        int openTasks = taskStats.getPending();
        int totalMoods = moods.size();
        int totalCycles = cycles.size();
        DashboardCounts counts = new DashboardCounts(totalNotes, totalTasks, openTasks, totalMoods, totalCycles);

        // Assemble recent activity (sorted newest first, limited to top 5)
        List<RecentActivity> activities = new ArrayList<>();

        for (Note n : notes) {
            LocalDateTime ts = n.getUpdatedAt() != null ? n.getUpdatedAt() : n.getCreatedAt();
            if (ts == null) ts = LocalDateTime.now();
            activities.add(new RecentActivity(
                "NOTE",
                "Note: " + n.getTitle(),
                ts,
                ts.toLocalDate().toString()
            ));
        }

        for (Task t : tasks) {
            LocalDateTime ts = t.getCreatedAt() != null ? t.getCreatedAt() : LocalDateTime.now();
            activities.add(new RecentActivity(
                "TASK",
                "Task: " + t.getTitle(),
                ts,
                ts.toLocalDate().toString()
            ));
        }

        for (MoodEntry m : moods) {
            LocalDate d = m.getEntryDate() != null ? m.getEntryDate() : LocalDate.now();
            LocalDateTime ts = d.atTime(12, 0);
            activities.add(new RecentActivity(
                "WELLNESS",
                "Wellness log for " + d,
                ts,
                d.toString()
            ));
        }

        for (PeriodLog p : cycles) {
            LocalDate d = p.getCycleStartDate() != null ? p.getCycleStartDate() : LocalDate.now();
            LocalDateTime ts = d.atTime(8, 0);
            activities.add(new RecentActivity(
                "CYCLE",
                "Cycle started: " + d,
                ts,
                d.toString()
            ));
        }

        activities.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        if (activities.size() > 5) {
            activities = activities.subList(0, 5);
        }

        data.setCounts(counts);
        data.setTaskStats(taskStats);
        data.setCycleStats(cycleStats);
        data.setMoodTrends(moodTrends);
        data.setRecentActivity(activities);
        data.setInsights(insights);

        return data;
    }

    // ── Polymorphic query support for PLAN.md ─────────────────────────────

    /**
     * Returns all workspace items belonging to this user polymorphically.
     * Demonstrates OOP polymorphism by collecting Notes, Tasks, PeriodLogs,
     * and MoodEntries into a single list of {@link WorkspaceItem}.
     */
    public List<WorkspaceItem> getAllItems() throws SQLException {
        List<WorkspaceItem> all = new ArrayList<>();
        all.addAll(noteDAO.findAll(userId));
        all.addAll(taskDAO.findAll(userId));
        all.addAll(periodLogDAO.findAll(userId));
        all.addAll(moodEntryDAO.findAll(userId));
        return all;
    }

    public List<Note> getNotes() throws SQLException {
        return noteDAO.findAll(userId);
    }

    public List<Task> getTasks() throws SQLException {
        return taskDAO.findAll(userId);
    }

    public List<PeriodLog> getCycles() throws SQLException {
        return periodLogDAO.findAll(userId);
    }

    public List<MoodEntry> getMoods() throws SQLException {
        return moodEntryDAO.findAll(userId);
    }
}

