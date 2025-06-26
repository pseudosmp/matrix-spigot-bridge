package com.pseudosmp.tools.game;

import com.pseudosmp.msb.MatrixSpigotBridge;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigUtils {
    private final MatrixSpigotBridge plugin;
    private final Logger logger;

    // Persistent config values
    public String matrixServer;
    public String matrixUserId;
    public String matrixRoomId;
    public int matrixPollDelay;
    public int matrixTopicUpdateInterval;
    public String matrixCommandPrefix;
    public List<String> matrixAvailableCommands;
    public List<String> matrixUserBlacklist;
    public List<String> matrixRegexBlacklist;
    public boolean cacheMatrixDisplaynames;
    public boolean canUsePapi;
    private Map<String, Object> format = Collections.emptyMap();

    public ConfigUtils(MatrixSpigotBridge plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        checkAndUpdateConfig();
    }

    public Boolean load() {
        try {
            plugin.reloadConfig();
            FileConfiguration config = plugin.getConfig();

            matrixServer = config.getString("matrix.server");
            if (matrixServer.endsWith("/")) {
                matrixServer = matrixServer.substring(0, matrixServer.length() - 1);
                config.set("matrix.server", matrixServer);
                plugin.saveConfig();
            }

            matrixUserId = config.getString("matrix.user_id");
            matrixRoomId = config.getString("matrix.room_id");
            matrixPollDelay = config.getInt("matrix.poll_delay");
            matrixCommandPrefix = config.getString("matrix.command_prefix", "!");
            matrixAvailableCommands = config.getStringList("matrix.available_commands");
            matrixTopicUpdateInterval = config.getInt("matrix.topic_update_interval", 5);
            matrixUserBlacklist = config.getStringList("common.user_blacklist");
            matrixRegexBlacklist = config.getStringList("common.regex_blacklist");
            cacheMatrixDisplaynames = config.getBoolean("common.cacheMatrixDisplaynames");
            canUsePapi = config.getBoolean("common.usePlaceholderApi") 
                                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

            ConfigurationSection formatSection = config.getConfigurationSection("format");
            if (formatSection != null) format = formatSection.getValues(true);

            return true;
        } catch (Exception e) {
            logger.severe("Failed to load config.yml: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void checkAndUpdateConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        boolean newConfig = !configFile.exists();

        plugin.saveDefaultConfig();
        if (newConfig) {
            String firstRun = "Config generated for the first time! Please edit config.yml and run /msb restart to start the bridge.";
            logger.warning(firstRun);
            Bukkit.getOnlinePlayers().stream()
                .filter(Player::isOp)
                .forEach(p -> p.sendMessage("§e[MatrixSpigotBridge] " + firstRun));
        } else if (isOlderConfigVersion()) {
            logger.warning("Your config.yml is outdated! Please update it to the latest version.");
            try {
                File newConfigFile = new File(plugin.getDataFolder(), "config.new.yml");
                if (newConfigFile.exists()) {
                    newConfigFile.delete();
                }
                InputStream in = plugin.getResource("config.yml");
                if (in != null) {
                    java.nio.file.Files.copy(
                        in,
                        newConfigFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    );
                    in.close();
                    logger.warning("You can find the latest config.yml in the plugin's folder as \"config.new.yml\".");
                } else {
                    logger.warning("Resource config.yml not found in jar.");
                }
            } catch (Exception e) {
                logger.warning("Failed to save config.new.yml: " + e.getMessage());
                logger.warning("Manually update by checking https://github.com/pseudosmp/matrix-spigot-bridge/blob/master/src/main/resources/config.yml");
            }
        }
    }

    private boolean isOlderConfigVersion() {
        String configVersion = plugin.getConfig().getString("common.configVersion", "0.0.0");
        InputStream inputStream = plugin.getResource("config.yml");
        YamlConfiguration resourceConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream));
        String pluginVersion = resourceConfig.getString("common.configVersion", "0.0.0");

        String[] curr = configVersion.split("\\.");
        String[] target = pluginVersion.split("\\.");

        int len = Math.max(curr.length, target.length);
        for (int i = 0; i < len; i++) {
            int currPart = i < curr.length ? Integer.parseInt(curr[i]) : 0;
            int targetPart = i < target.length ? Integer.parseInt(target[i]) : 0;
            if (currPart < targetPart) return true;
            if (currPart > targetPart) return false;
        }
        return false;
    }

    public String getMatrixPassword() { return plugin.getConfig().getString("matrix.password", ""); }

    public boolean getFormatSettingBool(String key) {
        Object value = format.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false; // Default to false if not set or not a boolean
    }

    public String getMessage(String key) {
        Object value = format.get(key);
        return value != null ? value.toString() : null;
    }
}
