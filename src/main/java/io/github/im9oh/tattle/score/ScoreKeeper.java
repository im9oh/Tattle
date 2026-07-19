package io.github.im9oh.tattle.score;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player "attention" scores with exponential decay. Scores accumulate from
 * small observations and fade over time, so sustained low-level misbehavior
 * surfaces without a single event ever crossing the report threshold.
 * Scores intentionally survive relogging.
 */
public final class ScoreKeeper {

    private static final class Entry {
        double score;
        long atMillis;
        String name;
    }

    private final Map<UUID, Entry> scores = new ConcurrentHashMap<>();

    /** Adds to the actor's decayed score and returns the new total. Thread-safe. */
    public double bump(UUID id, String name, double add, double halfLifeSeconds) {
        Entry entry = scores.computeIfAbsent(id, k -> new Entry());
        synchronized (entry) {
            long now = System.currentTimeMillis();
            entry.score = decayed(entry.score, entry.atMillis, now, halfLifeSeconds) + add;
            entry.atMillis = now;
            entry.name = name;
            return entry.score;
        }
    }

    public double current(UUID id, double halfLifeSeconds) {
        Entry entry = scores.get(id);
        if (entry == null) {
            return 0.0;
        }
        synchronized (entry) {
            return decayed(entry.score, entry.atMillis, System.currentTimeMillis(), halfLifeSeconds);
        }
    }

    public void reset(UUID id) {
        scores.remove(id);
    }

    /** Top attention scores (name → decayed score), highest first, entries below 0.1 omitted. */
    public List<Map.Entry<String, Double>> top(int limit, double halfLifeSeconds) {
        long now = System.currentTimeMillis();
        List<Map.Entry<String, Double>> all = new ArrayList<>();
        for (Map.Entry<UUID, Entry> e : scores.entrySet()) {
            Entry entry = e.getValue();
            synchronized (entry) {
                double value = decayed(entry.score, entry.atMillis, now, halfLifeSeconds);
                if (value >= 0.1) {
                    String name = entry.name != null ? entry.name : e.getKey().toString();
                    all.add(Map.entry(name, value));
                }
            }
        }
        all.sort(Comparator.comparingDouble(Map.Entry<String, Double>::getValue).reversed());
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    private static double decayed(double score, long atMillis, long now, double halfLifeSeconds) {
        if (score <= 0.0 || atMillis <= 0) {
            return 0.0;
        }
        double dtSeconds = (now - atMillis) / 1000.0;
        return score * Math.pow(0.5, dtSeconds / halfLifeSeconds);
    }
}
