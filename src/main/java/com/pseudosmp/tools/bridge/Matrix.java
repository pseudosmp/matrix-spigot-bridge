package com.pseudosmp.tools.bridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.HttpsURLConnection;

import org.json.*;

import com.pseudosmp.msb.MatrixSpigotBridge;
import com.pseudosmp.tools.game.ConfigUtils;
import com.pseudosmp.tools.formatting.MessageFormatter;
import org.bukkit.plugin.java.JavaPlugin;

public class Matrix {
	private String access_token = "";
	private String server = "";
	private String user_id = "";
	private String room_id = "";

	private String room_history_token = "";
	private String room_filters = "";

    private static final long TXN_EPOCH = System.currentTimeMillis();
    private static final AtomicLong TXN_SEQ = new AtomicLong(0);

	private HashMap<String, String> displayname_by_matrixid = new HashMap<String, String>();

	ConfigUtils config = MatrixSpigotBridge.config;
	JavaPlugin plugin = MatrixSpigotBridge.getInstance();
	MessageFormatter formatter = MatrixSpigotBridge.formatter;

	HttpsURLConnection url_conn;

	public Matrix(String server, String user_id) {
		this.user_id = user_id;
		this.server = server;
	}

    private static String nextTxnId() {
        long seq = TXN_SEQ.incrementAndGet();
        return Long.toString(TXN_EPOCH, 36) + "-" + Long.toString(seq, 36);
    }

	public boolean validateToken() {
		if (access_token == null || access_token.isEmpty()) {
			return false;
		}
		try {
			JSONObject whoami = new JSONObject(get("/_matrix/client/v3/account/whoami"));
			String returnedUser = whoami.optString("user_id", "");
			if (!returnedUser.isEmpty() && (user_id == null || user_id.isEmpty() || returnedUser.equalsIgnoreCase(user_id))) {
				return true;
			}
		} catch (Exception e) {
			plugin.getLogger().warning("Access token validation failed: " + e.getMessage());
		}
		return false;
	}

	public boolean login(String password) {
		if (password == null || password.isEmpty()) {
			return false;
		}
		try {
			JSONObject login_payload = new JSONObject();
			login_payload.put("type", "m.login.password");
			login_payload.put("identifier", new JSONObject()
				.put("type", "m.id.user")
				.put("user", user_id)
			);
			login_payload.put("password", password);

			JSONObject login_response = new JSONObject(request("POST", "/_matrix/client/v3/login", login_payload, false));
			if (login_response.has("access_token")) {
				access_token = login_response.getString("access_token");
				return true;
			} else {
				plugin.getLogger().severe("Login response missing access_token");
				return false;
			}
		} catch (Exception e) {
			plugin.getLogger().severe("Failed to obtain token: " + e.getMessage());
			return false;
		}
	}

	public void setAccessToken(String token) {
		this.access_token = token;
	}

	public String getAccessToken() {
		return access_token;
	}

	public boolean joinRoom(String room_id) {
		if (user_id == null || user_id.isEmpty() || room_id == null || room_id.isEmpty() || access_token == null || access_token.isEmpty())
			return false;

		this.room_id = room_id;

		boolean inRoom = false;
		// Check membership of bot in room
		try {
			JSONObject membershipState = new JSONObject(
				get("/_matrix/client/v3/rooms/" + room_id + "/state/m.room.member/" + user_id)
			);

			String membership = membershipState.optString("membership", "");
			if ("join".equals(membership)) {
				plugin.getLogger().info("Already in room " + room_id);
				inRoom = true;
			}
		} catch (Exception e) {
			plugin.getLogger().info("Membership check info: " + e.getMessage());
		}

		if (!inRoom) {
			// Not joined -> try to join
			try {
				request("POST", "/_matrix/client/v3/rooms/" + room_id + "/join", new JSONObject());
				plugin.getLogger().info("Joined room " + room_id);
				inRoom = true;
			} catch (Exception e) {
				plugin.getLogger().severe("Failed to join room " + room_id + ": " + e.getMessage());
				return false;
			}
		}

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
			plugin.getLogger().info("Matrix: Messages from these users will not be relayed to Minecraft chat: " + notSenders.toString());
			// Get only events from this room
			room.put("rooms", new JSONArray().put(room_id));
			// Get only message events
			timeline.put("types", new JSONArray().put("m.room.message"));
			// Ignore messages sent by these users
			timeline.put("not_senders", notSenders);
			// Lazy load members to reduce sync overhead
			timeline.put("lazy_load_members", true);

			room.put("timeline", timeline);
			roomFilters.put("room", room);

			room_filters = URLEncoder.encode(roomFilters.toString(), "UTF-8");
		} catch (Exception e) {
			plugin.getLogger().severe("Failed to construct room filters: " + e.getMessage());
			return false;
		}

		// Send first sync (to populate room_history_token and ignore any messages sent before server start)
		try {
			getLastMessages();
		} catch (Exception e) {
			plugin.getLogger().warning("Initial room sync failed: " + e.getMessage());
			return false;
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

	public boolean postMessage(String formattedBody) {
		if (room_id.isEmpty() || access_token.isEmpty()) {
			return false;
		}

		String htmlBody = formatter.yamlEscapeToHtml(formattedBody);
		String plainBody = formatter.stripHtmlTags(htmlBody);

		JSONObject payload = new JSONObject();
		payload.put("msgtype", "m.text");
		payload.put("body", plainBody != null ? plainBody : "");
		if (htmlBody != null && !htmlBody.isEmpty()) {
			payload.put("format", "org.matrix.custom.html");
			payload.put("formatted_body", htmlBody);
		}

		try {
			String txnId = nextTxnId();
			String endpoint = "/_matrix/client/v3/rooms/" + room_id + "/send/m.room.message/" + txnId;
			request("PUT", endpoint, payload);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public JSONArray getLastMessages() throws Exception {
		JSONArray result = new JSONArray();

		JSONObject raw_result = new JSONObject(request("GET", "/_matrix/client/v3/sync?filter=" + this.room_filters
			+ (room_history_token.isEmpty() ? "" : "&since=" + this.room_history_token)
		, new JSONObject(), true));

		if (raw_result.has("next_batch")) {
			this.room_history_token = raw_result.getString("next_batch");
		}

		JSONObject room_data = raw_result.optJSONObject("rooms");
		if (room_data != null) {
			JSONObject join_data = room_data.optJSONObject("join");
			if (join_data != null && join_data.has(room_id)) {
				JSONObject roomObj = join_data.optJSONObject(room_id);
				if (roomObj != null) {
					JSONObject timelineObj = roomObj.optJSONObject("timeline");
					if (timelineObj != null) {
						JSONArray eventsArr = timelineObj.optJSONArray("events");
						if (eventsArr != null) {
							result = eventsArr;
						}
					}
				}
			}
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
				JSONObject response = new JSONObject(get("/_matrix/client/v3/profile/" + matrixid + "/displayname"));
				displayname_by_matrixid.put(matrixid, response.getString("displayname"));
			} catch (Exception e) {
				e.printStackTrace();
				displayname_by_matrixid.put(matrixid, matrixid);
			}
		}

		return displayname_by_matrixid.get(matrixid);
	}

	public boolean setRoomTopic(String topic) {
		if (room_id.isEmpty() || access_token.isEmpty()) {
			return false;
		}
		try {
			JSONObject payload = new JSONObject();
			payload.put("topic", topic);
			// The state_key for m.room.topic is always an empty string
			request(
				"PUT",
				"/_matrix/client/v3/rooms/" + room_id + "/state/m.room.topic",
				payload
			);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean addReaction(String event_id, String reaction) {
		if (room_id.isEmpty() || access_token.isEmpty()) {
			return false;
		}
		try {
			JSONObject payload = new JSONObject();
			payload.put("m.relates_to", new JSONObject()
				.put("rel_type", "m.annotation")
				.put("event_id", event_id)
				.put("key", reaction)
			);

            String txnId = nextTxnId();
            String endpoint = "/_matrix/client/v3/rooms/" + room_id + "/send/m.reaction/" + txnId;
			request("PUT", endpoint, payload);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean isConnected() {
		if (access_token == null || access_token.isEmpty() || room_id == null || room_id.isEmpty()) {
			return false;
		}
		try {
			get("/_matrix/client/v3/rooms/" + room_id + "/state");
			return true;
		} catch (Exception e) {
			return false;
		}
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

		if (addBearer && !access_token.isEmpty())
			con.setRequestProperty("Authorization", "Bearer " + access_token);

		con.setRequestProperty("Content-Type", "application/json");
		con.setRequestProperty("Accept", "application/json");
		con.setDoOutput(true);

		if (!proto.equals("GET")) {
			try (OutputStream os = con.getOutputStream()) {
				byte[] input = strpayload.getBytes("utf-8");
				os.write(input, 0, input.length);
			}
		}

		// Handle error responses properly
		int statusCode = con.getResponseCode();
		if (statusCode >= 200 && statusCode < 300) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
				String responseLine = null;
				while ((responseLine = br.readLine()) != null) {
					response.append(responseLine.trim());
				}
			}
		} else {
			// Read error stream for better error messages
			try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getErrorStream(), "utf-8"))) {
				String responseLine = null;
				while ((responseLine = br.readLine()) != null) {
					response.append(responseLine.trim());
				}
			}
			
			// Log the error details for debugging
			plugin.getLogger().warning("Matrix API Error (" + statusCode + "): " + response.toString());
			
			// Throw exception with more detailed error message
			throw new IOException("Server returned HTTP response code: " + statusCode + 
				" for URL: " + server + url + " - Error: " + response.toString());
		}

		return response.toString();
	}
}
