package com.pseudosmp.msb.commands;

import com.pseudosmp.msb.MatrixSpigotBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RestartBridge implements CommandExecutor {
    private final MatrixSpigotBridge plugin;
    private boolean bridgeStarted = false;

    public RestartBridge(MatrixSpigotBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("matrixspigotbridge.restart")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        // Disconnect old Matrix connection if needed
        // (Assume Matrix class has a disconnect/close method, otherwise skip)
        // plugin.getMatrix().disconnect();

        sender.sendMessage("§eRestarting Matrix bridge...");

        // Reconnect logic (mirrors onEnable, but does not reload config)
        try {
            plugin.reloadConfig();
            plugin.cacheMatrixDisplaynames = plugin.getConfig().getBoolean("common.cacheMatrixDisplaynames");
            plugin.canUsePapi = plugin.getConfig().getBoolean("common.usePlaceholderApi")
                    && plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
            plugin.startBridgeAsync(sender);
        } catch (Exception e) {
            sender.sendMessage("§cFailed to restart Matrix bridge: " + e.getMessage());
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to restart Matrix bridge", e);
        }

        if (plugin.getMatrix() != null && plugin.getMatrix().isValid()) {
            sender.sendMessage("§eMatrix bridge restarted successfully.");
        } 
        return true;
    }
}