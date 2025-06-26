package com.pseudosmp.tools.bridge;

import java.io.BufferedReader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;

import javax.net.ssl.HttpsURLConnection;

import org.json.*;

import com.pseudosmp.msb.MatrixSpigotBridge;
import com.pseudosmp.tools.game.ConfigUtils;
import com.pseudosmp.tools.game.ServerInfo;
import org.bukkit.plugin.java.JavaPlugin;

public class Matrix {
	private String access_token = "";
	private String server = "";
	private String user_id = "";
	private String room_id = "";

	private String room_history_token = "";
	private String room_filters = "";

	private HashMap<String, String> displayname_by_matrixid = new HashMap<String, String>();

	ConfigUtils config = MatrixSpigotBridge.config;
	JavaPlugin plugin = MatrixSpigotBridge.getInstance();

	HttpsURLConnection url_conn;

	public Matrix(String server, String user_id) {
		this.user_id = user_id;
		this.server = server;
	}

	public boolean login(String password) {
		try {
			JSONObject login_payload = new JSONObject();
			login_payload.put("type", "m.login.password");
			login_payload.put("user", user_id);
			login_payload.put("password", password);

			JSONObject login_response = null;
			Exception lastException = null;
			String[] endpoints = {"/_matrix/client/v3/login", "/_matrix/client/r0/login", "/_matrix/client/api/v1/login"};
			for (String endpoint : endpoints) {
				try {
					login_response = new JSONObject(request("POST", endpoint, login_payload));
					break;
				} catch (IOException e) {
					// Accept both 404 and FileNotFoundException as "try next"
					if (e instanceof java.io.FileNotFoundException ||
						(e.getMessage() != null && (e.getMessage().contains("404") || e.getMessage().contains("FileNotFoundException")))) {
						lastException = e;
						continue;
					} else {
						throw e;
					}
				}
			}
			if (login_response == null) {
				throw lastException != null ? lastException : new IOException("Matrix login failed: No endpoint succeeded");
			}
			access_token = login_response.getString("access_token");
		} catch (Exception e) {
			plugin.getLogger().severe("Failed to obtain token: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public void setAccessToken(String token) {
		this.access_token = token;
	}

	public String getAccessToken() {
		return access_token;
	}

	public boolean joinRoom(String room_id) {
		if (user_id.equals(""))
			return false;

		this.room_id = room_id;

		JSONObject roomFilters = new JSONObject();
		JSONObject room = new JSONObject();
		JSONObject timeline = new JSONObject();

		try {
			// User Blacklist
			JSONArray notSenders = new JSONArray();
			notSenders.put(user_id);
			if (config.matrixUserBlacklist != null && !config.matrixUserBlacklist.isEmpty()) {
				for (String user : config.matrixUserBlacklist) {
					if (!user.equals(user_id)) { // Don't add the bot user to the blacklist again
						notSenders.put(user);
					}
				}
			}
			plugin.getLogger().info("Matrix: Messages from these users will not be relayed to the Minecraft chat: " + notSenders.toString());
			// Get only events from this room
			room.put("rooms", new JSONArray().put(room_id));
			// Get only message events
			timeline.put("types", new JSONArray().put("m.room.message"));
			// Ignore messages sent by these users
			timeline.put("not_senders", notSenders);

			room.put("timeline", timeline);
			roomFilters.put("room", room);

			room_filters = URLEncoder.encode(roomFilters.toString(), "UTF-8");
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		// Send first sync (to populate room_history_token and ignore any messages sent before server start)
		try {
			getLastMessages();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return true;
	}
    
	public int ping() {
		long start = System.currentTimeMillis();
		try {
			get("/_matrix/client/versions");
		} catch (Exception e) {
			return -1; // This situation _in theory_ should never happen :P
		}
		long delay = System.currentTimeMillis() - start;
		return (int) delay;
	}

	public boolean sendMessage(String formattedBody) {
		if (room_id.equals("") || access_token.equals(""))
			return false;

		String htmlBody = MatrixSpigotBridge.yamlEscapeToHtml(formattedBody);

		String plainBody = MatrixSpigotBridge.stripHtmlTags(htmlBody);

		JSONObject payload = new JSONObject();
		payload.put("msgtype", "m.text");
		payload.put("body", plainBody != null ? plainBody : "");
		if (htmlBody != null && !htmlBody.isEmpty()) {
			payload.put("format", "org.matrix.custom.html");
			payload.put("formatted_body", htmlBody);
		}

		try {
			request("POST", "/_matrix/client/api/v1/rooms/" + room_id + "/send/m.room.message", payload);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public JSONArray getLastMessages() throws Exception {
		JSONArray result = new JSONArray();

		JSONObject raw_result = new JSONObject(request("GET", "/_matrix/client/r0/sync?filter=" + this.room_filters
			+ (room_history_token.isEmpty() ? "" : "&since=" + this.room_history_token)
			+ "&access_token=" + this.access_token
		, new JSONObject(), false));

		this.room_history_token = raw_result.getString("next_batch");

		JSONObject room_data = raw_result.getJSONObject("rooms").getJSONObject("join");

		if (room_data.has(room_id)) {
			result = room_data
				.getJSONObject(room_id)
				.getJSONObject("timeline")
				.getJSONArray("events");
		}

		return result;
	}

	public String getDisplayName(String matrixid) {
		return getDisplayName(matrixid, false);
	}

	public String getDisplayName(String matrixid, boolean clear_cache) {
		if (clear_cache)
			displayname_by_matrixid.clear();

		if (!displayname_by_matrixid.containsKey(matrixid)) {
			try {
				JSONObject response = new JSONObject(get("/_matrix/client/r0/profile/" + matrixid + "/displayname"));
				displayname_by_matrixid.put(matrixid, response.getString("displayname"));
			} catch (Exception e) {
				e.printStackTrace();
				displayname_by_matrixid.put(matrixid, matrixid);
			}
		}

		return displayname_by_matrixid.get(matrixid);
	}

	public boolean setRoomTopic(String topic) {
		if (room_id.equals("") || access_token.equals("")) {
			return false;
		}
		try {
			JSONObject payload = new JSONObject();
			payload.put("topic", topic);
			// The state_key for m.room.topic is always an empty string
			request(
				"PUT",
				"/_matrix/client/r0/rooms/" + room_id + "/state/m.room.topic",
				payload
			);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public void handleCommand(String command, String sender_address) {
		if (command == null || command.trim().isEmpty()) return;

		String[] parts = command.trim().split("\\s+");
		String cmd = parts[0].toLowerCase();

		StringBuilder sb = new StringBuilder();
		for (String cmdName : config.matrixAvailableCommands) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(config.matrixCommandPrefix).append(cmdName);
		}

		String COMMANDS = sb.toString();
		String disabledMessage = config.getMessage("matrix_commands.disabled");

		if (config.matrixAvailableCommands.contains(cmd) && disabledMessage != null) {
			sendMessage(disabledMessage.replace("{COMMANDS}", COMMANDS));
			return;
		}

		switch (cmd) {
			case "ping":
				String pingMessage = config.getMessage("matrix_commands.ping");
				int ping = ping();
				if (pingMessage != null) {
					if (ping > 0) sendMessage(pingMessage.replace("{PING}", String.valueOf(ping())));
					else sendMessage(config.getMessage("matrix_commands.error")
										.replace("{ERROR}", "Could not ping Matrix server."));
				}
				break;
			case "list":
				String listMessage = config.getMessage("matrix_commands.list");
				if (listMessage != null) {
					try {
						ServerInfo.PlayerStatus status = ServerInfo.getPlayerList();
						StringBuilder names = new StringBuilder();
						for (String name : status.getNames()) {
							if (names.length() > 0) names.append(", ");
							names.append(name);
						}

						String finalListMessage = listMessage
							.replace("{ONLINE}", String.valueOf(status.getOnline()))
							.replace("{MAX}", String.valueOf(status.getMax()))
							.replace("{NAMES}", names.toString());

						sendMessage(finalListMessage);
					} catch (Exception e) {
						sendMessage(config.getMessage("matrix_commands.error")
										.replace("{ERROR}", e.getMessage()));
					}
				}
				break;
			case "tps":
				String tpsMessage = config.getMessage("matrix_commands.tps");
				if (tpsMessage != null) {
					try {
						double tps = ServerInfo.getTps();
						sendMessage(tpsMessage.replace("{TPS}", String.format("%.2f", tps)));
					} catch (Exception e) {
						sendMessage(config.getMessage("matrix_commands.error")
											.replace("{ERROR}", e.getMessage()));
					}
				}
				break;
			case "ip":
				String ipMessage = config.getMessage("matrix_commands.ip");
				if (ipMessage != null) sendMessage(ipMessage);
				break;
			case "help":
				String helpMessage = config.getMessage("matrix_commands.help");
				if (helpMessage != null) sendMessage(helpMessage.replace("{COMMANDS}", COMMANDS));
				break;
			default:
				String unknownMessage = config.getMessage("matrix_commands.unknown");
				if (unknownMessage != null) sendMessage(unknownMessage.replace("{COMMANDS}", COMMANDS));
				break;
		}
	}
	public boolean isValid() {
		try {
			get("/_matrix/client/api/v1/rooms/" + room_id + "/state");
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	protected String get(String url) throws Exception {
		return request("GET", url, new JSONObject());
	}

	protected String request(String proto, String url, JSONObject payload) throws Exception {
		return request(proto, url, payload, true);
	}

	protected String request(String proto, String url, JSONObject payload, Boolean addBearer) throws Exception {
		String strpayload = payload.toString();
		StringBuilder response = new StringBuilder();

		URL url_conn = new URL(server + url);
		HttpURLConnection con = (HttpURLConnection) url_conn.openConnection();
		con.setRequestMethod(proto);

		if (addBearer)
			con.setRequestProperty("Authorization", "Bearer " + access_token);

		con.setRequestProperty("Content-Type", "application/json; utf-8");
		con.setRequestProperty("Accept", "application/json");
		con.setDoOutput(true);

		if (!proto.equals("GET")) {
			try (OutputStream os = con.getOutputStream()) {
				byte[] input = strpayload.getBytes("utf-8");
				os.write(input, 0, input.length);
			}
		}

		try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
			String responseLine = null;
			while ((responseLine = br.readLine()) != null) {
				response.append(responseLine.trim());
			}
		}

		return response.toString();
	}
}
