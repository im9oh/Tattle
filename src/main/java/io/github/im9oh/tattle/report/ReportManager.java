package io.github.im9oh.tattle.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.im9oh.tattle.TattleContext;
import io.github.im9oh.tattle.config.Settings;
import io.github.im9oh.tattle.model.Observation;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Turns qualifying observations into reports: a console log line, an in-memory
 * recent-reports buffer, and (if configured) a Discord embed.
 *
 * Reporting is deliberately the end of the pipeline — Tattle takes no action
 * against players. Staff decide.
 */
public final class ReportManager {

    private static final int RECENT_LIMIT = 25;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TattleContext ctx;
    private final DiscordWebhook webhook;
    private final Map<String, Long> lastReportAt = new ConcurrentHashMap<>();
    private final Map<String, Integer> suppressedInCooldown = new ConcurrentHashMap<>();
    private final Deque<String> recent = new ArrayDeque<>();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong suppressed = new AtomicLong();

    public ReportManager(TattleContext ctx, DiscordWebhook webhook) {
        this.ctx = ctx;
        this.webhook = webhook;
    }

    /** May be called from any thread. */
    public void report(Observation obs, String note) {
        Settings settings = ctx.settings();
        String key = obs.actorKey() + "|" + obs.category();
        long now = System.currentTimeMillis();

        Long last = lastReportAt.get(key);
        if (last != null && now - last < settings.cooldownMillis) {
            suppressedInCooldown.merge(key, 1, Integer::sum);
            suppressed.incrementAndGet();
            return;
        }
        lastReportAt.put(key, now);
        Integer repeats = suppressedInCooldown.remove(key);

        sent.incrementAndGet();
        String line = String.format("REPORT [%s] %s — %s: %s (score %.1f)%s%s",
                obs.severity(), obs.category(), obs.actorName(), obs.summary(), obs.score(),
                note != null ? " — " + note : "",
                repeats != null ? " [+" + repeats + " similar suppressed earlier]" : "");
        ctx.logger().info(line);

        synchronized (recent) {
            recent.addFirst("[" + TIME.format(obs.timestamp().atZone(ZoneId.systemDefault())) + "] " + line);
            while (recent.size() > RECENT_LIMIT) {
                recent.removeLast();
            }
        }

        if (!settings.webhookUrl.isEmpty() && obs.severity().atLeast(settings.minSeverity)) {
            webhook.enqueue(buildEmbed(obs, note, repeats));
        }
    }

    public List<String> recentReports() {
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }

    public long sentCount() {
        return sent.get();
    }

    public long suppressedCount() {
        return suppressed.get();
    }

    private static JsonObject buildEmbed(Observation obs, String note, Integer repeats) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", obs.severity().emoji() + " " + obs.severity() + " · " + truncate(obs.summary(), 200));
        embed.addProperty("description", truncate(obs.detail(), 1500));
        embed.addProperty("color", obs.severity().color());
        embed.addProperty("timestamp", obs.timestamp().toString());

        JsonArray fields = new JsonArray();
        fields.add(field("Actor", obs.actorName(), true));
        fields.add(field("Source", obs.source().display(), true));
        fields.add(field("Category", obs.category(), true));
        fields.add(field("Score", String.format("%.1f", obs.score()), true));
        if (repeats != null) {
            fields.add(field("Suppressed repeats", repeats + " similar during cooldown", true));
        }
        if (note != null) {
            fields.add(field("Note", truncate(note, 1000), false));
        }
        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Tattle observes and reports — action is always a human decision.");
        embed.add("footer", footer);
        return embed;
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value == null || value.isEmpty() ? "—" : value);
        field.addProperty("inline", inline);
        return field;
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
