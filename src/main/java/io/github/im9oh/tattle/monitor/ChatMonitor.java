package io.github.im9oh.tattle.monitor;

import io.github.im9oh.tattle.TattlePlugin;
import io.github.im9oh.tattle.inspect.ChatInspector;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Bukkit adapter for {@link ChatInspector}. AsyncPlayerChatEvent fires off the
 * main thread; everything downstream is thread-safe by design.
 */
@SuppressWarnings("deprecation") // AsyncPlayerChatEvent: Bukkit API, works on both Spigot and Paper
public final class ChatMonitor implements Listener {

    private final TattlePlugin plugin;
    private final ChatInspector inspector;

    public ChatMonitor(TattlePlugin plugin, ChatInspector inspector) {
        this.plugin = plugin;
        this.inspector = inspector;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.settings().chatEnabled) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.isExempt(player.getUniqueId())) {
            return;
        }
        inspector.inspect(player.getName(), player.getUniqueId(), event.getMessage());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inspector.clear(event.getPlayer().getUniqueId());
    }
}
