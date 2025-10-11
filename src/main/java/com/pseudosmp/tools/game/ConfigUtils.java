package com.pseudosmp.tools.game;

import com.pseudosmp.msb.MatrixSpigotBridge;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigUtils {
    private final MatrixSpigotBridge plugin;
    private final Logger logger;
    public final boolean isFirstRun;

    // Persistent config values
    public boolean bstatsConsent;
    public String matrixServer;
    public String matrixUserId;
    public String matrixRoomId;
    public String matrixCommandPrefix;
    public int matrixPollDelay;
    public int matrixTopicUpdateInterval;
    public int nextTopicIndex;
    public int matrixCharLimit;
    public int matrixLineLimit;
    public List<String> matrixAvailableCommands;
    public List<String> matrixUserBlacklist;
    public List<String> matrixRegexBlacklist;
    public List<String> matrixRoomTopicPool;
    public boolean cacheMatrixDisplaynames;
    public boolean canUsePapi;
    private Map<String, Object> format = Collections.emptyMap();

    public ConfigUtils(MatrixSpigotBridge plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.isFirstRun = !plugin.getDataFolder().exists() && !new File(plugin.getDataFolder(), "config.yml").exists();
        checkAndUpdateConfig();
    }

    public boolean load() {
        try {
            plugin.reloadConfig();
            FileConfiguration config = plugin.getConfig();

            bstatsConsent = config.getBoolean("common.bstats_consent", true);
            matrixServer = config.getString("matrix.server");
            matrixUserId = config.getString("matrix.user_id");
            matrixRoomId = config.getString("matrix.room_id");
            matrixPollDelay = config.getInt("matrix.poll_delay");
            matrixCommandPrefix = config.getString("matrix.command_prefix", "!");
            matrixAvailableCommands = config.getStringList("matrix.available_commands");
            matrixTopicUpdateInterval = config.getInt("matrix.topic_update_interval", -1);
            matrixRoomTopicPool = config.getStringList("format.room_topic");
            nextTopicIndex = 0; // Resetting to 0 on each load, will be updated in updateRoomTopicAsync
            matrixUserBlacklist = config.getStringList("matrix.user_blacklist");
            matrixRegexBlacklist = config.getStringList("matrix.regex_blacklist");
            matrixCharLimit = config.getInt("matrix.character_limit", 256);
            matrixLineLimit = config.getInt("matrix.line_limit", 8);
            cacheMatrixDisplaynames = config.getBoolean("matrix.cache_displaynames", true);

            canUsePapi = config.getBoolean("common.usePlaceholderApi") 
                                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

            ConfigurationSection formatSection = config.getConfigurationSection("format");
            if (formatSection != null) format = formatSection.getValues(true);

            /* Config Checks */

            // If any of these are empty, why would there be a point to this plugin?
            if (!isFirstRun) {
                if (matrixServer == null || matrixServer.isEmpty()) {
                    logger.severe("Matrix server URL is not set! Please set it and run /msb restart!");
                    return false;
                }
                if (matrixUserId == null || matrixUserId.isEmpty()) {
                    logger.severe("Matrix user ID is not set! Please set it and run /msb restart!");
                    return false;
                }
                if (matrixRoomId == null || matrixRoomId.isEmpty()) {
                    logger.severe("Matrix room ID is not set! Please set it and run /msb restart!");
                    return false;
                }
            }
            // Trailing / will lead the requests to http://example.com//_matrix...
            if (matrixServer.endsWith("/")) {
                logger.warning("Matrix server URL should not end with a slash (/). Removing trailing slash automatically.");
            }
            while (matrixServer.endsWith("/")) {
                matrixServer = matrixServer.substring(0, matrixServer.length() - 1);
                config.set("matrix.server", matrixServer);
                plugin.saveConfig();
            }
            // Regex validation
            for (String regex : matrixRegexBlacklist) {
                try {
                    Pattern.compile(regex);
                } catch (PatternSyntaxException e) {
                    logger.warning("Invalid regex found in matrix.regex_blacklist: " + regex);
                    matrixRegexBlacklist.remove(regex);
                }
            }
            // Moving this here from onEnable()
            if (canUsePapi) {
                logger.info("PlaceholderAPI is enabled. You can use placeholders in messages!");
            }
            // Validate TIME placeholders in format strings
            validateTimePlaceholders(formatSection);
            // bstats consent disclaimer
            if (bstatsConsent && isFirstRun) {
                logger.warning("bstats Plugin Analytics is enabled by default. " + 
                    "If you wish to opt-out and defer it from loading even once, set bstats_consent to false in the config "+
                    "before the next restart. This message will only be shown once."
                );
            }
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
            logger.severe("Your config.yml is outdated! Please update it to the latest version and run /msb restart!");
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
                    logger.warning("You can find the latest config.yml in the plugin's folder as \"config.new.yml\". "
                                    + "The plugin may not work as intended if you do not wish to update the config.");
                } else {
                    logger.severe("Resource config.yml not found in jar.");
                }
            } catch (Exception e) {
                logger.severe("Failed to save config.new.yml: " + e.getMessage());
                logger.severe("Manually update by checking https://github.com/pseudosmp/matrix-spigot-bridge/blob/master/src/main/resources/config.yml");
            }
        }
    }

    private boolean isOlderConfigVersion() {
        String configVersion = plugin.getConfig().getString("common.configVersion", "0.0.0");
        InputStream inputStream = plugin.getResource("config.yml");
        YamlConfiguration resourceConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream));
        String pluginVersion = resourceConfig.getString("common.configVersion", plugin.getDescription().getVersion());

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

    public String getFormat(String key) {
        Object value = format.get(key);
        return value != null ? value.toString() : "";
    }

    private void validateTimePlaceholders(ConfigurationSection formatSection) {
        if (formatSection == null) return;
        
        Pattern timePattern = Pattern.compile("\\{TIME:([^}]+)\\}");
        
        for (String key : formatSection.getKeys(true)) {
            Object value = formatSection.get(key);
            if (value instanceof String) {
                String message = (String) value;
                Matcher matcher = timePattern.matcher(message);
                
                while (matcher.find()) {
                    String pattern = matcher.group(1);
                    try {
                        DateTimeFormatter.ofPattern(pattern);
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid TIME placeholder format in '" + key + "': {TIME:" + pattern + "}");
                        logger.warning("  Error: " + e.getMessage());
                    }
                }
            }
        }
    }
}
