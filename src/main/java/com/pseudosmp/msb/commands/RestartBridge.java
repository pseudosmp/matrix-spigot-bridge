package com.pseudosmp.msb.commands;

import com.pseudosmp.msb.MatrixSpigotBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RestartBridge implements CommandExecutor {
    private final MatrixSpigotBridge plugin;
    private final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();
    private final long CONFIRM_TIMEOUT = 60_000; // 60 seconds

    public RestartBridge(MatrixSpigotBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String senderKey = sender instanceof org.bukkit.entity.Player
                ? ((org.bukkit.entity.Player) sender).getUniqueId().toString()
                : "CONSOLE";

        if (!sender.hasPermission("msb.command.restart")) {
            sender.sendMessage("§e[MatrixSpigotBridge] §cYou do not have permission to use this command.");
            return true;
        }

        // Handle confirmation
        if (args.length > 0 && args[0].equalsIgnoreCase("confirm")) {
            Long requested = pendingConfirmations.get(senderKey);
            if (requested != null && System.currentTimeMillis() - requested < CONFIRM_TIMEOUT) {
                pendingConfirmations.remove(senderKey);
                doRestart(sender);
            } else {
                sender.sendMessage("§e[MatrixSpigotBridge] §cNo restart pending confirmation or confirmation timed out. Use /msb restart again.");
            }
            return true;
        }

        // If not confirming, prompt for confirmation
        pendingConfirmations.put(senderKey, System.currentTimeMillis());
        sender.sendMessage("§e[MatrixSpigotBridge] §cAre you sure you want to restart the Matrix bridge?");
        sender.sendMessage("§eThis may have unintended side effects when the chat is active.");
        sender.sendMessage("§e[MatrixSpigotBridge] §7Type §a/msb restart confirm §7within 60 seconds to confirm.");
        return true;
    }

    private void doRestart(CommandSender sender) {
        sender.sendMessage("§e[MatrixSpigotBridge] §aRestarting Matrix bridge...");

        plugin.reloadConfig();
        plugin.cacheMatrixDisplaynames = plugin.getConfig().getBoolean("common.cacheMatrixDisplaynames");
        plugin.canUsePapi = plugin.getConfig().getBoolean("common.usePlaceholderApi")
                && plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        plugin.startBridgeAsync(sender, success -> {
            if (success) {
                sender.sendMessage("§e[MatrixSpigotBridge] §aMatrix bridge restarted successfully.");
            } else {
                sender.sendMessage("§e[MatrixSpigotBridge] §cFailed to restart Matrix bridge. Check your config and run /msb restart again.");
            }
        });
    }
}
