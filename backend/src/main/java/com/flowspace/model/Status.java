package com.flowspace.model;

/**
 * Task lifecycle status with state machine enforcement.
 * Done can only be reopened to To Do.
 *
 * @author Member 3 – Tasks module
 */
public enum Status {
    TODO("To Do"),
    IN_PROGRESS("In Progress"),
    DONE("Done");

    private final String label;

    Status(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Enforces the project workflow rule:
     * - TODO -> IN_PROGRESS or DONE
     * - IN_PROGRESS -> TODO or DONE
     * - DONE -> TODO only (Reopen)
     *
     * @param target The target status
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(Status target) {
        if (target == null) return false;
        if (this == target) return true;

        if (this == DONE) {
            return target == TODO;
        }

        return true;
    }

    public static Status fromString(String s) {
        if (s == null || s.isBlank()) return TODO;
        String normalized = s.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        for (Status st : values()) {
            if (st.name().equals(normalized)) return st;
        }
        throw new IllegalArgumentException("Unknown status: " + s);
    }
}
