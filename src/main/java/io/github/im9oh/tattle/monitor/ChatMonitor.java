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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Observes chat: configurable regex rules, spam bursts, and excessive caps.
 * AsyncPlayerChatEvent fires off the main thread; everything downstream is
 * thread-safe by design.
 */
@SuppressWarnings("deprecation") // AsyncPlayerChatEvent: Bukkit API, works on both Spigot and Paper
public final class ChatMonitor implements Listener {

    private final TattlePlugin plugin;
    private final ScoringEngine engine;
    private final RateTracker spamTracker = new RateTracker();

    public ChatMonitor(TattlePlugin plugin, ScoringEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Settings settings = plugin.settings();
        if (!settings.chatEnabled) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.isExempt(player.getUniqueId())) {
            return;
        }
        String message = event.getMessage();

        for (RegexRule rule : settings.chatRules) {
            if (rule.pattern().matcher(message).find()) {
                engine.observe(Observation.of(SourceType.CHAT, player.getName(), player.getUniqueId(),
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
                engine.observe(Observation.of(SourceType.CHAT, player.getName(), player.getUniqueId(),
                        "chat/caps", "Excessive caps in chat", message, caps.score(), caps.severity()));
            }
        }

        Settings.SpamRule spam = settings.chatSpam;
        int hits = spamTracker.hit(player.getUniqueId(), spam.windowMillis());
        if (hits == spam.maxHits() + 1) {
            engine.observe(Observation.of(SourceType.CHAT, player.getName(), player.getUniqueId(),
                    "chat/spam",
                    "Chat spam: more than " + spam.maxHits() + " messages in " + spam.windowMillis() / 1000 + "s",
                    "Latest message: " + message, spam.score(), spam.severity()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        spamTracker.clear(event.getPlayer().getUniqueId());
    }
}
