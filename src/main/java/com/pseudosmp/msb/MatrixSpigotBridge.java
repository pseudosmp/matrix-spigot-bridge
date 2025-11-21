package com.pseudosmp.msb;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import org.json.JSONArray;
import org.json.JSONObject;

import com.pseudosmp.tools.bridge.HttpsTrustAll;
import com.pseudosmp.tools.bridge.Matrix;
import com.pseudosmp.tools.bridge.commands.MatrixCommandHandler;
import com.pseudosmp.tools.game.MinecraftChatListener;
import com.pseudosmp.tools.game.PlayerEventsListener;
import com.pseudosmp.tools.game.ConfigUtils;
import com.pseudosmp.tools.formatting.MessageFormatter;

import org.bstats.bukkit.Metrics;

public class MatrixSpigotBridge extends JavaPlugin implements Listener {
	private java.util.logging.Logger logger;

	private final Set<String> relayedEventIDs = ConcurrentHashMap.newKeySet();
	private static final int MAX_RELAYED_EVENTS = 120; // Limit memory usage

	private MinecraftChatListener minecraftChatListener;
	private PlayerEventsListener playerEventsListener;
	private BukkitTask establishConnection;
	private BukkitTask matrixPollerTask;
	private BukkitTask topicUpdaterTask;

	public static ConfigUtils config;
	private Matrix matrix;
	public static MessageFormatter formatter;
	private MatrixCommandHandler commandHandler;

	public Matrix getMatrix() {
		return matrix;
	}
	
	public static JavaPlugin getInstance() {
		return JavaPlugin.getPlugin(MatrixSpigotBridge.class);
	}

	public void startBridgeAsync(CommandSender sender, Consumer<Boolean> callback) {
		logger.info("Connecting to Matrix server");

		// Cancel previous connection attempt if running
		if (establishConnection != null) {
			logger.warning("Reconnection requested while already connecting. Forcefully reconnecting...");
			if (sender instanceof Player) {
				Bukkit.getScheduler().runTask(this, () ->
					sender.sendMessage("§e[MatrixSpigotBridge] §cThere is already a connection attempt in progress, forcefully reconnecting...")
				);
			}
			try {
				establishConnection.cancel();
			} catch (IllegalStateException ignored) {}
			establishConnection = null;
		}

		cancelAllTasks();
		unregisterEventListeners();

		BukkitRunnable poller = new BukkitRunnable(){
			@Override
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

							String sender_address = matrix.getDisplayName(obj.getString("sender"), !config.cacheMatrixDisplaynames);
							String body = obj.getJSONObject("content").getString("body");
							String formattedBody = obj.getJSONObject("content").optString("formatted_body", "");

						if (body.startsWith(config.matrixCommandPrefix)) {
							String command = body.substring(config.matrixCommandPrefix.length()).trim();
							if (commandHandler != null) {
								commandHandler.handleCommand(command, sender_address, event_id);
							}
						} else sendMessageToMinecraft(
								config.getFormat("matrix_chat"),
								event_id, body, formattedBody,
								null, sender_address
							);
						});
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

		// Load access token from file or create if it does not exist
		File tokenFile = new File(getDataFolder(), "access.yml");
		FileConfiguration tokenConfiguration;

		if (!tokenFile.exists()) {
			tokenConfiguration = new YamlConfiguration();
			tokenConfiguration.set("token", "");
			try {
				tokenConfiguration.save(tokenFile);
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Could not create token file " + tokenFile, e);
				logger.log(Level.SEVERE, "This will not prevent the bridge from working, but new access tokens will be generated every time.");
			}
		} else {
			tokenConfiguration = YamlConfiguration.loadConfiguration(tokenFile);
		}
		String token = tokenConfiguration.getString("token");

		establishConnection = Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			HttpsTrustAll.ignoreAllSSL();

			matrix = new Matrix(config.matrixServer, config.matrixUserId);

			boolean loginSuccess = false;
			if (token != null && !token.isEmpty()) {
				logger.info("Access token found in access.yml");
				matrix.setAccessToken(token);
				loginSuccess = matrix.joinRoom(config.matrixRoomId);
				if (!loginSuccess) {
					logger.warning("Access token is invalid or expired, clearing token...");
					matrix.setAccessToken("");
					loginSuccess = tryPasswordLogin(matrix, sender);
				}
			} else {
				logger.info("No access token found, trying to login...");
				loginSuccess = tryPasswordLogin(matrix, sender);
			}

			boolean connected = false;
			if (loginSuccess && matrix.isConnected()) {
				Bukkit.getScheduler().runTask(this, () -> {
					tokenConfiguration.set("token", matrix.getAccessToken());
					try {
						tokenConfiguration.save(tokenFile);
						if (config.getMatrixPassword() != null && !config.getMatrixPassword().isEmpty())
							logger.info("Logged in with token and saved it to access.yml. You can now remove the password from config.yml if you wish to.");
					} catch (IOException e) {
						logger.log(Level.SEVERE, "Could not save token to " + tokenFile, e);
					}
				});
				connected = true;
			}

			if (connected) {
				logger.info("Connected to Matrix server as " + config.matrixUserId + " in room " + config.matrixRoomId);
				// Initialize command handler
				commandHandler = new MatrixCommandHandler(matrix, config, formatter);
				// Register commands based on config
				commandHandler.registerDefaultCommands();
				// Start poller and register events on main thread
				Bukkit.getScheduler().runTask(this, () -> {
					minecraftChatListener = new MinecraftChatListener(this);
					playerEventsListener = new PlayerEventsListener(this);
					getServer().getPluginManager().registerEvents(minecraftChatListener, this);
					getServer().getPluginManager().registerEvents(playerEventsListener, this);
					matrixPollerTask = poller.runTaskTimerAsynchronously(this, 0, config.matrixPollDelay * 20);
				});
			} else {
				logger.log(Level.SEVERE, "Could not connect to server! Please check your configuration and run /msb restart!");
				if (sender != null) {
					Bukkit.getScheduler().runTask(this, () ->
						sender.sendMessage("§e[MatrixSpigotBridge] §cCould not connect to server! Please check your configuration and run /msb restart!")
					);
				}
			}
			establishConnection = null; // task is done

			// Notify callback on main thread
			if (callback != null) {
				boolean finalConnected = connected;
				Bukkit.getScheduler().runTask(this, () -> callback.accept(finalConnected));
			}

			if (connected && sender instanceof Player) {
				Bukkit.getScheduler().runTask(this, () ->
					sender.sendMessage("§e[MatrixSpigotBridge] §aMatrix bridge connected!")
				);
			}
		});
	}

	private boolean tryPasswordLogin(Matrix matrix, CommandSender sender) {
		String matrixPassword = config.getMatrixPassword();
		if (matrixPassword != null && !matrixPassword.isEmpty()) {
			try {
				matrix.login(matrixPassword);
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Failed to login with password: " + e.getMessage(), e);
				if (sender instanceof Player) {
					Bukkit.getScheduler().runTask(this, () ->
						sender.sendMessage("§e[MatrixSpigotBridge] §cFailed to login with password: " + e.getMessage())
					);
				}
				establishConnection = null; // task is done
				return false;
			}
			return matrix.joinRoom(config.matrixRoomId);
		} else {
			logger.severe("No valid access token or password found! Please set a password in config.yml or run /msb restart to generate a new access token.");
			if (sender != null) {
				Bukkit.getScheduler().runTask(this, () ->
					sender.sendMessage("§e[MatrixSpigotBridge] §cNo valid access token or password found! Please set a password in config.yml or run /msb restart to generate a new access token.")
				);
			}
			establishConnection = null; // task is done
			return false;
		}
	}

	public void updateRoomTopicAsync(Consumer<Boolean> callback) {
		// Cancel if previous updater task is running
		if (topicUpdaterTask != null) {
			try {
				topicUpdaterTask.cancel();
			} catch (IllegalStateException ignored) {}
			topicUpdaterTask = null;
		}
		config.nextTopicIndex = 0;
		BukkitRunnable roomTopicUpdater = new BukkitRunnable() {
			@Override
			public void run() {
				boolean randomize = config.getFormatSettingBool("randomize_topic");
				String room_topic = null;

				if (config.matrixRoomTopicPool != null && !config.matrixRoomTopicPool.isEmpty()) {
					if (randomize) {
						int idx = (int) (Math.random() * config.matrixRoomTopicPool.size());
						room_topic = config.matrixRoomTopicPool.get(idx);
					} else {
						room_topic = config.matrixRoomTopicPool.get(config.nextTopicIndex);
						config.nextTopicIndex = (config.nextTopicIndex + 1) % config.matrixRoomTopicPool.size();
					}
				}

				final boolean success;
				if (room_topic != null && !room_topic.isEmpty()) {
					// Room topic processing
					if (config.canUsePapi) {
						room_topic = formatter.replacePlaceholderAPI(null, room_topic);
						room_topic = formatter.stripMinecraftColors(room_topic);
					}
					success = matrix.setRoomTopic(room_topic);
				} else {
					success = true;
				}
				// Notify callback on main thread
				if (callback != null) {
					Bukkit.getScheduler().runTask(MatrixSpigotBridge.this, () -> callback.accept(success));
				}
			}
		};
		if (config.matrixTopicUpdateInterval > 0 && !config.getFormat("room_topic").isEmpty()) {
			Bukkit.getScheduler().runTask(this, () -> {
				topicUpdaterTask = roomTopicUpdater.runTaskTimerAsynchronously(this, 0, config.matrixTopicUpdateInterval * 60 * 20);
			});
		} else if (config.matrixTopicUpdateInterval == 0) {
			// If no topic update interval is set, run once immediately
			topicUpdaterTask = roomTopicUpdater.runTaskAsynchronously(this);
		} else if (config.matrixTopicUpdateInterval < 0) {
            // If negative, do not run the task, just callback true
            if (callback != null) {
                Bukkit.getScheduler().runTask(this, () -> callback.accept(true));
            }
        }
	}

	private void cancelAllTasks() {
		if (matrixPollerTask != null) {
			try {
				matrixPollerTask.cancel();
			} catch (IllegalStateException ignored) {}
			matrixPollerTask = null;
		}
		if (topicUpdaterTask != null) {
			try {
				topicUpdaterTask.cancel();
			} catch (IllegalStateException ignored) {}
			topicUpdaterTask = null;
		}
	}

	private void unregisterEventListeners() {
		if (minecraftChatListener != null) {
			try {
				org.bukkit.event.HandlerList.unregisterAll(minecraftChatListener);
			} catch (Exception ignored) {}
			minecraftChatListener = null;
		}
		if (playerEventsListener != null) {
			try {
				org.bukkit.event.HandlerList.unregisterAll(playerEventsListener);
			} catch (Exception ignored) {}
			playerEventsListener = null;
		}
	}

	public void sendMessageToMatrix(String format, String message, Player player) {
		if (matrix == null || establishConnection != null) {
			// Ignoring for now, not connected to matrix server yet
			return;
		}

		if (config.canUsePapi)
			format = formatter.replacePlaceholderAPI(player, format);
		if (config.getFormatSettingBool("reserialize_player")) {
			message = formatter.minecraftToMatrixHTML(message);
		}
		message = formatter.stripMinecraftColors(message);

		final String Format = formatter.replaceTimePlaceholders(format);
		final String Message = message;
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			matrix.postMessage(Format
					.replace("{PLAYERNAME}", (player != null) ? player.getName() : "???")
					.replace("{MESSAGE}", Message)
			);
		});
	}

	public void sendMessageToMinecraft(String format, String event_id, String message, String formattedMessage, Player player) {
		sendMessageToMinecraft(format, event_id, message, formattedMessage, player, "???");
	}

	public void sendMessageToMinecraft(String format, String event_id, String message, String formattedMessage, Player player, String defaultPlayername) {
		if (config.canUsePapi)
			format = formatter.replacePlaceholderAPI(player, format);

		// Check against regex blacklist
		for (String regex : config.matrixRegexBlacklist) {
			if (regex == null || regex.isEmpty()) continue;
			if (Pattern.compile(regex).matcher(message).find()) {
				logger.info(
					"Matrix: regex matched {" + regex + "} [" + 
					(player != null ? player.getName() : defaultPlayername) + "] " + message
				);
				matrix.addReaction(event_id, "❗");
				return;
			}
		}
		// Check against character and line limit
		if (config.matrixCharLimit > 0 && message.length() > config.matrixCharLimit) {
			logger.info(
				"Matrix: message too long [" +
				(player != null ? player.getName() : defaultPlayername) + "] " +
				message.replace("\n", " ").substring(0, 64) + "..."
			);
			matrix.addReaction(event_id, "❗");
			return;
		}
		if (config.matrixLineLimit > 0 && message.split("\n").length > config.matrixLineLimit) {
			logger.info(
				"Matrix: message too long [" +
				(player != null ? player.getName() : defaultPlayername) + "] " +
				message.replace("\n", " ").substring(0, Math.min(64, message.length())) + "..."
			);
			matrix.addReaction(event_id, "❗");
			return;
		}

		if (config.getFormatSettingBool("reserialize_matrix") && !formattedMessage.isEmpty())
			message = formatter.matrixHTMLToMinecraft(formattedMessage);

		Bukkit.broadcastMessage(formatter.replaceTimePlaceholders(format)
			.replace("{MATRIXNAME}", (player != null) ? player.getName() : defaultPlayername)
			.replace("{MESSAGE}", message)
		);
	}

	@Override
	public void onEnable() {
		logger = getLogger();

		logger.info("Starting MatrixSpigotBridge");
		config = new ConfigUtils(this);

		// Register commands
		MsbCommand msbCommand = new MsbCommand(this);
		getCommand("msb").setExecutor(msbCommand);
		getCommand("msb").setTabCompleter(msbCommand);

		if (!config.load()) {
			logger.severe("Failed to load config.yml! Please check the console for errors.");
			return;
		}

		// Initialize message formatter
		formatter = new MessageFormatter(logger, config.canUsePapi);

		if (config.bstatsConsent && !config.isFirstRun) {
			@SuppressWarnings("unused")
			Metrics metrics = new Metrics(this, 26323);
			logger.info("bstats for MatrixSpigotBridge has been enabled. You can opt-out by disabling bstats in the plugin config.");
		}
		
		// Connect to Matrix Server
		if (!config.isFirstRun) {
			startBridgeAsync(null, success -> {
				if (success) {
					try {
						matrix.getLastMessages(); // Don't process messages sent during startup
					} catch (Exception ignored) {}
					String start_message = config.getFormat("server.start");
					if (!start_message.isEmpty())
						sendMessageToMatrix(start_message, "", null);
					updateRoomTopicAsync(success1 -> {});
				}
			});
		}

		logger.info("Startup sequence complete!");
	}

	@Override
	public void onDisable() {
		String stop_message = config.getFormat("server.stop");
		if (!stop_message.isEmpty() && matrix != null) {
			final String msg = stop_message;
			Thread shutdownThread = new Thread(() -> {
				try {
					sendMessageToMatrix(msg,"", null);
				} catch (Exception ignored) {}
			});
			shutdownThread.start();
			try {
				shutdownThread.join(5000); // Wait up to 5 seconds for the message to send
				if (shutdownThread.isAlive()) {
					logger.warning("Shutdown message did not send in time, forcefully disabling...");
					cancelAllTasks();
					shutdownThread.interrupt();
				}
			} catch (InterruptedException ignored) {}
		}
	}
}
