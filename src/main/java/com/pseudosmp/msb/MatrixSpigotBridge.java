package com.pseudosmp.msb;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bstats.bukkit.Metrics;
import org.json.JSONArray;
import org.json.JSONObject;

import me.clip.placeholderapi.PlaceholderAPI;

import com.pseudosmp.tools.bridge.HttpsTrustAll;
import com.pseudosmp.tools.bridge.Matrix;
import com.pseudosmp.tools.game.MinecraftChatListener;
import com.pseudosmp.tools.game.PlayerEventsListener;

public class MatrixSpigotBridge extends JavaPlugin implements Listener {
	private java.util.logging.Logger logger;
	private final Set<String> relayedEventIDs = ConcurrentHashMap.newKeySet();
	private static final int MAX_RELAYED_EVENTS = 120; // Limit memory usage
	public BukkitTask matrixPollerTask = null;
	public Matrix matrix;

	public boolean canUsePapi = false;
	public boolean cacheMatrixDisplaynames = false;
	public String matrixMessagePrefix = "";
	public String matrixCommandPrefix = "!";

	public Matrix getMatrix() {
		return matrix;
	}

	private boolean isOlderConfigVersion() {
        String configVersion = this.getConfig().getString("common.configVersion", "0.0.0");

        // Get version of config stored in resources
        InputStream inputStream = this.getResource("config.yml");
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

	public void startBridgeAsync(CommandSender sender, Consumer<Boolean> callback) {
		logger.info("Connecting to Matrix server");

		// Cancel previous poller if running
		if (matrixPollerTask != null) {
			try {
				matrixPollerTask.cancel();
			} catch (IllegalStateException ignored) {}
			matrixPollerTask = null;
		}
		BukkitRunnable poller = new BukkitRunnable(){
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
							String event_id = obj.optString("event_id", null);
							if (event_id != null && relayedEventIDs.contains(event_id)) {
								// Already processed this event, skip it
								return;
							}
							if (event_id != null) {
								// Add event ID to the set
								relayedEventIDs.add(event_id);
								// Limit memory usage by removing old events
								if (relayedEventIDs.size() > MAX_RELAYED_EVENTS) {
									relayedEventIDs.remove(relayedEventIDs.iterator().next());
								}
							}

							String sender_address = matrix.getDisplayName(obj.getString("sender"), !cacheMatrixDisplaynames);
							String body = obj.getJSONObject("content").getString("body");

							if (body.startsWith(matrixCommandPrefix)) {
								String command = body.substring(matrixCommandPrefix.length()).trim();
								matrix.handleCommand(command, sender_address);
							} else sendMessageToMinecraft(
								matrixMessagePrefix,
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
		};

		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
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
					logger.log(Level.SEVERE, "This will not prevent the bridge from working, but new access tokens will be generated every time.");
				}
			} else {
				tokenConfiguration = YamlConfiguration.loadConfiguration(tokenFile);
			}

			//  Check if we already have an access token
			String token = tokenConfiguration.getString("token");
			boolean loginSuccess = false;
			if (token != null && !token.isEmpty()) {
				logger.info("Access token found in access.yml");
				matrix.setAccessToken(token);
				loginSuccess = true;
			} else {
				logger.info("No access token found, trying to login...");
				loginSuccess = matrix.login(getConfig().getString("matrix.password"));
				if (loginSuccess) {
					tokenConfiguration.set("token", matrix.getAccessToken());
					try {
						tokenConfiguration.save(tokenFile);
						logger.info("Token saved in access.yml");
					} catch (IOException e) {
						logger.log(Level.SEVERE, "Could not save config to " + tokenFile, e);
					}
				}
			}

			boolean configValid = getConfig().contains("matrix.room_id") && getConfig().contains("matrix.user_id")
					&& !getConfig().getString("matrix.room_id").isEmpty() && !getConfig().getString("matrix.user_id").isEmpty();

			boolean connected = false;
			if (loginSuccess && configValid && matrix.joinRoom(getConfig().getString("matrix.room_id")) && matrix.isValid()) {
				connected = true;
			}

			if (connected) {
				logger.info("Connected to Matrix server as " + getConfig().getString("matrix.user_id") + " in room " + getConfig().getString("matrix.room_id"));
				// Start poller on main thread
				Bukkit.getScheduler().runTask(this, () -> {
					matrixPollerTask = poller.runTaskTimerAsynchronously(this, 0, getConfig().getInt("matrix.poll_delay") * 20);
				});
			} else {
				if (!configValid) {
					logger.log(Level.SEVERE, "Invalid configuration! (checking upper errors might help you)");
					if (sender != null) {
						Bukkit.getScheduler().runTask(this, () ->
							sender.sendMessage("§e[MatrixSpigotBridge] §cInvalid configuration! (checking upper errors might help you)")
						);
					}
				} else {
					logger.log(Level.SEVERE, "Could not connect to server! Please check your configuration and run /msb restart!");
					if (sender != null) {
						Bukkit.getScheduler().runTask(this, () ->
							sender.sendMessage("§e[MatrixSpigotBridge] §cCould not connect to server! Please check your configuration and run /msb restart!")
						);
					}
				}
			}

			// Notify callback on main thread
			if (callback != null) {
				boolean finalConnected = connected;
				Bukkit.getScheduler().runTask(this, () -> callback.accept(finalConnected));
			}

			// Optionally, send success message to sender
			if (connected && sender != null) {
				Bukkit.getScheduler().runTask(this, () ->
					sender.sendMessage("§e[MatrixSpigotBridge] §aMatrix bridge connected!")
				);
			}
		});
	}
	@Override
	public void onEnable() {
		boolean isFirstRun = false;
		logger = getLogger();

		logger.info("Starting MatrixSpigotBridge");

		File configFile = new File(getDataFolder(), "config.yml");
		boolean newConfig = !configFile.exists();
		this.saveDefaultConfig();
		if (newConfig) {
			isFirstRun = true;
			String firstRun = "Config generated for the first time! Please edit config.yml and run /msb restart to start the bridge.";
			getLogger().warning(firstRun);
			// Optionally notify online ops:
			Bukkit.getOnlinePlayers().stream()
				.filter(Player::isOp)
				.forEach(p -> p.sendMessage("§e[MatrixSpigotBridge] " + firstRun));
		} else if (isOlderConfigVersion()) {
			getLogger().warning("Your config.yml is outdated! Please update it to the latest version.");
			// Copy resource config.yml to data folder as config.new.yml
			try {
				File newConfigFile = new File(getDataFolder(), "config.new.yml");
				if (newConfigFile.exists()) {
					newConfigFile.delete();
				}
				// Save the resource config.yml as config.new.yml
				InputStream in = getResource("config.yml");
				if (in != null) {
					java.nio.file.Files.copy(
						in,
						newConfigFile.toPath(),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING
					);
					in.close();
					getLogger().warning("You can find the latest config.yml in the plugin's folder as \"config.new.yml\".");
				} else {
					getLogger().warning("Resource config.yml not found in jar.");
				}
			} catch (Exception e) {
				getLogger().warning("Failed to save config.new.yml: " + e.getMessage());
				getLogger().warning("Manually update by checking https://github.com/pseudosmp/matrix-spigot-bridge/blob/master/src/main/resources/config.yml");
			}
		}

		reloadConfig();

		if (getConfig().getBoolean("common.bstats_consent", true)) {
			int pluginId = 25993;
			@SuppressWarnings("unused")
			Metrics metrics = new Metrics(this, pluginId);
			getLogger().info("bstats for MatrixSpigotBridge has been enabled. You can opt-out by disabling bstats in the plugin config.");
		}

		cacheMatrixDisplaynames = getConfig().getBoolean("common.cacheMatrixDisplaynames");

		if (getConfig().getBoolean("common.usePlaceholderApi") && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			canUsePapi = true;
			logger.info("PlaceholderAPI found and bound, you can use placeholders in messages");
		}

		matrixMessagePrefix = getConfig().getString("format.matrix_chat");
		matrixCommandPrefix = getConfig().getString("matrix.command_prefix", "!");
		
		MsbCommand msbCommand = new MsbCommand(this);
		getCommand("msb").setExecutor(msbCommand);
		getCommand("msb").setTabCompleter(msbCommand);
		
		// Connect to Matrix Server
		if (!isFirstRun) {
			startBridgeAsync(null, success -> {
				if (success) {
					String start_message = getConfig().getString("format.server.start");
					if (start_message != null && !start_message.isEmpty())
						sendMessageToMatrix(start_message, "", null);
				}
			});
		}
		// Register event handlers
		getServer().getPluginManager().registerEvents(new MinecraftChatListener(this), this);
		getServer().getPluginManager().registerEvents(new PlayerEventsListener(this), this);

		logger.info("Startup sequence complete!");
	}

	public void sendMessageToMatrix(String format, String message, Player player) {
		if (matrix == null || !matrix.isValid()) {
			// Ignoring for now, not connected to matrix server yet
			return;
		}
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
		if (stop_message != null && !stop_message.isEmpty() && matrix != null) {
			Thread shutdownThread = new Thread(() -> {
				try {
					sendMessageToMatrix(stop_message, "", null);
				} catch (Exception ignored) {}
			});
			shutdownThread.start();
			try {
				shutdownThread.join(5000); // Wait up to 5 seconds for the message to send
				if (shutdownThread.isAlive()) {
					logger.warning("Shutdown message did not send in time, forcefully disabling (ignore the following error)...");
					shutdownThread.interrupt();
				}
			} catch (InterruptedException ignored) {}
		}
	}
}
