package io.github.im9oh.tattle.inspect;

import io.github.im9oh.tattle.TattleContext;
import io.github.im9oh.tattle.config.Settings;
import io.github.im9oh.tattle.model.Observation;
import io.github.im9oh.tattle.model.RegexRule;
import io.github.im9oh.tattle.model.SourceType;
import io.github.im9oh.tattle.score.RateTracker;
import io.github.im9oh.tattle.score.ScoringEngine;

import java.util.UUID;

/**
 * Inspects one chat message: configurable regex rules, excessive caps, and
 * spam bursts. Shared between the Bukkit chat monitor and the simulator.
 * Thread-safe.
 */
public final class ChatInspector {

    private final TattleContext ctx;
    private final ScoringEngine engine;
    private final RateTracker spamTracker = new RateTracker();

    public ChatInspector(TattleContext ctx, ScoringEngine engine) {
        this.ctx = ctx;
        this.engine = engine;
    }

    public void inspect(String actorName, UUID actorId, String message) {
        Settings settings = ctx.settings();

        for (RegexRule rule : settings.chatRules) {
            if (rule.pattern().matcher(message).find()) {
                engine.observe(Observation.of(SourceType.CHAT, actorName, actorId,
                        "chat/" + rule.name(),
                        "Chat matched rule '" + rule.name() + "'",
                        message, rule.score(), rule.severity()));
            }
        }

        Settings.CapsRule caps = settings.chatCaps;
        if (message.length() >= caps.minLength()) {
            int letters = 0;
            int upper = 0;
            for (int i = 0; i < message.length(); i++) {
                char c = message.charAt(i);
                if (Character.isLetter(c)) {
                    letters++;
                    if (Character.isUpperCase(c)) {
                        upper++;
                    }
                }
            }
            if (letters >= caps.minLength() / 2 && (double) upper / letters >= caps.maxRatio()) {
                engine.observe(Observation.of(SourceType.CHAT, actorName, actorId,
                        "chat/caps", "Excessive caps in chat", message, caps.score(), caps.severity()));
            }
        }

        Settings.SpamRule spam = settings.chatSpam;
        int hits = spamTracker.hit(actorId, spam.windowMillis());
        if (hits == spam.maxHits() + 1) {
            engine.observe(Observation.of(SourceType.CHAT, actorName, actorId,
                    "chat/spam",
                    "Chat spam: more than " + spam.maxHits() + " messages in " + spam.windowMillis() / 1000 + "s",
                    "Latest message: " + message, spam.score(), spam.severity()));
        }
    }

    public void clear(UUID actorId) {
        spamTracker.clear(actorId);
    }
}
