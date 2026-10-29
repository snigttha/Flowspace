package com.flowspace.model;

/**
 * Task priority levels.
 *
 * @author Member 3 – Tasks module
 */
public enum Priority {
    LOW(1, "Low", "#10b981"),
    MEDIUM(2, "Medium", "#f59e0b"),
    HIGH(3, "High", "#ef4444");

    private final int rank;
    private final String displayName;
    private final String hexColor;

    Priority(int rank, String displayName, String hexColor) {
        this.rank = rank;
        this.displayName = displayName;
        this.hexColor = hexColor;
    }

    public int getRank() {
        return rank;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHexColor() {
        return hexColor;
    }

    public static Priority fromString(String s) {
        if (s == null || s.isBlank()) return MEDIUM;
        for (Priority p : values()) {
            if (p.name().equalsIgnoreCase(s.trim())) return p;
        }
        throw new IllegalArgumentException("Unknown priority: " + s);
    }
}
