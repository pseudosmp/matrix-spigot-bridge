package com.pseudosmp.msb;

import java.io.File;
import java.io.IOException;
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

import org.json.JSONArray;
import org.json.JSONObject;

import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatColor;

import com.pseudosmp.tools.bridge.HttpsTrustAll;
import com.pseudosmp.tools.bridge.Matrix;
import com.pseudosmp.tools.game.MinecraftChatListener;
import com.pseudosmp.tools.game.PlayerEventsListener;
import com.pseudosmp.tools.game.ConfigUtils;

public class MatrixSpigotBridge extends JavaPlugin implements Listener {
	private java.util.logging.Logger logger;
	private final Set<String> relayedEventIDs = ConcurrentHashMap.newKeySet();
	private static final int MAX_RELAYED_EVENTS = 120; // Limit memory usage
	public static ConfigUtils config;
	public BukkitTask matrixPollerTask = null;
	public Matrix matrix;
	

	public Matrix getMatrix() {
		return matrix;
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

							String sender_address = matrix.getDisplayName(obj.getString("sender"), !config.cacheMatrixDisplaynames);
							String body = obj.getJSONObject("content").getString("body");

							if (body.startsWith(config.matrixCommandPrefix)) {
								String command = body.substring(config.matrixCommandPrefix.length()).trim();
								matrix.handleCommand(command, sender_address);
							} else sendMessageToMinecraft(
								config.matrixMessagePrefix,
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

			matrix = new Matrix(config.matrixServer, config.matrixUserId);
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
				loginSuccess = matrix.login(config.getMatrixPassword());
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

			boolean connected = false;
			if (loginSuccess && matrix.joinRoom(config.matrixRoomId) && matrix.isValid()) {
				connected = true;
			}

			if (connected) {
				logger.info("Connected to Matrix server as " + config.matrixUserId + " in room " + config.matrixRoomId);
				// Start poller on main thread
				Bukkit.getScheduler().runTask(this, () -> {
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
	@Override
	public void onEnable() {
		boolean isFirstRun = false;
		logger = getLogger();

		logger.info("Starting MatrixSpigotBridge");
		ConfigUtils config = new ConfigUtils(this);

		if (config.usePlaceholderApi && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			logger.info("PlaceholderAPI found and bound, you can use placeholders in messages");
		}
		
		MsbCommand msbCommand = new MsbCommand(this);
		getCommand("msb").setExecutor(msbCommand);
		getCommand("msb").setTabCompleter(msbCommand);
		
		// Connect to Matrix Server
		if (!isFirstRun) {
			startBridgeAsync(null, success -> {
				if (success) {
					String start_message = config.getMessage("server.start");
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

	public static String minecraftToMatrixMarkdown(String input) {
		if (input == null) return null;
		StringBuilder out = new StringBuilder();
		boolean bold = false, italic = false, underline = false, strike = false, magic = false;
		char[] chars = input.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (chars[i] == '§' && i + 1 < chars.length) {
				char code = Character.toLowerCase(chars[++i]);
				switch (code) {
					case 'l': // Bold
						if (!bold) { out.append("**"); bold = true; }
						break;
					case 'o': // Italic
						if (!italic) { out.append("_"); italic = true; }
						break;
					case 'n': // Underline
						if (!underline) { out.append("<u>"); underline = true; }
						break;
					case 'm': // Strikethrough
						if (!strike) { out.append("~~"); strike = true; }
						break;
					case 'k': // Obfuscated (magic)
						if (!magic) { out.append("�"); magic = true; }
						break;
					case 'r': // Reset
						if (bold) { out.append("**"); bold = false; }
						if (italic) { out.append("_"); italic = false; }
						if (underline) { out.append("</u>"); underline = false; }
						if (strike) { out.append("~~"); strike = false; }
						if (magic) { out.append("</span>"); magic = false; }
						break;
					default:
						// Ignore color codes and unknown codes
						break;
				}
			} else {
				out.append(chars[i]);
			}
		}
		// Close any unclosed tags
		if (bold) out.append("**");
		if (italic) out.append("_");
		if (underline) out.append("</u>");
		if (strike) out.append("~~");
		if (magic) out.append("</span>");
		return out.toString();
	}

	public static String matrixMarkdownToMinecraft(String input) {
		if (input == null) return null;
		// Bold: **text** > §ltext§r
		input = input.replaceAll("\\*\\*(.*?)\\*\\*", "§l$1§r");
		// Italic: _text_ > §o$1§r
		input = input.replaceAll("_(.*?)_", "§o$1§r");
		// Strikethrough: ~~text~~ > §m$1§r
		input = input.replaceAll("~~(.*?)~~", "§m$1§r");
		// Underline: <u>text</u> > §n$1§r
		input = input.replaceAll("(?i)<u>(.*?)</u>", "§n$1§r");
		// Remove any remaining HTML tags
		input = input.replaceAll("(?i)<[^>]+>", "");
		return input;
	}

	public void sendMessageToMatrix(String format, String message, Player player) {
		if (matrix == null || !matrix.isValid()) {
			// Ignoring for now, not connected to matrix server yet
			return;
		}

		if (config.usePlaceholderApi)
			format = PlaceholderAPI.setPlaceholders(player, format);
		if (config.getFormatSettingBool("reserialize_player"))
			message = minecraftToMatrixMarkdown(message);
		message = ChatColor.stripColor(message);

		matrix.sendMessage(format
			.replace("{PLAYERNAME}", (player != null) ? player.getName() : "???")
			.replace("{MESSAGE}", message)
		);
	}

	public void sendMessageToMinecraft(String format, String message, Player player) {
		sendMessageToMinecraft(format, message, player, "???");
	}

	public void sendMessageToMinecraft(String format, String message, Player player, String defaultPlayername) {
		if (config.usePlaceholderApi)
			format = PlaceholderAPI.setPlaceholders(player, format);

		if (config.getFormatSettingBool("reserialize_player"))
			message = matrixMarkdownToMinecraft(message);

		Bukkit.broadcastMessage(format
			.replace("{MATRIXNAME}", (player != null) ? player.getName() : defaultPlayername)
			.replace("{MESSAGE}", message)
		);
	}

	@Override
	public void onDisable() {
		String stop_message = config.getMessage("server.stop");
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
