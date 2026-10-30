package com.flowspace.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe sliding-window rate limiter for brute-force mitigation.
 *
 * @author FlowSpace Security Lead
 */
public class RateLimiter {

    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public RateLimiter(int maxAttempts, long windowMillis) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowMillis;
    }

    /**
     * Checks if an action is allowed and records the attempt if allowed.
     */
    public synchronized boolean isAllowed(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        cleanup(timestamps, now);

        if (timestamps.size() >= maxAttempts) {
            return false;
        }
        timestamps.addLast(now);
        return true;
    }

    /**
     * Checks if the key is currently blocked due to exceeding max attempts.
     */
    public synchronized boolean isBlocked(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = attempts.get(key);
        if (timestamps == null) {
            return false;
        }
        cleanup(timestamps, now);
        return timestamps.size() >= maxAttempts;
    }

    /**
     * Records a failed attempt for the given key.
     */
    public synchronized void recordFailure(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        cleanup(timestamps, now);
        timestamps.addLast(now);
    }

    /**
     * Resets/clears the recorded attempts for the given key (e.g. after a successful login).
     */
    public synchronized void reset(String key) {
        attempts.remove(key);
    }

    private void cleanup(Deque<Long> timestamps, long now) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
            timestamps.pollFirst();
        }
    }
}
