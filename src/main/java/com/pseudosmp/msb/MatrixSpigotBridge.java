package com.pseudosmp.msb;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

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

import com.pseudosmp.msb.MsbCommand;
import com.pseudosmp.tools.bridge.HttpsTrustAll;
import com.pseudosmp.tools.bridge.Matrix;
import com.pseudosmp.tools.game.MinecraftChatListener;
import com.pseudosmp.tools.game.PlayerEventsListener;

public class MatrixSpigotBridge extends JavaPlugin implements Listener {
	private java.util.logging.Logger logger;
	protected BukkitRunnable matrixPoller;
	public BukkitTask matrixPollerTask = null;
	public Matrix matrix;

	public boolean canUsePapi = false;
	public boolean cacheMatrixDisplaynames = false;

	public Matrix getMatrix() {
		return matrix;
	}

	public void startBridgeAsync(CommandSender sender) {
		logger.info("Connecting to Matrix server");

		// Cancel previous poller if running
		if (matrixPollerTask != null) {
			try {
				matrixPollerTask.cancel();
			} catch (IllegalStateException ignored) {}
			matrixPollerTask = null;
		}

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
			if (token != null && !token.isEmpty()) {
				logger.info("Access token found in access.yml");
				matrix.setAccessToken(token);
			} else {
				logger.info("No access token found, trying to login...");
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
				if (sender != null) {
					Bukkit.getScheduler().runTask(this, () ->
						sender.sendMessage("§e[MatrixSpigotBridge] §cInvalid configuration! (checking upper errors might help you)")
					);
				}
				return;
			}

			if (!matrix.joinRoom(getConfig().getString("matrix.room_id")) || !matrix.isValid()) {
				logger.log(Level.SEVERE, "Could not connect to server! Please check your configuration and run /msb restart!");
				if (sender != null) {
					Bukkit.getScheduler().runTask(this, () ->
						sender.sendMessage("§e[MatrixSpigotBridge] §cCould not connect to server! Please check your configuration and run /msb restart!")
					);
				}
				return;
			}

			logger.info("Connected to Matrix server as " + getConfig().getString("matrix.user_id") + " in room " + getConfig().getString("matrix.room_id"));

			// Start poller on main thread
			Bukkit.getScheduler().runTask(this, () -> {
				matrixPollerTask = matrixPoller.runTaskTimerAsynchronously(this, 0, getConfig().getInt("matrix.poll_delay") * 20);
			});

			if (sender != null) {
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
		}

		reloadConfig();

		if (getConfig().getBoolean("common.bstats_consent", true)) {
			int pluginId = 25993;
			Metrics metrics = new Metrics(this, pluginId);
			getLogger().info("bstats for MatrixSpigotBridge has been enabled. You can opt-out by disabling bstats in the plugin config.");
		}

		cacheMatrixDisplaynames = getConfig().getBoolean("common.cacheMatrixDisplaynames");

		if (getConfig().getBoolean("common.usePlaceholderApi") && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			canUsePapi = true;
			logger.info("PlaceholderAPI found and bound, you can use placeholders in messages");
		}

		MsbCommand msbCommand = new MsbCommand(this);
		getCommand("msb").setExecutor(msbCommand);
		getCommand("msb").setTabCompleter(msbCommand);

		matrixPoller = new BukkitRunnable() {
			protected String matrix_message_prefix = getConfig().getString("format.matrix_chat");
			protected String matrix_command_prefix = getConfig().getString("matrix.command_prefix", "!");

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
		};
		
		// Connect to Matrix Server
		if (!isFirstRun) {
			startBridgeAsync(null);
		}
		// Register event handlers
		getServer().getPluginManager().registerEvents(new MinecraftChatListener(this), this);
		getServer().getPluginManager().registerEvents(new PlayerEventsListener(this), this);

		logger.info("Started!");

		if (matrix != null && matrix.isValid()) {
			String start_message = getConfig().getString("format.server.start");
			if (start_message != null && !start_message.isEmpty())
				sendMessageToMatrix(start_message, "", null);
		}
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
		if (stop_message != null && !stop_message.isEmpty() && matrix != null && matrix.isValid())
			sendMessageToMatrix(stop_message, "", null);
	}
}
