package io.github.im9oh.tattle.score;

import io.github.im9oh.tattle.TattlePlugin;
import io.github.im9oh.tattle.config.Settings;
import io.github.im9oh.tattle.model.Observation;
import io.github.im9oh.tattle.model.SourceType;
import io.github.im9oh.tattle.report.ReportManager;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Decides which observations are worth telling staff about.
 *
 * Two paths to a report:
 *  - a single observation scores at or above reporting.report-threshold, or
 *  - the actor's decaying attention score crosses reporting.attention-threshold.
 *
 * Tattle never acts on what it sees — reporting is the only outcome.
 */
public final class ScoringEngine {

    private final TattlePlugin plugin;
    private final ReportManager reports;
    private final ScoreKeeper keeper = new ScoreKeeper();
    private final Map<SourceType, AtomicLong> observed = new EnumMap<>(SourceType.class);

    public ScoringEngine(TattlePlugin plugin, ReportManager reports) {
        this.plugin = plugin;
        this.reports = reports;
        for (SourceType type : SourceType.values()) {
            observed.put(type, new AtomicLong());
        }
    }

    /** Scored path: report immediately if notable, otherwise accumulate attention. May be called from any thread. */
    public void observe(Observation obs) {
        observed.get(obs.source()).incrementAndGet();
        Settings settings = plugin.settings();

        boolean report = obs.score() >= settings.reportThreshold;
        String note = null;

        if (obs.actorId() != null) {
            double total = keeper.bump(obs.actorId(), obs.actorName(), obs.score(), settings.attentionHalfLifeSeconds);
            if (!report && total >= settings.attentionThreshold) {
                report = true;
                note = String.format("Attention score reached %.1f (threshold %.1f) from repeated low-level activity. Score has been reset.",
                        total, settings.attentionThreshold);
                keeper.reset(obs.actorId());
            }
        }

        if (report) {
            reports.report(obs, note);
        }
    }

    /** Informational path: always reported (still subject to cooldowns and the Discord severity filter). */
    public void observeDirect(Observation obs) {
        observed.get(obs.source()).incrementAndGet();
        reports.report(obs, null);
    }

    public ScoreKeeper keeper() {
        return keeper;
    }

    public long observedCount(SourceType type) {
        return observed.get(type).get();
    }
}
