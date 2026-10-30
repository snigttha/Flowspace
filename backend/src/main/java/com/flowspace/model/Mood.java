package com.flowspace.model;

/**
 * Enumeration of supported mood states in FlowSpace.
 * Each mood maps to a 1-5 numerical score used in Member 6 trend analytics.
 */
public enum Mood {
    HAPPY("Happy", 5, "😊"),
    CALM("Calm", 4, "😌"),
    NEUTRAL("Neutral", 3, "😐"),
    TIRED("Tired", 2, "😴"),
    ANXIOUS("Anxious", 2, "😰"),
    IRRITABLE("Irritable", 2, "😣"),
    SAD("Sad", 1, "😢");

    private final String displayName;
    private final int score;
    private final String emoji;

    Mood(String displayName, int score, String emoji) {
        this.displayName = displayName;
        this.score = score;
        this.emoji = emoji;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getScore() {
        return score;
    }

    public String getEmoji() {
        return emoji;
    }

    /**
     * Case-insensitive lookup helper from string value.
     */
    public static Mood fromString(String val) {
        if (val == null || val.trim().isEmpty()) {
            return null;
        }
        String clean = val.trim().toUpperCase();
        for (Mood m : values()) {
            if (m.name().equals(clean) || m.displayName.equalsIgnoreCase(clean)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown mood: " + val);
    }
}
