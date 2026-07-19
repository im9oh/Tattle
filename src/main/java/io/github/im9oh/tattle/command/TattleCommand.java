package io.github.im9oh.tattle.command;

import io.github.im9oh.tattle.TattlePlugin;
import io.github.im9oh.tattle.config.Settings;
import io.github.im9oh.tattle.model.Observation;
import io.github.im9oh.tattle.model.Severity;
import io.github.im9oh.tattle.model.SourceType;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /tattle status | reload | test | score [player] | recent [count] */
public final class TattleCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("status", "reload", "test", "score", "recent");

    private final TattlePlugin plugin;

    public TattleCommand(TattlePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "status" -> status(sender);
            case "reload" -> {
                plugin.reloadSettings();
                sender.sendMessage(ChatColor.GREEN + "[Tattle] Configuration reloaded.");
            }
            case "test" -> test(sender);
            case "score" -> score(sender, args);
            case "recent" -> recent(sender, args);
            default -> sender.sendMessage(ChatColor.YELLOW + "[Tattle] Usage: /" + label + " <status|reload|test|score|recent>");
        }
        return true;
    }

    private void status(CommandSender sender) {
        Settings s = plugin.settings();
        long uptimeMinutes = plugin.uptimeMillis() / 60_000;
        sender.sendMessage(ChatColor.AQUA + "── Tattle status ──");
        sender.sendMessage(ChatColor.GRAY + "Watching for " + uptimeMinutes + " min. Tattle only observes and reports — it never punishes.");
        sender.sendMessage(ChatColor.GRAY + "Monitors: "
                + monitor("chat", s.chatEnabled) + " "
                + monitor("commands", s.commandsEnabled) + " "
                + monitor("console", s.consoleEnabled) + " "
                + monitor("actions", s.actionsEnabled));
        sender.sendMessage(ChatColor.GRAY + "Discord webhook: "
                + (s.webhookUrl.isEmpty()
                        ? ChatColor.RED + "not configured (console-only reports)"
                        : ChatColor.GREEN + "configured" + ChatColor.GRAY + " (min severity " + s.minSeverity + ", queue "
                          + plugin.webhook().queueSize() + ", delivered " + plugin.webhook().deliveredCount()
                          + ", dropped " + plugin.webhook().failedCount() + ")"));
        sender.sendMessage(ChatColor.GRAY + "Observed — chat: " + plugin.engine().observedCount(SourceType.CHAT)
                + ", commands: " + plugin.engine().observedCount(SourceType.COMMAND)
                + ", console: " + plugin.engine().observedCount(SourceType.CONSOLE)
                + ", actions: " + plugin.engine().observedCount(SourceType.ACTION));
        sender.sendMessage(ChatColor.GRAY + "Reports: " + plugin.reports().sentCount()
                + " sent, " + plugin.reports().suppressedCount() + " suppressed by cooldown.");
        List<Map.Entry<String, Double>> top = plugin.engine().keeper().top(5, s.attentionHalfLifeSeconds);
        if (!top.isEmpty()) {
            StringBuilder line = new StringBuilder(ChatColor.GRAY + "Attention: ");
            for (int i = 0; i < top.size(); i++) {
                if (i > 0) {
                    line.append(", ");
                }
                line.append(top.get(i).getKey()).append(" ").append(String.format("%.1f", top.get(i).getValue()));
            }
            sender.sendMessage(line.toString());
        }
    }

    private static String monitor(String name, boolean enabled) {
        return (enabled ? ChatColor.GREEN : ChatColor.RED) + name + ChatColor.GRAY;
    }

    private void test(CommandSender sender) {
        plugin.engine().observeDirect(Observation.of(SourceType.ACTION, sender.getName(),
                sender instanceof Player player ? player.getUniqueId() : null,
                "tattle/test",
                "Test report requested by " + sender.getName(),
                "If you can read this in Discord, Tattle's webhook is working.",
                0.0, Severity.LOW));
        boolean webhookConfigured = !plugin.settings().webhookUrl.isEmpty();
        sender.sendMessage(ChatColor.GREEN + "[Tattle] Test report queued. "
                + (webhookConfigured
                        ? "It will reach Discord within " + plugin.settings().batchIntervalSeconds + "s."
                        : "No webhook configured, so it only went to the console."));
    }

    private void score(CommandSender sender, String[] args) {
        Settings s = plugin.settings();
        if (args.length >= 2) {
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "[Tattle] Player not online: " + args[1]);
                return;
            }
            double value = plugin.engine().keeper().current(target.getUniqueId(), s.attentionHalfLifeSeconds);
            sender.sendMessage(ChatColor.AQUA + "[Tattle] " + target.getName() + " attention score: "
                    + String.format("%.1f", value) + ChatColor.GRAY + " (report at " + String.format("%.1f", s.attentionThreshold) + ")");
            return;
        }
        List<Map.Entry<String, Double>> top = plugin.engine().keeper().top(10, s.attentionHalfLifeSeconds);
        if (top.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "[Tattle] No attention scores right now — all quiet.");
            return;
        }
        sender.sendMessage(ChatColor.AQUA + "── Attention scores (report at " + String.format("%.1f", s.attentionThreshold) + ") ──");
        for (Map.Entry<String, Double> entry : top) {
            sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " + String.format("%.1f", entry.getValue()));
        }
    }

    private void recent(CommandSender sender, String[] args) {
        int count = 10;
        if (args.length >= 2) {
            try {
                count = Math.max(1, Math.min(25, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        List<String> recent = plugin.reports().recentReports();
        if (recent.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "[Tattle] No reports yet.");
            return;
        }
        sender.sendMessage(ChatColor.AQUA + "── Recent reports ──");
        for (String line : recent.subList(0, Math.min(count, recent.size()))) {
            sender.sendMessage(ChatColor.GRAY + line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("score")) {
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
