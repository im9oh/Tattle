package io.github.im9oh.tattle.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A single observed event: who did what, how notable it is, and why.
 * Observations are immutable and safe to pass between threads.
 *
 * @param actorId null for non-player actors (console / server)
 */
public record Observation(
        SourceType source,
        String actorName,
        UUID actorId,
        String category,
        String summary,
        String detail,
        double score,
        Severity severity,
        Instant timestamp) {

    public static Observation of(SourceType source, String actorName, UUID actorId, String category,
                                 String summary, String detail, double score, Severity severity) {
        return new Observation(source, actorName, actorId, category, summary, detail, score, severity, Instant.now());
    }

    /** Stable key identifying the actor for cooldown/attention bookkeeping. */
    public String actorKey() {
        return actorId != null ? actorId.toString() : actorName;
    }
}
