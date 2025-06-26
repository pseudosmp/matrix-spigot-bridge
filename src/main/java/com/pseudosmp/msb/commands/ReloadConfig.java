package com.pseudosmp.msb.commands;

import com.pseudosmp.msb.MatrixSpigotBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.pseudosmp.tools.game.ConfigUtils;

public class ReloadConfig implements CommandExecutor {
    private final ConfigUtils config;
    private final MatrixSpigotBridge plugin;

    public ReloadConfig(MatrixSpigotBridge plugin) {
        this.plugin = plugin;
        this.config = MatrixSpigotBridge.config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prevUser = config.matrixUserId;
        String prevPwd = config.getMatrixPassword();
        String prevHomeserver = config.matrixServer;
        String prevRoomID = config.matrixRoomId;
        int prevTopicUpdateInterval = config.matrixTopicUpdateInterval;

        if (!config.load()) {
            sender.sendMessage("§e[MatrixSpigotBridge] §cFailed to reload configuration. Please check the console for errors.");
            return false;
        }

        if (!config.matrixUserId.equals(prevUser)
                || !config.getMatrixPassword().equals(prevPwd)
                || !config.matrixServer.equals(prevHomeserver)
                || !config.matrixRoomId.equals(prevRoomID)) {
            sender.sendMessage("§e[MatrixSpigotBridge] §aMatrix credentials changed! §7Please run §a/msb restart §7to connect with the new credentials.");
            return true;
        }

        if (config.matrixTopicUpdateInterval != prevTopicUpdateInterval) {
            plugin.updateRoomTopicAsync(success -> {});
        }

        sender.sendMessage("§e[MatrixSpigotBridge] §aConfiguration reloaded.");
        return true;
    }
}