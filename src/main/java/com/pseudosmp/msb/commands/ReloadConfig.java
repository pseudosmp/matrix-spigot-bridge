package com.pseudosmp.msb.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.pseudosmp.msb.MatrixSpigotBridge;

public class ReloadConfig implements CommandExecutor {
    private final MatrixSpigotBridge plugin;

    public ReloadConfig(MatrixSpigotBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("msb.command.reload")) {
            sender.sendMessage("§e[MatrixSpigotBridge] §cYou do not have permission to use this command.");
            return true;
        }
        plugin.reloadConfig();
        plugin.cacheMatrixDisplaynames = plugin.getConfig().getBoolean("common.cacheMatrixDisplaynames");
        plugin.canUsePapi = plugin.getConfig().getBoolean("common.usePlaceholderApi")
                && plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        sender.sendMessage("§e[MatrixSpigotBridge] §aConfiguration reloaded.");
        return true;
    }
}