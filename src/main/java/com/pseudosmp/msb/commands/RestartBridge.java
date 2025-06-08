package com.pseudosmp.msb.commands;

import com.pseudosmp.msb.MatrixSpigotBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RestartBridge implements CommandExecutor {
    private final MatrixSpigotBridge plugin;

    public RestartBridge(MatrixSpigotBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("msb.command.restart")) {
            sender.sendMessage("§e[MatrixSpigotBridge] §cYou do not have permission to use this command.");
            return true;
        }

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
        return true;
    }
}