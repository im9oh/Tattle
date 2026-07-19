package io.github.im9oh.tattle.monitor;

import io.github.im9oh.tattle.TattlePlugin;
import io.github.im9oh.tattle.config.Settings;
import io.github.im9oh.tattle.model.Observation;
import io.github.im9oh.tattle.model.RegexRule;
import io.github.im9oh.tattle.model.SourceType;
import io.github.im9oh.tattle.score.RateTracker;
import io.github.im9oh.tattle.score.ScoringEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.UUID;

/**
 * Observes commands run by players and by the console: a configurable watchlist
 * (op, ban, give, ...), regex rules over the full command line, and spam bursts.
 */
public final class CommandMonitor implements Listener {

    private final TattlePlugin plugin;
    private final ScoringEngine engine;
    private final RateTracker spamTracker = new RateTracker();

    public CommandMonitor(TattlePlugin plugin, ScoringEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Settings settings = plugin.settings();
        if (!settings.commandsEnabled) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.isExempt(player.getUniqueId())) {
            return;
        }
        inspect(settings, player.getName(), player.getUniqueId(), event.getMessage());

        Settings.SpamRule spam = settings.commandSpam;
        int hits = spamTracker.hit(player.getUniqueId(), spam.windowMillis());
        if (hits == spam.maxHits() + 1) {
            engine.observe(Observation.of(SourceType.COMMAND, player.getName(), player.getUniqueId(),
                    "command/spam",
                    "Command spam: more than " + spam.maxHits() + " commands in " + spam.windowMillis() / 1000 + "s",
                    "Latest command: " + event.getMessage(), spam.score(), spam.severity()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        Settings settings = plugin.settings();
        if (!settings.commandsEnabled) {
            return;
        }
        inspect(settings, event.getSender().getName(), null, "/" + event.getCommand());
    }

    private void inspect(Settings settings, String actorName, UUID actorId, String commandLine) {
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

    /** "/minecraft:op Steve" → "op" */
    private static String rootCommand(String commandLine) {
        String stripped = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        int space = stripped.indexOf(' ');
        String root = (space >= 0 ? stripped.substring(0, space) : stripped).toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        return colon >= 0 ? root.substring(colon + 1) : root;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        spamTracker.clear(event.getPlayer().getUniqueId());
    }
}
