package io.github.im9oh.tattle.monitor;

import io.github.im9oh.tattle.TattlePlugin;
import io.github.im9oh.tattle.config.Settings;
import io.github.im9oh.tattle.inspect.ActionInspector;
import io.github.im9oh.tattle.model.Observation;
import io.github.im9oh.tattle.model.Severity;
import io.github.im9oh.tattle.model.SourceType;
import io.github.im9oh.tattle.score.ScoringEngine;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Bukkit adapter for {@link ActionInspector}, plus the informational events
 * (first joins, kicks, deaths) that are reported directly without scoring.
 */
@SuppressWarnings("deprecation") // PlayerKickEvent#getReason / PlayerDeathEvent#getDeathMessage: Bukkit API
public final class PlayerActionMonitor implements Listener {

    private final TattlePlugin plugin;
    private final ScoringEngine engine;
    private final ActionInspector inspector;

    public PlayerActionMonitor(TattlePlugin plugin, ScoringEngine engine, ActionInspector inspector) {
        this.plugin = plugin;
        this.engine = engine;
        this.inspector = inspector;
    }

    private boolean skip(Settings settings, Player player) {
        return !settings.actionsEnabled || plugin.isExempt(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Settings settings = plugin.settings();
        Player player = event.getPlayer();
        if (skip(settings, player)) {
            return;
        }
        inspector.blockBreak(player.getName(), player.getUniqueId(),
                event.getBlock().getType().name(), where(event.getBlock().getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Settings settings = plugin.settings();
        Player player = event.getPlayer();
        if (skip(settings, player)) {
            return;
        }
        inspector.watchedItem(player.getName(), player.getUniqueId(), "Placed",
                event.getBlockPlaced().getType().name(), where(event.getBlockPlaced().getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Settings settings = plugin.settings();
        Player player = event.getPlayer();
        if (skip(settings, player)) {
            return;
        }
        inspector.watchedItem(player.getName(), player.getUniqueId(), "Emptied",
                event.getBucket().name(), where(event.getBlockClicked().getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // Blocks are covered by BlockPlaceEvent and buckets by PlayerBucketEmptyEvent;
        // this catches non-block "use" items like flint and steel or end crystals.
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null
                || !event.getAction().name().startsWith("RIGHT_CLICK")) {
            return;
        }
        Material type = event.getItem().getType();
        if (type.isBlock() || type.name().endsWith("_BUCKET")) {
            return;
        }
        Settings settings = plugin.settings();
        Player player = event.getPlayer();
        if (skip(settings, player)) {
            return;
        }
        Location location = event.getClickedBlock() != null
                ? event.getClickedBlock().getLocation()
                : player.getLocation();
        inspector.watchedItem(player.getName(), player.getUniqueId(), "Used", type.name(), where(location));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Settings settings = plugin.settings();
        Player player = event.getPlayer();
        if (skip(settings, player)) {
            return;
        }
        inspector.gamemodeChange(player.getName(), player.getUniqueId(),
                String.valueOf(player.getGameMode()), String.valueOf(event.getNewGameMode()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Settings settings = plugin.settings();
        Player player = event.getPlayer();
        if (!settings.actionsEnabled || !settings.reportNewPlayers || player.hasPlayedBefore()) {
            return;
        }
        engine.observeDirect(Observation.of(SourceType.ACTION, player.getName(), player.getUniqueId(),
                "player/new-join",
                "New player joined for the first time",
                player.getName() + " joined for the first time.",
                0.0, Severity.LOW));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        Settings settings = plugin.settings();
        Player player = event.getPlayer();
        if (!settings.actionsEnabled || !settings.reportKicks) {
            return;
        }
        engine.observeDirect(Observation.of(SourceType.ACTION, player.getName(), player.getUniqueId(),
                "player/kick",
                "Player was kicked",
                "Reason: " + event.getReason(),
                0.0, Severity.MEDIUM));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Settings settings = plugin.settings();
        Player player = event.getEntity();
        if (!settings.actionsEnabled || !settings.reportDeaths || plugin.isExempt(player.getUniqueId())) {
            return;
        }
        String message = event.getDeathMessage();
        engine.observeDirect(Observation.of(SourceType.ACTION, player.getName(), player.getUniqueId(),
                "player/death",
                "Player died",
                message != null ? message : player.getName() + " died at " + where(player.getLocation()),
                0.0, Severity.LOW));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inspector.clear(event.getPlayer().getUniqueId());
    }

    private static String where(Location location) {
        return (location.getWorld() != null ? location.getWorld().getName() : "?")
                + " (" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }
}
