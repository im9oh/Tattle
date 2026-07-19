package io.github.im9oh.tattle.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.im9oh.tattle.TattleContext;
import io.github.im9oh.tattle.config.Settings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Batches report embeds and delivers them to a Discord webhook.
 * All network I/O happens off the main server thread (flush() is driven by an
 * async repeating task). Failures are retried a few times, then dropped —
 * delivery problems must never affect the server.
 */
public final class DiscordWebhook {

    private static final int MAX_EMBEDS_PER_MESSAGE = 10;
    private static final int MAX_QUEUE = 200;
    private static final int MAX_ATTEMPTS = 3;

    private static final class Pending {
        final JsonObject embed;
        int attempts;

        Pending(JsonObject embed) {
            this.embed = embed;
        }
    }

    private final TattleContext ctx;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final Deque<Pending> queue = new ArrayDeque<>();
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private volatile long backoffUntil = 0;
    private volatile long lastErrorLogAt = 0;

    public DiscordWebhook(TattleContext ctx) {
        this.ctx = ctx;
    }

    /** Thread-safe. Oldest embeds are dropped if the queue overflows. */
    public void enqueue(JsonObject embed) {
        synchronized (queue) {
            queue.addLast(new Pending(embed));
            while (queue.size() > MAX_QUEUE) {
                queue.pollFirst();
                failed.incrementAndGet();
            }
        }
    }

    /** Sends one batch if due. Called from an async task (and once on shutdown). */
    public void flush() {
        Settings settings = ctx.settings();
        if (settings.webhookUrl.isEmpty() || System.currentTimeMillis() < backoffUntil) {
            return;
        }

        List<Pending> batch = new ArrayList<>(MAX_EMBEDS_PER_MESSAGE);
        synchronized (queue) {
            while (batch.size() < MAX_EMBEDS_PER_MESSAGE && !queue.isEmpty()) {
                batch.add(queue.pollFirst());
            }
        }
        if (batch.isEmpty()) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("username", settings.webhookUsername);
        JsonArray embeds = new JsonArray();
        for (Pending pending : batch) {
            embeds.add(pending.embed);
        }
        payload.add("embeds", embeds);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(settings.webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                delivered.addAndGet(batch.size());
            } else if (status == 429) {
                long retryMillis = response.headers().firstValue("Retry-After")
                        .map(v -> (long) (Double.parseDouble(v) * 1000))
                        .orElse(5000L);
                backoffUntil = System.currentTimeMillis() + Math.max(1000L, retryMillis);
                requeue(batch, false);
            } else {
                logError("Discord webhook returned HTTP " + status);
                requeue(batch, true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            requeue(batch, false);
        } catch (Exception e) {
            logError("Discord webhook delivery failed: " + e.getMessage());
            requeue(batch, true);
        }
    }

    private void requeue(List<Pending> batch, boolean countAttempt) {
        synchronized (queue) {
            // Re-add in reverse so original order is preserved at the front.
            for (int i = batch.size() - 1; i >= 0; i--) {
                Pending pending = batch.get(i);
                if (countAttempt) {
                    pending.attempts++;
                }
                if (pending.attempts >= MAX_ATTEMPTS) {
                    failed.incrementAndGet();
                } else {
                    queue.addFirst(pending);
                }
            }
        }
    }

    private void logError(String message) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLogAt > 60_000) {
            lastErrorLogAt = now;
            ctx.logger().warning(message);
        }
    }

    public int queueSize() {
        synchronized (queue) {
            return queue.size();
        }
    }

    public long deliveredCount() {
        return delivered.get();
    }

    public long failedCount() {
        return failed.get();
    }
}
