package io.github.im9oh.tattle.monitor;

import io.github.im9oh.tattle.TattlePlugin;
import io.github.im9oh.tattle.inspect.CommandInspector;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;

/** Bukkit adapter for {@link CommandInspector}: player and console commands. */
public final class CommandMonitor implements Listener {

    private final TattlePlugin plugin;
    private final CommandInspector inspector;

    public CommandMonitor(TattlePlugin plugin, CommandInspector inspector) {
        this.plugin = plugin;
        this.inspector = inspector;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.settings().commandsEnabled) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.isExempt(player.getUniqueId())) {
            return;
        }
        inspector.inspect(player.getName(), player.getUniqueId(), event.getMessage());
        inspector.trackSpam(player.getName(), player.getUniqueId(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!plugin.settings().commandsEnabled) {
            return;
        }
        inspector.inspect(event.getSender().getName(), null, "/" + event.getCommand());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inspector.clear(event.getPlayer().getUniqueId());
    }
}
