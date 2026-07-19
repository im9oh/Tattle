package io.github.im9oh.tattle.score;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sliding-window event counter per player. Thread-safe. */
public final class RateTracker {

    private final Map<UUID, Deque<Long>> windows = new ConcurrentHashMap<>();

    /** Records a hit and returns how many hits fall inside the window, including this one. */
    public int hit(UUID id, long windowMillis) {
        Deque<Long> window = windows.computeIfAbsent(id, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (window) {
            long cutoff = now - windowMillis;
            while (!window.isEmpty() && window.peekFirst() < cutoff) {
                window.pollFirst();
            }
            window.addLast(now);
            return window.size();
        }
    }

    public void clear(UUID id) {
        windows.remove(id);
    }
}
