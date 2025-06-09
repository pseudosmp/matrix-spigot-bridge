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

        String prevUser = plugin.getConfig().getString("matrix.user_id");
        String prevPwd = plugin.getConfig().getString("matrix.password");
        String prevHomeserver = plugin.getConfig().getString("matrix.server");
        String prevRoomID = plugin.getConfig().getString("matrix.room_id");
        
        plugin.reloadConfig();
        plugin.cacheMatrixDisplaynames = plugin.getConfig().getBoolean("common.cacheMatrixDisplaynames");
        plugin.canUsePapi = plugin.getConfig().getBoolean("common.usePlaceholderApi")
                && plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        plugin.matrixMessagePrefix = plugin.getConfig().getString("format.matrix_chat");
		plugin.matrixCommandPrefix = plugin.getConfig().getString("matrix.command_prefix", "!");
        
        if (!plugin.getConfig().getString("matrix.user_id").equals(prevUser)
                || !plugin.getConfig().getString("matrix.password").equals(prevPwd)
                || !plugin.getConfig().getString("matrix.server").equals(prevHomeserver)
                || !plugin.getConfig().getString("matrix.room_id").equals(prevRoomID)) {
            sender.sendMessage("§e[MatrixSpigotBridge] §aMatrix credentials changed! §7Please run §a/msb restart §7to connect with the new credentials.");
            return true;
        }
        sender.sendMessage("§e[MatrixSpigotBridge] §aConfiguration reloaded.");
        return true;
    }
}