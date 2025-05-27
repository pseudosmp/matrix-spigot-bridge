package com.pseudosmp.msb;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bstats.bukkit.Metrics;
import org.json.JSONArray;
import org.json.JSONObject;

import me.clip.placeholderapi.PlaceholderAPI;
import com.pseudosmp.tools.HttpsTrustAll;
import com.pseudosmp.tools.Matrix;

public class MatrixSpigotBridge extends JavaPlugin implements Listener {
	private java.util.logging.Logger logger;
	private Matrix matrix;

	private boolean isVersionOlder(String current, String target) {
		if (current == null || current.isEmpty()) return true;
		String[] c = current.split("\\.");
		String[] t = target.split("\\.");
		for (int i = 0; i < Math.max(c.length, t.length); i++) {
			int cv = i < c.length ? Integer.parseInt(c[i]) : 0;
			int tv = i < t.length ? Integer.parseInt(t[i]) : 0;
			if (cv < tv) return true;
			if (cv > tv) return false;
		}
		return false;
	}

	protected boolean canUsePapi = false;
	protected boolean cacheMatrixDisplaynames = false;

	public Matrix getMatrix() {
		return matrix;
	}

	@Override
	public void onEnable() {
		logger = getLogger();

		logger.info("Starting MatrixSpigotBridge");

		// Save default config if not present
		this.saveDefaultConfig();

		// Load config and check version
		File configFile = new File(getDataFolder(), "config.yml");
		FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
		String currentVersion = config.getString("configVersion", "");
		String defaultVersion = "";
		try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
			if (in != null) {
				YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
				defaultVersion = defaultConfig.getString("configVersion", "");
			}
		} catch (IOException e) {
			logger.warning("Could not read default config version from resources.");
		}

		if (isVersionOlder(currentVersion, defaultVersion)) {
			// ...migration logic as before...
			File backupFile = new File(getDataFolder(), "config-old.yml");
			if (configFile.exists()) {
				configFile.renameTo(backupFile);
				logger.warning("Old config.yml backed up as config-old.yml");
			}
			saveResource("config.yml", true);
			FileConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);
			for (String key : config.getKeys(true)) {
				if (newConfig.contains(key)) {
					newConfig.set(key, config.get(key));
				}
			}
			newConfig.set("configVersion", defaultVersion);
			try {
				newConfig.save(configFile);
				logger.info("Migrated config.yml to new version, old config preserved as config-old.yml");
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Failed to save migrated config.yml", e);
				Bukkit.getPluginManager().disablePlugin(this);
				return;
			}
			reloadConfig();
		}

        if (getConfig().getBoolean("bstats_consent", true)) {
            int pluginId = 25993;
            Metrics metrics = new Metrics(this, pluginId);
            getLogger().info("bstats for MatrixSpigotBridge has been enabled. You can opt-out by disabling bstats in the plugin config.");
        }

		cacheMatrixDisplaynames = getConfig().getBoolean("common.cacheMatrixDisplaynames");

		if (getConfig().getBoolean("common.usePlaceholderApi") && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			canUsePapi = true;
			logger.info("PlaceholderAPI found and bound, you can use placeholders in messages");
		}

		logger.info("Connecting to Matrix server");

		HttpsTrustAll.ignoreAllSSL();

		matrix = new Matrix(getConfig().getString("matrix.server"), getConfig().getString("matrix.user_id"));
		// Init token file
		File tokenFile = new File(getDataFolder(), "access.yml");
		FileConfiguration tokenConfiguration;

		if (!tokenFile.exists()) {
			tokenConfiguration = new YamlConfiguration();
			tokenConfiguration.set("token", "");

			try {
				tokenConfiguration.save(tokenFile);
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Could not save config to " + tokenFile, e);
			}
		} else {
			tokenConfiguration = YamlConfiguration.loadConfiguration(tokenFile);
		}

		//  Check if we already have an access token
		String token = tokenConfiguration.getString("token");
		if (token != null && !token.isEmpty()) {
			logger.info("Access token found in access.yml");
			matrix.setAccessToken(token);
		} else {
			logger.info("No access token found, trying to login...");

			//
			if (matrix.login(getConfig().getString("matrix.password"))) {
				tokenConfiguration.set("token", matrix.getAccessToken());
				try {
					tokenConfiguration.save(tokenFile);
					logger.info("Token saved in access.yml");
				} catch (IOException e) {
					logger.log(Level.SEVERE, "Could not save config to " + tokenFile, e);
				}
			}
		}

		// Check all configuration are ok
		if (
			!getConfig().contains("matrix.room_id") || !getConfig().contains("matrix.user_id")
			|| getConfig().getString("matrix.room_id").isEmpty() || getConfig().getString("matrix.user_id").isEmpty()
		) {
			logger.log(Level.SEVERE, "Invalid configuration! (checking upper errors might help you)");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		if (!matrix.joinRoom(getConfig().getString("matrix.room_id")) || !matrix.isValid()) {
			logger.log(Level.SEVERE, "Could not connect to server! Please check your configuration!");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		// Register event handlers
		getServer().getPluginManager().registerEvents(new MinecraftChatListener(this), this);
		getServer().getPluginManager().registerEvents(new PlayerEventsListener(this), this);

		new BukkitRunnable() {
			protected String matrix_message_prefix = getConfig().getString("format.matrix_chat");
			protected String matrix_command_prefix = getConfig().getString("matrix.command_prefix");

			public void run() {
				JSONArray messages = new JSONArray();

				try {
					messages = matrix.getLastMessages();
				} catch (Exception e) {
					// Matrix server gone away, do nothing
				}

				try {
					if (!messages.isEmpty()) {
						messages.forEach(o -> {
							JSONObject obj = (JSONObject) o;

							String sender_address = matrix.getDisplayName(obj.getString("sender"), !cacheMatrixDisplaynames);
							String body = obj.getJSONObject("content").getString("body");

							if (body.startsWith(matrix_command_prefix)) {
								String command = body.substring(matrix_command_prefix.length()).trim();
								matrix.handleCommand(command, sender_address);
							} else sendMessageToMinecraft(
								matrix_message_prefix,
								obj.getJSONObject("content").getString("body"),
								null,
								sender_address
							);
						});
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.runTaskTimerAsynchronously(this, 0, getConfig().getInt("matrix.poll_delay") * 20);

		logger.info("Started!");

		String start_message = getConfig().getString("format.server.start");
		if (start_message != null && !start_message.isEmpty())
			sendMessageToMatrix(start_message, "", null);
	}

	public void sendMessageToMatrix(String format, String message, Player player) {
		if (canUsePapi)
			format = PlaceholderAPI.setPlaceholders(player, format);

		matrix.sendMessage(format
			.replace("{PLAYERNAME}", (player != null) ? player.getName() : "???")
			.replace("{MESSAGE}", message)
		);
	}

	public void sendMessageToMinecraft(String format, String message, Player player) {
		sendMessageToMinecraft(format, message, player, "???");
	}

	public void sendMessageToMinecraft(String format, String message, Player player, String defaultPlayername) {
		if (canUsePapi)
			format = PlaceholderAPI.setPlaceholders(player, format);

		Bukkit.broadcastMessage(format
			.replace("{MATRIXNAME}", (player != null) ? player.getName() : defaultPlayername)
			.replace("{MESSAGE}", message)
		);
	}

	@Override
	public void onDisable() {
		String stop_message = getConfig().getString("format.server.stop");
		if (stop_message != null && !stop_message.isEmpty())
			sendMessageToMatrix(stop_message, "", null);
	}
}
