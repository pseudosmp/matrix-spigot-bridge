package com.pseudosmp.msb.commands;

import com.pseudosmp.msb.MatrixSpigotBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.pseudosmp.tools.game.ConfigUtils;

public class ReloadConfig implements CommandExecutor {
    ConfigUtils config;

    public ReloadConfig(MatrixSpigotBridge plugin) {
        this.config = MatrixSpigotBridge.config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("msb.command.reload")) {
            sender.sendMessage("§e[MatrixSpigotBridge] §cYou do not have permission to use this command.");
            return true;
        }

        String prevUser = config.matrixUserId;
        String prevPwd = config.getMatrixPassword();
        String prevHomeserver = config.matrixServer;
        String prevRoomID = config.matrixRoomId;

        if (!config.load()) return false;

        if (!config.matrixUserId.equals(prevUser)
                || !config.getMatrixPassword().equals(prevPwd)
                || !config.matrixServer.equals(prevHomeserver)
                || !config.matrixRoomId.equals(prevRoomID)) {
            sender.sendMessage("§e[MatrixSpigotBridge] §aMatrix credentials changed! §7Please run §a/msb restart §7to connect with the new credentials.");
            return true;
        }
        sender.sendMessage("§e[MatrixSpigotBridge] §aConfiguration reloaded.");
        return true;
    }
}