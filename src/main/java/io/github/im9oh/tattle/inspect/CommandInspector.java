package io.github.im9oh.tattle.inspect;

import io.github.im9oh.tattle.TattleContext;
import io.github.im9oh.tattle.config.Settings;
import io.github.im9oh.tattle.model.Observation;
import io.github.im9oh.tattle.model.RegexRule;
import io.github.im9oh.tattle.model.SourceType;
import io.github.im9oh.tattle.score.RateTracker;
import io.github.im9oh.tattle.score.ScoringEngine;

import java.util.Locale;
import java.util.UUID;

/**
 * Inspects one command line (player or console): the watched-command list and
 * regex rules. Player command spam is tracked separately via trackSpam.
 * Shared between the Bukkit command monitor and the simulator. Thread-safe.
 */
public final class CommandInspector {

    private final TattleContext ctx;
    private final ScoringEngine engine;
    private final RateTracker spamTracker = new RateTracker();

    public CommandInspector(TattleContext ctx, ScoringEngine engine) {
        this.ctx = ctx;
        this.engine = engine;
    }

    /** actorId is null for console-issued commands. */
    public void inspect(String actorName, UUID actorId, String commandLine) {
        Settings settings = ctx.settings();
        String root = rootCommand(commandLine);

        Settings.WatchedEntry watched = settings.watchedCommands.get(root);
        if (watched != null) {
            engine.observe(Observation.of(SourceType.COMMAND, actorName, actorId,
                    "command/" + root,
                    "Watched command used: /" + root,
                    commandLine, watched.score(), watched.severity()));
        }

        for (RegexRule rule : settings.commandRules) {
            if (rule.pattern().matcher(commandLine).find()) {
                engine.observe(Observation.of(SourceType.COMMAND, actorName, actorId,
                        "command-rule/" + rule.name(),
                        "Command matched rule '" + rule.name() + "'",
                        commandLine, rule.score(), rule.severity()));
            }
        }
    }

    public void trackSpam(String actorName, UUID actorId, String commandLine) {
        Settings.SpamRule spam = ctx.settings().commandSpam;
        int hits = spamTracker.hit(actorId, spam.windowMillis());
        if (hits == spam.maxHits() + 1) {
            engine.observe(Observation.of(SourceType.COMMAND, actorName, actorId,
                    "command/spam",
                    "Command spam: more than " + spam.maxHits() + " commands in " + spam.windowMillis() / 1000 + "s",
                    "Latest command: " + commandLine, spam.score(), spam.severity()));
        }
    }

    /** "/minecraft:op Steve" → "op" */
    public static String rootCommand(String commandLine) {
        String stripped = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        int space = stripped.indexOf(' ');
        String root = (space >= 0 ? stripped.substring(0, space) : stripped).toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        return colon >= 0 ? root.substring(colon + 1) : root;
    }

    public void clear(UUID actorId) {
        spamTracker.clear(actorId);
    }
}
