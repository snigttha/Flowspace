package com.flowspace.model;

/**
 * Data Transfer Object containing statistical averages for a user's wellness logs.
 * Handed off to Member 6 for dashboard metrics and insight generation.
 */
public class MoodAverages {
    private final int totalEntries;
    private final double avgSleep;
    private final double avgWater;
    private final double avgPain;

    public MoodAverages(int totalEntries, double avgSleep, double avgWater, double avgPain) {
        this.totalEntries = totalEntries;
        this.avgSleep = avgSleep;
        this.avgWater = avgWater;
        this.avgPain = avgPain;
    }

    public int getTotalEntries() {
        return totalEntries;
    }

    public double getAvgSleep() {
        return avgSleep;
    }

    public double getAvgWater() {
        return avgWater;
    }

    public double getAvgPain() {
        return avgPain;
    }

    @Override
    public String toString() {
        return String.format("MoodAverages[entries=%d, avgSleep=%.1fh, avgWater=%.1f cups, avgPain=%.1f/10]",
                totalEntries, avgSleep, avgWater, avgPain);
    }
}
