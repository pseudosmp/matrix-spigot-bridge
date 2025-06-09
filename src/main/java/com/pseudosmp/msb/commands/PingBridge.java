package com.pseudosmp.msb.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.pseudosmp.msb.MatrixSpigotBridge;

public class PingBridge implements CommandExecutor {
    private final MatrixSpigotBridge plugin;

    public PingBridge(MatrixSpigotBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("msb.command.ping")) {
            sender.sendMessage("§e[MatrixSpigotBridge] §cYou do not have permission to use this command.");
            return true;
        }
        sender.sendMessage("§e[MatrixSpigotBridge] §7" + plugin.getMatrix().ping());
        return true;
    }
}