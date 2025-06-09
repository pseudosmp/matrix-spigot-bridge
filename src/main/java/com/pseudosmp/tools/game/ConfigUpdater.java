package com.pseudosmp.tools.game;

import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Level;


public class ConfigUpdater {
    public static boolean isOlderConfigVersion(Plugin plugin) {
        String configVersion = plugin.getConfig().getString("common.configVersion", "0.0.0");

        // Get version of config stored in resources
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
        return false; // equal
    }

    private static HashMap<String, Object> getCurrentConfig(Plugin plugin) {
        HashMap<String, Object> config = new HashMap<>();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        for (String key : currentConfig.getKeys(true)) {
            config.put(key, currentConfig.get(key));
        }
        return config;
    }

    public static boolean updateConfig(Plugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        HashMap<String, Object> currentConfig = getCurrentConfig(plugin);

        // Move old config file to backup
        File backupFile = new File(plugin.getDataFolder(), "config.old.yml");
        if (!configFile.renameTo(backupFile)) {
            plugin.getLogger().log(Level.SEVERE, "Config Migration: Failed to rename old config file to config.old.yml");
            return false;
        }
        plugin.getLogger().log(Level.INFO, "Config Migration: Old config file backed up to config.old.yml");

        // Replace old config file with new default config
        try (InputStream inputStream = plugin.getResource("config.yml")) {
            java.nio.file.Files.copy(inputStream, configFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Config Migration: Failed to write new default config file.", e);
            return false;
        }

        FileConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);
        for (String key : currentConfig.keySet()) {
            newConfig.set(key, currentConfig.get(key));
        }

        try {
            newConfig.save(configFile);
            plugin.getLogger().log(Level.INFO, "Config Migration: Completed Successfully!");
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Config Migration: Failed to save updated config file.", e);
            return false;
        }
    }
}
