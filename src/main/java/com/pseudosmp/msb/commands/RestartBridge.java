package com.pseudosmp.msb.commands;

import com.pseudosmp.msb.MatrixSpigotBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.pseudosmp.tools.game.ConfigUtils;

public class RestartBridge implements CommandExecutor {
    private final MatrixSpigotBridge plugin;
    private final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();
    private final long CONFIRM_TIMEOUT = 60_000; // 60 seconds
    private final ConfigUtils config;

    public RestartBridge(MatrixSpigotBridge plugin) {
        this.plugin = plugin;
        this.config = MatrixSpigotBridge.config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String senderKey = sender instanceof org.bukkit.entity.Player
                ? ((org.bukkit.entity.Player) sender).getUniqueId().toString()
                : "CONSOLE";

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

        if (!config.load()) {
            sender.sendMessage("§e[MatrixSpigotBridge] §cFailed to reload configuration. Please check the console for errors.");
            return;
        }

        plugin.startBridgeAsync(sender, success -> {
            if (success) {
                sender.sendMessage("§e[MatrixSpigotBridge] §aMatrix bridge restarted successfully.");
                plugin.getMatrix().postMessage(config.getFormat("server.reconnect"));
                if (config.matrixTopicUpdateInterval > 0 && !config.getFormat("room_topic").isEmpty()) {
                    plugin.updateRoomTopicAsync(success1 -> {});
                }
            } else {
                sender.sendMessage("§e[MatrixSpigotBridge] §cFailed to restart Matrix bridge. Check your config and run /msb restart again.");
            }
        });
    }
}
