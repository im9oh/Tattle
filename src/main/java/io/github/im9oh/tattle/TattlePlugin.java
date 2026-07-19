package io.github.im9oh.tattle;

import io.github.im9oh.tattle.command.TattleCommand;
import io.github.im9oh.tattle.config.Settings;
import io.github.im9oh.tattle.monitor.ChatMonitor;
import io.github.im9oh.tattle.monitor.CommandMonitor;
import io.github.im9oh.tattle.monitor.ConsoleMonitor;
import io.github.im9oh.tattle.monitor.PlayerActionMonitor;
import io.github.im9oh.tattle.report.DiscordWebhook;
import io.github.im9oh.tattle.report.ReportManager;
import io.github.im9oh.tattle.score.ScoringEngine;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tattle — passive monitoring and reporting.
 *
 * Observes chat, commands, console output and player actions, scores them, and
 * reports notable activity to Discord for human review.
 *
 * By design there is no punishment code in this plugin: no bans, no kicks,
 * no mutes, no cancelled events. Staff decide what to do with reports.
 */
public final class TattlePlugin extends org.bukkit.plugin.java.JavaPlugin implements Listener {

    private volatile Settings settings;
    private ScoringEngine engine;
    private ReportManager reports;
    private DiscordWebhook webhook;
    private ConsoleMonitor consoleMonitor;
    private BukkitTask flushTask;
    private long enabledAtMillis;

    /** Players with the tattle.exempt permission, cached at join to stay off the main thread later. */
    private final Set<UUID> exempt = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        enabledAtMillis = System.currentTimeMillis();
        saveDefaultConfig();
        settings = new Settings(getConfig(), getLogger());

        webhook = new DiscordWebhook(this);
        reports = new ReportManager(this, webhook);
        engine = new ScoringEngine(this, reports);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(new ChatMonitor(this, engine), this);
        pm.registerEvents(new CommandMonitor(this, engine), this);
        pm.registerEvents(new PlayerActionMonitor(this, engine), this);

        consoleMonitor = new ConsoleMonitor(this, engine);
        consoleMonitor.attach();

        PluginCommand command = getCommand("tattle");
        if (command != null) {
            TattleCommand executor = new TattleCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        startFlushTask();

        getLogger().info("Tattle is watching. Discord webhook: "
                + (settings.webhookUrl.isEmpty() ? "not configured (reports go to console only)" : "configured")
                + ". Tattle only observes and reports — it never punishes.");
    }

    @Override
    public void onDisable() {
        if (consoleMonitor != null) {
            consoleMonitor.detach();
        }
        if (flushTask != null) {
            flushTask.cancel();
        }
        if (webhook != null) {
            // Best-effort final delivery of anything still queued.
            webhook.flush();
        }
    }

    /** Re-reads config.yml and swaps in a fresh settings snapshot. */
    public void reloadSettings() {
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        startFlushTask();
        for (Player player : getServer().getOnlinePlayers()) {
            updateExempt(player);
        }
        getLogger().info("Configuration reloaded.");
    }

    private void startFlushTask() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        long ticks = settings.batchIntervalSeconds * 20L;
        flushTask = getServer().getScheduler().runTaskTimerAsynchronously(this, webhook::flush, ticks, ticks);
    }

    public Settings settings() {
        return settings;
    }

    public ScoringEngine engine() {
        return engine;
    }

    public ReportManager reports() {
        return reports;
    }

    public DiscordWebhook webhook() {
        return webhook;
    }

    public long uptimeMillis() {
        return System.currentTimeMillis() - enabledAtMillis;
    }

    public boolean isExempt(UUID id) {
        return exempt.contains(id);
    }

    private void updateExempt(Player player) {
        if (player.hasPermission("tattle.exempt")) {
            exempt.add(player.getUniqueId());
        } else {
            exempt.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateExempt(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        exempt.remove(event.getPlayer().getUniqueId());
    }
}
