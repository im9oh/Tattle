package io.github.im9oh.tattle.monitor;

import io.github.im9oh.tattle.TattlePlugin;
import io.github.im9oh.tattle.config.Settings;
import io.github.im9oh.tattle.model.Observation;
import io.github.im9oh.tattle.model.Severity;
import io.github.im9oh.tattle.model.SourceType;
import io.github.im9oh.tattle.score.RateTracker;
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

import java.util.Locale;

/**
 * Observes in-world player activity: rapid block breaking, placement/use of
 * grief-adjacent items, gamemode changes, first-time joins, kicks and deaths.
 */
@SuppressWarnings("deprecation") // PlayerKickEvent#getReason / PlayerDeathEvent#getDeathMessage: Bukkit API
public final class PlayerActionMonitor implements Listener {

    private final TattlePlugin plugin;
    private final ScoringEngine engine;
    private final RateTracker breakTracker = new RateTracker();

    public PlayerActionMonitor(TattlePlugin plugin, ScoringEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
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
        Settings.SpamRule rule = settings.blockBreak;
        int hits = breakTracker.hit(player.getUniqueId(), rule.windowMillis());
        if (hits == rule.maxHits() + 1) {
            engine.observe(Observation.of(SourceType.ACTION, player.getName(), player.getUniqueId(),
                    "action/rapid-break",
                    "Rapid block breaking: more than " + rule.maxHits() + " blocks in " + rule.windowMillis() / 1000 + "s",
                    "Last block: " + event.getBlock().getType() + " at " + where(event.getBlock().getLocation()),
                    rule.score(), rule.severity()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Settings settings = plugin.settings();
        Player player = event.getPlayer();
        if (skip(settings, player)) {
            return;
        }
        Material type = event.getBlockPlaced().getType();
        Settings.WatchedEntry watched = settings.watchedItems.get(type);
        if (watched != null) {
            watchedItem(player, "Placed", type, event.getBlockPlaced().getLocation(), watched);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Settings settings = plugin.settings();
        Player player = event.getPlayer();
        if (skip(settings, player)) {
            return;
        }
        Settings.WatchedEntry watched = settings.watchedItems.get(event.getBucket());
        if (watched != null) {
            watchedItem(player, "Emptied", event.getBucket(), event.getBlockClicked().getLocation(), watched);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // Blocks are covered by BlockPlaceEvent and buckets by PlayerBucketEmptyEvent;
        // this catches non-block "use" items like flint and steel or end crystals.
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null || !event.getAction().name().startsWith("RIGHT_CLICK")) {
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
        Settings.WatchedEntry watched = settings.watchedItems.get(type);
        if (watched != null) {
            Location location = event.getClickedBlock() != null
                    ? event.getClickedBlock().getLocation()
                    : player.getLocation();
            watchedItem(player, "Used", type, location, watched);
        }
    }

    private void watchedItem(Player player, String verb, Material type, Location location, Settings.WatchedEntry watched) {
        String name = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        engine.observe(Observation.of(SourceType.ACTION, player.getName(), player.getUniqueId(),
                "action/item-" + type.name().toLowerCase(Locale.ROOT),
                verb + " " + name,
                verb + " " + name + " at " + where(location),
                watched.score(), watched.severity()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Settings settings = plugin.settings();
        Player player = event.getPlayer();
        if (skip(settings, player) || !settings.gamemodeEnabled) {
            return;
        }
        engine.observe(Observation.of(SourceType.ACTION, player.getName(), player.getUniqueId(),
                "action/gamemode",
                "Gamemode changed to " + event.getNewGameMode(),
                player.getName() + " switched from " + player.getGameMode() + " to " + event.getNewGameMode(),
                settings.gamemodeChange.score(), settings.gamemodeChange.severity()));
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
        breakTracker.clear(event.getPlayer().getUniqueId());
    }

    private static String where(Location location) {
        return (location.getWorld() != null ? location.getWorld().getName() : "?")
                + " (" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }
}
