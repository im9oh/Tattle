package io.github.im9oh.tattle.inspect;

import io.github.im9oh.tattle.TattleContext;
import io.github.im9oh.tattle.config.Settings;
import io.github.im9oh.tattle.model.Observation;
import io.github.im9oh.tattle.model.SourceType;
import io.github.im9oh.tattle.score.RateTracker;
import io.github.im9oh.tattle.score.ScoringEngine;

import java.util.Locale;
import java.util.UUID;

/**
 * Inspects in-world actions: rapid block breaking, watched-item placement/use,
 * gamemode changes. Shared between the Bukkit action monitor and the simulator.
 * Thread-safe.
 */
public final class ActionInspector {

    private final TattleContext ctx;
    private final ScoringEngine engine;
    private final RateTracker breakTracker = new RateTracker();

    public ActionInspector(TattleContext ctx, ScoringEngine engine) {
        this.ctx = ctx;
        this.engine = engine;
    }

    public void blockBreak(String actorName, UUID actorId, String materialName, String where) {
        Settings settings = ctx.settings();
        if (settings.isIgnoredBreakBlock(materialName)) {
            return; // e.g. tree blocks: TreeFeller-style mass log breaks are legitimate
        }
        Settings.SpamRule rule = settings.blockBreak;
        int hits = breakTracker.hit(actorId, rule.windowMillis());
        if (hits == rule.maxHits() + 1) {
            engine.observe(Observation.of(SourceType.ACTION, actorName, actorId,
                    "action/rapid-break",
                    "Rapid block breaking: more than " + rule.maxHits() + " blocks in " + rule.windowMillis() / 1000 + "s",
                    "Last block: " + materialName + " at " + where,
                    rule.score(), rule.severity()));
        }
    }

    /** No-op unless materialName is in the watched-items list. */
    public void watchedItem(String actorName, UUID actorId, String verb, String materialName, String where) {
        String key = Settings.normalizeMaterial(materialName);
        Settings.WatchedEntry watched = ctx.settings().watchedItems.get(key);
        if (watched == null) {
            return;
        }
        String display = key.toLowerCase(Locale.ROOT).replace('_', ' ');
        engine.observe(Observation.of(SourceType.ACTION, actorName, actorId,
                "action/item-" + key.toLowerCase(Locale.ROOT),
                verb + " " + display,
                verb + " " + display + " at " + where,
                watched.score(), watched.severity()));
    }

    public void gamemodeChange(String actorName, UUID actorId, String fromMode, String toMode) {
        Settings settings = ctx.settings();
        if (!settings.gamemodeEnabled) {
            return;
        }
        engine.observe(Observation.of(SourceType.ACTION, actorName, actorId,
                "action/gamemode",
                "Gamemode changed to " + toMode,
                actorName + " switched from " + fromMode + " to " + toMode,
                settings.gamemodeChange.score(), settings.gamemodeChange.severity()));
    }

    public void clear(UUID actorId) {
        breakTracker.clear(actorId);
    }
}
