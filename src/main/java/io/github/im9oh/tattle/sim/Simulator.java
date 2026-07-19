package io.github.im9oh.tattle.sim;

import io.github.im9oh.tattle.TattleContext;
import io.github.im9oh.tattle.config.MapConf;
import io.github.im9oh.tattle.config.Settings;
import io.github.im9oh.tattle.inspect.ActionInspector;
import io.github.im9oh.tattle.inspect.ChatInspector;
import io.github.im9oh.tattle.inspect.CommandInspector;
import io.github.im9oh.tattle.inspect.ConsoleInspector;
import io.github.im9oh.tattle.model.Observation;
import io.github.im9oh.tattle.model.Severity;
import io.github.im9oh.tattle.model.SourceType;
import io.github.im9oh.tattle.report.DiscordWebhook;
import io.github.im9oh.tattle.report.ReportManager;
import io.github.im9oh.tattle.score.ScoringEngine;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Standalone harness to test Tattle without a Minecraft server.
 *
 * Runs the exact same pipeline as the plugin — same config file, same rules,
 * same scoring, same cooldowns, same Discord delivery — fed with synthetic
 * events typed on stdin or read from a script file.
 *
 * Run from the project root:
 *   mvn -q compile exec:java
 *   mvn -q compile exec:java -Dexec.args="--script sim-demo.txt"
 *   mvn -q compile exec:java -Dexec.args="--config plugins/Tattle/config.yml --webhook https://discord.com/api/webhooks/..."
 */
public final class Simulator implements TattleContext {

    private final Logger logger;
    private volatile Settings settings;
    private final ScoringEngine engine;
    private final ReportManager reports;
    private final DiscordWebhook webhook;
    private final ChatInspector chat;
    private final CommandInspector commands;
    private final ConsoleInspector console;
    private final ActionInspector actions;
    private final PrintStream out = System.out;

    private Simulator(Settings settings) {
        this.logger = makeLogger();
        this.settings = settings;
        this.webhook = new DiscordWebhook(this);
        this.reports = new ReportManager(this, webhook);
        this.engine = new ScoringEngine(this, reports);
        this.chat = new ChatInspector(this, engine);
        this.commands = new CommandInspector(this, engine);
        this.console = new ConsoleInspector(this, engine);
        this.actions = new ActionInspector(this, engine);
    }

    @Override
    public Settings settings() {
        return settings;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    public static void main(String[] args) throws Exception {
        String configPath = null;
        String webhookOverride = null;
        String scriptPath = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> configPath = args[++i];
                case "--webhook" -> webhookOverride = args[++i];
                case "--script" -> scriptPath = args[++i];
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    System.err.println("Usage: Simulator [--config config.yml] [--webhook URL] [--script events.txt]");
                    return;
                }
            }
        }

        Map<?, ?> raw = loadConfig(configPath);
        if (webhookOverride != null) {
            raw = withWebhook(raw, webhookOverride);
        }
        Settings settings = new Settings(new MapConf(raw), Logger.getLogger("TattleSim"));
        Simulator sim = new Simulator(settings);
        sim.run(scriptPath);
    }

    private void run(String scriptPath) throws Exception {
        out.println("── Tattle simulator ──");
        out.println("Same rules, scoring, cooldowns and Discord delivery as in-game; no server needed.");
        out.printf("report-threshold %.1f · attention-threshold %.1f (half-life %.0fs) · cooldown %ds%n",
                settings.reportThreshold, settings.attentionThreshold,
                settings.attentionHalfLifeSeconds, settings.cooldownMillis / 1000);
        out.println("Discord webhook: " + (settings.webhookUrl.isEmpty()
                ? "not configured — reports print here only"
                : "configured — reports at severity " + settings.minSeverity + "+ will actually be sent"));
        out.println("Type 'help' for commands." + (scriptPath != null ? " Running script: " + scriptPath : ""));
        out.println();

        ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tattle-sim-flush");
            t.setDaemon(true);
            return t;
        });
        flusher.scheduleAtFixedRate(webhook::flush, settings.batchIntervalSeconds, settings.batchIntervalSeconds, TimeUnit.SECONDS);

        try {
            if (scriptPath != null) {
                for (String line : Files.readAllLines(Path.of(scriptPath), StandardCharsets.UTF_8)) {
                    if (!line.isBlank() && !line.trim().startsWith("#")) {
                        out.println("> " + line.trim());
                        if (!handle(line.trim())) {
                            break;
                        }
                    }
                }
            } else {
                BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.isBlank() || line.trim().startsWith("#")) {
                        continue;
                    }
                    if (!handle(line.trim())) {
                        break;
                    }
                }
            }
        } finally {
            // Final delivery of anything still queued before exiting.
            webhook.flush();
            flusher.shutdownNow();
            out.println();
            status();
        }
    }

    /** Returns false to quit. */
    private boolean handle(String line) {
        String[] parts = line.split("\\s+", 3);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        try {
            switch (cmd) {
                case "chat" -> chat.inspect(parts[1], uuid(parts[1]), parts[2]);
                case "cmd" -> {
                    String commandLine = parts[2].startsWith("/") ? parts[2] : "/" + parts[2];
                    commands.inspect(parts[1], uuid(parts[1]), commandLine);
                    commands.trackSpam(parts[1], uuid(parts[1]), commandLine);
                }
                case "concmd" -> {
                    String rest = line.substring(cmd.length()).trim();
                    commands.inspect("CONSOLE", null, rest.startsWith("/") ? rest : "/" + rest);
                }
                case "log" -> console.inspect(line.substring(cmd.length()).trim(), "INFO");
                case "break" -> {
                    int count = parts.length >= 3 ? Integer.parseInt(parts[2]) : 1;
                    for (int i = 0; i < count; i++) {
                        actions.blockBreak(parts[1], uuid(parts[1]), "STONE at world (0, 64, 0)");
                    }
                }
                case "use", "place" -> actions.watchedItem(parts[1], uuid(parts[1]),
                        cmd.equals("place") ? "Placed" : "Used", parts[2], "world (0, 64, 0)");
                case "gamemode" -> actions.gamemodeChange(parts[1], uuid(parts[1]), "SURVIVAL",
                        parts[2].toUpperCase(Locale.ROOT));
                case "join" -> engine.observeDirect(Observation.of(SourceType.ACTION, parts[1], uuid(parts[1]),
                        "player/new-join", "New player joined for the first time",
                        parts[1] + " joined for the first time.", 0.0, Severity.LOW));
                case "kick" -> engine.observeDirect(Observation.of(SourceType.ACTION, parts[1], uuid(parts[1]),
                        "player/kick", "Player was kicked",
                        "Reason: " + (parts.length >= 3 ? parts[2] : "unspecified"), 0.0, Severity.MEDIUM));
                case "death" -> engine.observeDirect(Observation.of(SourceType.ACTION, parts[1], uuid(parts[1]),
                        "player/death", "Player died",
                        parts.length >= 3 ? parts[2] : parts[1] + " died.", 0.0, Severity.LOW));
                case "score" -> {
                    if (parts.length >= 2) {
                        out.printf("attention %s: %.1f (report at %.1f)%n", parts[1],
                                engine.keeper().current(uuid(parts[1]), settings.attentionHalfLifeSeconds),
                                settings.attentionThreshold);
                    } else {
                        List<Map.Entry<String, Double>> top = engine.keeper().top(10, settings.attentionHalfLifeSeconds);
                        if (top.isEmpty()) {
                            out.println("no attention scores — all quiet");
                        }
                        for (Map.Entry<String, Double> e : top) {
                            out.printf("attention %s: %.1f%n", e.getKey(), e.getValue());
                        }
                    }
                }
                case "status" -> status();
                case "recent" -> reports.recentReports().forEach(out::println);
                case "sleep" -> Thread.sleep(Long.parseLong(parts[1]));
                case "flush" -> webhook.flush();
                case "help" -> help();
                case "quit", "exit" -> {
                    return false;
                }
                default -> out.println("Unknown command '" + cmd + "' — type 'help'.");
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            out.println("Missing arguments for '" + cmd + "' — type 'help'.");
        } catch (NumberFormatException e) {
            out.println("Expected a number: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    private void status() {
        out.printf("observed — chat %d, commands %d, console %d, actions %d%n",
                engine.observedCount(SourceType.CHAT), engine.observedCount(SourceType.COMMAND),
                engine.observedCount(SourceType.CONSOLE), engine.observedCount(SourceType.ACTION));
        out.printf("reports — %d sent, %d suppressed by cooldown · webhook — queue %d, delivered %d, dropped %d%n",
                reports.sentCount(), reports.suppressedCount(),
                webhook.queueSize(), webhook.deliveredCount(), webhook.failedCount());
    }

    private void help() {
        out.println("""
                Events:
                  chat <player> <message...>       simulate a chat message
                  cmd <player> <command...>        simulate a player command
                  concmd <command...>              simulate a console command
                  log <line...>                    simulate a console log line
                  break <player> [count]           simulate block breaks
                  place <player> <MATERIAL>        simulate placing an item (e.g. TNT)
                  use <player> <MATERIAL>          simulate using an item (e.g. FLINT_AND_STEEL)
                  gamemode <player> <MODE>         simulate a gamemode change
                  join <player>                    simulate a first-time join
                  kick <player> [reason]           simulate a kick
                  death <player> [message]         simulate a death
                Inspect:
                  score [player]                   show attention score(s)
                  status                           show counters
                  recent                           show recent reports
                Control:
                  sleep <millis>                   pause (for scripted pacing)
                  flush                            deliver queued Discord embeds now
                  quit                             exit""");
    }

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(("tattle-sim:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static Logger makeLogger() {
        Logger logger = Logger.getLogger("TattleSim");
        logger.setUseParentHandlers(false);
        for (Handler handler : logger.getHandlers()) {
            logger.removeHandler(handler);
        }
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                String prefix = record.getLevel().intValue() >= Level.WARNING.intValue() ? "! " : "";
                System.out.println(prefix + record.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        });
        return logger;
    }

    private static Map<?, ?> loadConfig(String explicitPath) throws Exception {
        if (explicitPath != null) {
            try (InputStream in = Files.newInputStream(Path.of(explicitPath))) {
                return new Yaml().load(in);
            }
        }
        for (String candidate : new String[]{"plugins/Tattle/config.yml", "src/main/resources/config.yml", "config.yml"}) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                System.out.println("Using config: " + path);
                try (InputStream in = Files.newInputStream(path)) {
                    return new Yaml().load(in);
                }
            }
        }
        try (InputStream in = Simulator.class.getResourceAsStream("/config.yml")) {
            if (in != null) {
                System.out.println("Using bundled default config.");
                return new Yaml().load(in);
            }
        }
        System.out.println("No config found — using built-in defaults.");
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> withWebhook(Map<?, ?> raw, String url) {
        Map<Object, Object> root = new HashMap<>(raw);
        Object discord = root.get("discord");
        Map<Object, Object> discordMap = discord instanceof Map<?, ?> m ? new HashMap<>((Map<Object, Object>) m) : new HashMap<>();
        discordMap.put("webhook-url", url);
        root.put("discord", discordMap);
        return root;
    }
}
