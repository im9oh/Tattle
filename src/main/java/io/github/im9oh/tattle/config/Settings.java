package io.github.im9oh.tattle.config;

import io.github.im9oh.tattle.model.RegexRule;
import io.github.im9oh.tattle.model.Severity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Immutable snapshot of the plugin configuration. Reload swaps the whole
 * object. Deliberately Bukkit-free so the same settings (and everything
 * downstream of them) run in the standalone simulator.
 */
public final class Settings {

    public record SpamRule(long windowMillis, int maxHits, double score, Severity severity) {}

    public record CapsRule(int minLength, double maxRatio, double score, Severity severity) {}

    public record WatchedEntry(double score, Severity severity) {}

    // Discord
    public final String webhookUrl;
    public final String webhookUsername;
    public final int batchIntervalSeconds;
    public final Severity minSeverity;

    // Reporting
    public final double reportThreshold;
    public final double attentionThreshold;
    public final double attentionHalfLifeSeconds;
    public final long cooldownMillis;
    public final boolean reportNewPlayers;
    public final boolean reportDeaths;
    public final boolean reportKicks;

    // Chat monitor
    public final boolean chatEnabled;
    public final SpamRule chatSpam;
    public final CapsRule chatCaps;

    // Command monitor
    public final boolean commandsEnabled;
    public final SpamRule commandSpam;
    public final Map<String, WatchedEntry> watchedCommands;

    // Console monitor
    public final boolean consoleEnabled;

    // Action monitor
    public final boolean actionsEnabled;
    public final SpamRule blockBreak;
    public final boolean gamemodeEnabled;
    public final WatchedEntry gamemodeChange;
    /** Keyed by normalized material name, e.g. "TNT", "LAVA_BUCKET". */
    public final Map<String, WatchedEntry> watchedItems;

    // Regex rules
    public final List<RegexRule> chatRules;
    public final List<RegexRule> commandRules;
    public final List<RegexRule> consoleRules;

    public Settings(Conf c, Logger log) {
        this.webhookUrl = c.getString("discord.webhook-url", "").trim();
        this.webhookUsername = c.getString("discord.username", "Tattle");
        this.batchIntervalSeconds = Math.max(2, c.getInt("discord.batch-interval-seconds", 10));
        this.minSeverity = Severity.parse(c.getString("discord.min-severity", null), Severity.LOW);

        this.reportThreshold = c.getDouble("reporting.report-threshold", 5.0);
        this.attentionThreshold = c.getDouble("reporting.attention-threshold", 12.0);
        this.attentionHalfLifeSeconds = Math.max(10.0, c.getDouble("reporting.attention-half-life-seconds", 300.0));
        this.cooldownMillis = Math.max(0, c.getInt("reporting.cooldown-seconds", 120)) * 1000L;
        this.reportNewPlayers = c.getBoolean("reporting.new-players", true);
        this.reportDeaths = c.getBoolean("reporting.deaths", false);
        this.reportKicks = c.getBoolean("reporting.kicks", true);

        this.chatEnabled = c.getBoolean("monitors.chat.enabled", true);
        this.chatSpam = spamRule(c, "monitors.chat.spam", "max-messages", 8, 6, 5.0, Severity.MEDIUM);
        this.chatCaps = new CapsRule(
                c.getInt("monitors.chat.caps.min-length", 12),
                c.getDouble("monitors.chat.caps.max-ratio", 0.8),
                c.getDouble("monitors.chat.caps.score", 1.5),
                Severity.parse(c.getString("monitors.chat.caps.severity", null), Severity.LOW));

        this.commandsEnabled = c.getBoolean("monitors.commands.enabled", true);
        this.commandSpam = spamRule(c, "monitors.commands.spam", "max-commands", 10, 12, 4.0, Severity.MEDIUM);
        this.watchedCommands = watchedMap(c, "monitors.commands.watched", key -> key.toLowerCase(Locale.ROOT));

        this.consoleEnabled = c.getBoolean("monitors.console.enabled", true);

        this.actionsEnabled = c.getBoolean("monitors.actions.enabled", true);
        this.blockBreak = spamRule(c, "monitors.actions.block-break", "max-blocks", 5, 50, 5.0, Severity.MEDIUM);
        this.gamemodeEnabled = c.getBoolean("monitors.actions.gamemode-change.enabled", true);
        this.gamemodeChange = new WatchedEntry(
                c.getDouble("monitors.actions.gamemode-change.score", 3.0),
                Severity.parse(c.getString("monitors.actions.gamemode-change.severity", null), Severity.MEDIUM));
        this.watchedItems = watchedMap(c, "monitors.actions.watched-items", Settings::normalizeMaterial);

        this.chatRules = List.copyOf(RegexRule.parseList(c.getMapList("rules.chat"), "rules.chat", log));
        this.commandRules = List.copyOf(RegexRule.parseList(c.getMapList("rules.commands"), "rules.commands", log));
        this.consoleRules = List.copyOf(RegexRule.parseList(c.getMapList("rules.console"), "rules.console", log));
    }

    /** "lava bucket" / "lava-bucket" / "LAVA_BUCKET" → "LAVA_BUCKET" */
    public static String normalizeMaterial(String name) {
        return name.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static SpamRule spamRule(Conf c, String path, String maxKey,
                                     int defWindow, int defMax, double defScore, Severity defSeverity) {
        return new SpamRule(
                Math.max(1, c.getInt(path + ".window-seconds", defWindow)) * 1000L,
                Math.max(1, c.getInt(path + "." + maxKey, defMax)),
                c.getDouble(path + ".score", defScore),
                Severity.parse(c.getString(path + ".severity", null), defSeverity));
    }

    private interface KeyNormalizer {
        String apply(String key);
    }

    private static Map<String, WatchedEntry> watchedMap(Conf c, String path, KeyNormalizer normalizer) {
        Map<String, WatchedEntry> map = new LinkedHashMap<>();
        for (String key : c.keys(path)) {
            double score = c.getDouble(path + "." + key + ".score", 3.0);
            Severity severity = Severity.parse(c.getString(path + "." + key + ".severity", null), Severity.MEDIUM);
            map.put(normalizer.apply(key), new WatchedEntry(score, severity));
        }
        return Collections.unmodifiableMap(map);
    }
}
