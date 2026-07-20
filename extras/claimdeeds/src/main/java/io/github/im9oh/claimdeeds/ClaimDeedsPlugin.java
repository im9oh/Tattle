package io.github.im9oh.claimdeeds;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Physical claim-block vouchers for GriefPrevention.
 *
 * Deeds are ordinary items (so they stack, trade, and sell in item shops).
 * Right-clicking one consumes it and runs the configured console command
 * (GriefPrevention's /acb by default) to credit bonus claim blocks — no
 * compile-time dependency on GriefPrevention needed.
 */
public final class ClaimDeedsPlugin extends JavaPlugin implements Listener {

    private NamespacedKey blocksKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        blocksKey = new NamespacedKey(this, "blocks");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ClaimDeeds ready. Chest chance: " + getConfig().getDouble("chest-chance")
                + ", sizes: " + getConfig().getIntegerList("deed-sizes"));
    }

    // ── item ────────────────────────────────────────────────────────────────

    public ItemStack makeDeed(int blocks, int count) {
        ItemStack item = new ItemStack(Material.PAPER, count);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Claim Deed " + ChatColor.GRAY + "(+" + blocks + " blocks)");
        meta.setLore(List.of(
                ChatColor.GRAY + "Right-click to add " + ChatColor.WHITE + blocks + ChatColor.GRAY + " claim blocks",
                ChatColor.GRAY + "to your land-claim balance.",
                ChatColor.DARK_GRAY + "Stone Realm land registry"));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(blocksKey, PersistentDataType.INTEGER, blocks);
        item.setItemMeta(meta);
        return item;
    }

    private int deedBlocks(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer value = item.getItemMeta().getPersistentDataContainer()
                .get(blocksKey, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    // ── natural loot ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        String table = event.getLootTable().getKey().toString();
        boolean eligible = getConfig().getStringList("loot-tables").stream().anyMatch(table::contains);
        if (!eligible) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= getConfig().getDouble("chest-chance", 0.25)) {
            return;
        }
        List<Integer> sizes = getConfig().getIntegerList("deed-sizes");
        if (sizes.isEmpty()) {
            sizes = List.of(50);
        }
        int blocks = sizes.get(ThreadLocalRandom.current().nextInt(sizes.size()));
        event.getLoot().add(makeDeed(blocks, 1));
    }

    // ── redeem ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        int blocks = deedBlocks(item);
        if (blocks <= 0) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        item.setAmount(item.getAmount() - 1);
        String command = getConfig().getString("redeem-command", "acb %player% %blocks%")
                .replace("%player%", player.getName())
                .replace("%blocks%", String.valueOf(blocks));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        player.sendMessage(ChatColor.GREEN + "[Claims] " + ChatColor.WHITE + "+" + blocks
                + " claim blocks added to your balance.");
        player.playSound(player.getLocation(), "entity.player.levelup", 0.7f, 1.4f);
    }

    // ── admin command ───────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " give <player> <blocks> [count]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not online: " + args[1]);
            return true;
        }
        int blocks;
        int count = 1;
        try {
            blocks = Integer.parseInt(args[2]);
            if (args.length >= 4) {
                count = Math.max(1, Integer.parseInt(args[3]));
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Not a number: " + args[2]);
            return true;
        }
        target.getInventory().addItem(makeDeed(blocks, count));
        sender.sendMessage(ChatColor.GREEN + "Gave " + count + "x Claim Deed (+" + blocks + ") to " + target.getName());
        return true;
    }
}
