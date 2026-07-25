package com.pseudosmp.tools.bridge;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.HttpsURLConnection;

import org.json.*;

import com.pseudosmp.msb.MatrixSpigotBridge;
import com.pseudosmp.tools.game.ConfigUtils;
import com.pseudosmp.tools.formatting.MessageFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class Matrix {
	private String access_token = "";
	private String server = "";
	private String user_id = "";
	private String room_id = "";
	private Set<String> joined_room_ids = new HashSet<String>();

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
			if (!returnedUser.isEmpty()
					&& (user_id == null || user_id.isEmpty() || returnedUser.equalsIgnoreCase(user_id))) {
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
					.put("user", user_id));
			login_payload.put("password", password);

			JSONObject login_response = new JSONObject(
					request("POST", "/_matrix/client/v3/login", login_payload, false));
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

	public String getRoomId() {
		return room_id;
	}

	public Set<String> getJoinedRoomIds() {
		return Collections.unmodifiableSet(joined_room_ids);
	}

	public String getRoomDisplayName(String roomId) {
		if (roomId == null || roomId.trim().isEmpty()) {
			return "unknown room";
		}
		String trimmed = roomId.trim();
		try {
			JSONObject nameState = new JSONObject(get("/_matrix/client/v3/rooms/" + trimmed + "/state/m.room.name"));
			String name = nameState.optString("name", "").trim();
			if (!name.isEmpty()) {
				return name + " (" + trimmed + ")";
			}
		} catch (Exception ignored) {
		}

		String purposes = config != null ? config.getPurposesForRoomId(trimmed) : "configured";
		return purposes + " room (" + trimmed + ")";
	}

	public boolean knockRoom(String roomId) {
		if (roomId == null || roomId.trim().isEmpty())
			return false;
		try {
			request("POST", "/_matrix/client/v3/knock/" + roomId.trim(), new JSONObject());
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public boolean isKnockRejected(String roomId) {
		if (roomId == null || roomId.trim().isEmpty()) {
			return false;
		}
		String trimmed = roomId.trim();
		try {
			JSONObject syncObj = new JSONObject(get("/_matrix/client/v3/sync"));
			JSONObject rooms = syncObj.optJSONObject("rooms");
			JSONObject leaveRooms = rooms != null ? rooms.optJSONObject("leave") : null;

			if (leaveRooms == null || !leaveRooms.has(trimmed)) {
				return false;
			}

			JSONObject roomData = leaveRooms.optJSONObject(trimmed);
			if (roomData == null) {
				return false;
			}

			for (JSONObject evt : extractEvents(roomData)) {
				if (!"m.room.member".equals(evt.optString("type")))
					continue;
				if (!user_id.equalsIgnoreCase(evt.optString("state_key")))
					continue;

				JSONObject content = evt.optJSONObject("content");
				String membership = content != null ? content.optString("membership", "") : "";
				if (!"leave".equalsIgnoreCase(membership))
					continue;

				String sender = evt.optString("sender", "");
				if (user_id.equalsIgnoreCase(sender))
					continue; // Self-left, not an admin rejection

				String prevMembership = extractPrevMembership(evt);
				if ("knock".equalsIgnoreCase(prevMembership)) {
					return true;
				}
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	private java.util.List<JSONObject> extractEvents(JSONObject roomData) {
		java.util.List<JSONObject> events = new java.util.ArrayList<>();
		String[] sections = { "state", "timeline" };
		for (String section : sections) {
			JSONObject secObj = roomData.optJSONObject(section);
			if (secObj != null) {
				JSONArray evts = secObj.optJSONArray("events");
				if (evts != null) {
					for (int i = 0; i < evts.length(); i++) {
						JSONObject evt = evts.optJSONObject(i);
						if (evt != null) {
							events.add(evt);
						}
					}
				}
			}
		}
		return events;
	}

	private String extractPrevMembership(JSONObject evt) {
		JSONObject unsigned = evt.optJSONObject("unsigned");
		if (unsigned != null && unsigned.has("prev_content")) {
			JSONObject prevContent = unsigned.optJSONObject("prev_content");
			if (prevContent != null) {
				return prevContent.optString("membership", "");
			}
		}
		JSONObject prevContent = evt.optJSONObject("prev_content");
		return prevContent != null ? prevContent.optString("membership", "") : "";
	}

	public boolean joinRoom(String room_id) {
		return joinRooms(Collections.singletonList(room_id), null) > 0;
	}

	public int joinRooms(Collection<String> targetRoomIds) {
		return joinRooms(targetRoomIds, null);
	}

	public int joinRooms(Collection<String> targetRoomIds, CommandSender sender) {
		if (user_id == null || user_id.isEmpty() || access_token == null || access_token.isEmpty()
				|| targetRoomIds == null || targetRoomIds.isEmpty()) {
			return 0;
		}

		joined_room_ids.clear();

		for (String targetRoomId : targetRoomIds) {
			if (targetRoomId == null || targetRoomId.trim().isEmpty())
				continue;
			String trimmedRoomId = targetRoomId.trim();

			boolean inRoom = false;
			String membershipCheckError = null;
			String currentMembership = "";

			// Check membership of bot in room
			try {
				JSONObject membershipState = new JSONObject(
						get("/_matrix/client/v3/rooms/" + trimmedRoomId + "/state/m.room.member/" + user_id));

				currentMembership = membershipState.optString("membership", "");
				if ("join".equals(currentMembership)) {
					plugin.getLogger().info("Already in room " + getRoomDisplayName(trimmedRoomId));
					inRoom = true;
				}
			} catch (Exception e) {
				membershipCheckError = e.getMessage();
			}

			if (!inRoom) {
				if ("invite".equals(currentMembership)) {
					// Invited -> try to join
					try {
						request("POST", "/_matrix/client/v3/rooms/" + trimmedRoomId + "/join", new JSONObject());
						plugin.getLogger()
								.info("Joined room " + getRoomDisplayName(trimmedRoomId) + " via pending invite.");
						inRoom = true;
					} catch (Exception e) {
						plugin.getLogger().severe(
								"Failed to join room " + getRoomDisplayName(trimmedRoomId) + ": " + e.getMessage());
					}
				} else if ("knock".equals(currentMembership)) {
					// Knock is currently pending
					String displayName = getRoomDisplayName(trimmedRoomId);
					plugin.getLogger().warning("Knock for " + displayName
							+ " is pending approval. Please accept the knock in Matrix and run /msb restart.");
					if (sender != null) {
						sender.sendMessage("§e[MatrixSpigotBridge] §eKnock for " + displayName
								+ " is pending approval. Please accept the knock in Matrix and run §a/msb restart§e.");
					}
				} else {
					// Not joined -> try to join directly first
					try {
						request("POST", "/_matrix/client/v3/rooms/" + trimmedRoomId + "/join", new JSONObject());
						plugin.getLogger().info("Joined room " + getRoomDisplayName(trimmedRoomId));
						inRoom = true;
					} catch (Exception joinEx) {
						// Join failed -> try knocking if room requires invite/knock
						String displayName = getRoomDisplayName(trimmedRoomId);
						if (knockRoom(trimmedRoomId)) {
							plugin.getLogger().warning("Knock request sent for " + displayName
									+ ". Please accept the knock in Matrix and run /msb restart.");
							if (sender != null) {
								sender.sendMessage("§e[MatrixSpigotBridge] §eKnock request sent for " + displayName
										+ ". Please accept the knock in Matrix and run §a/msb restart§e.");
							}
						} else {
							// Knock failed or disabled -> check if knock was explicitly rejected
							if (isKnockRejected(trimmedRoomId)) {
								plugin.getLogger().severe("Knock for Matrix room " + displayName
										+ " was REJECTED by room administrators!");
								if (sender != null) {
									sender.sendMessage("§e[MatrixSpigotBridge] §cKnock for Matrix room " + displayName
											+ " was REJECTED by room administrators!");
								}
							} else {
								plugin.getLogger().severe(
										"Failed to join Matrix room " + displayName + ": " + joinEx.getMessage());
								if (membershipCheckError != null) {
									plugin.getLogger().severe(
											"Membership check info for " + displayName + ": " + membershipCheckError);
								}
								if (sender != null) {
									sender.sendMessage(
											"§e[MatrixSpigotBridge] §cFailed to join Matrix room " + displayName + "!");
								}
							}
						}
					}
				}
			}

			if (inRoom) {
				joined_room_ids.add(trimmedRoomId);
			}
		}

		if (joined_room_ids.isEmpty()) {
			plugin.getLogger().severe("No configured Matrix rooms could be joined or verified!");
			return 0;
		}

		// Set primary room_id to first joined room
		this.room_id = joined_room_ids.iterator().next();

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
			plugin.getLogger().info("Matrix: Messages from these users will not be relayed to Minecraft chat: "
					+ notSenders.toString());

			JSONArray filterRooms = new JSONArray();
			for (String rId : joined_room_ids) {
				filterRooms.put(rId);
			}
			room.put("rooms", filterRooms);

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
			return 0;
		}

		// Send first sync (to populate room_history_token and ignore any messages sent
		// before server start)
		try {
			getLastMessages();
		} catch (Exception e) {
			plugin.getLogger().warning("Initial room sync failed: " + e.getMessage());
			return 0;
		}

		return joined_room_ids.size();
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
		return postMessage(this.room_id, formattedBody);
	}

	public boolean postMessage(String targetRoomId, String formattedBody) {
		String rId = (targetRoomId != null && !targetRoomId.trim().isEmpty()) ? targetRoomId.trim() : room_id;
		if (rId.isEmpty() || access_token.isEmpty()) {
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
			String endpoint = "/_matrix/client/v3/rooms/" + rId + "/send/m.room.message/" + txnId;
			request("PUT", endpoint, payload);
		} catch (MatrixApiException e) {
			// Known API error already logged cleanly with user solution in request()
			return false;
		} catch (Exception e) {
			plugin.getLogger().warning("Unexpected error posting message to Matrix: " + e.getMessage());
			e.printStackTrace();
			return false;
		}

		return true;
	}

	public JSONArray getLastMessages() throws Exception {
		JSONArray result = new JSONArray();

		JSONObject raw_result = new JSONObject(request("GET", "/_matrix/client/v3/sync?filter=" + this.room_filters
				+ (room_history_token.isEmpty() ? "" : "&since=" + this.room_history_token), new JSONObject(), true));

		if (raw_result.has("next_batch")) {
			this.room_history_token = raw_result.getString("next_batch");
		}

		JSONObject room_data = raw_result.optJSONObject("rooms");
		if (room_data == null) {
			return result;
		}

		JSONObject join_data = room_data.optJSONObject("join");
		if (join_data == null) {
			return result;
		}

		for (String rId : join_data.keySet()) {
			JSONObject roomObj = join_data.optJSONObject(rId);
			if (roomObj == null)
				continue;

			JSONObject timelineObj = roomObj.optJSONObject("timeline");
			if (timelineObj == null)
				continue;

			JSONArray eventsArr = timelineObj.optJSONArray("events");
			if (eventsArr == null)
				continue;

			for (int i = 0; i < eventsArr.length(); i++) {
				JSONObject evtObj = eventsArr.getJSONObject(i);
				if (!evtObj.has("room_id")) {
					evtObj.put("room_id", rId);
				}
				result.put(evtObj);
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
			} catch (MatrixApiException e) {
				displayname_by_matrixid.put(matrixid, matrixid);
			} catch (Exception e) {
				plugin.getLogger()
						.warning("Unexpected error fetching display name for " + matrixid + ": " + e.getMessage());
				e.printStackTrace();
				displayname_by_matrixid.put(matrixid, matrixid);
			}
		}

		return displayname_by_matrixid.get(matrixid);
	}

	public boolean setRoomTopic(String topic) {
		return setRoomTopic(this.room_id, topic);
	}

	public boolean setRoomTopic(String targetRoomId, String topic) {
		String rId = (targetRoomId != null && !targetRoomId.trim().isEmpty()) ? targetRoomId.trim() : room_id;
		if (rId.isEmpty() || access_token.isEmpty()) {
			return false;
		}
		try {
			JSONObject payload = new JSONObject();
			payload.put("topic", topic);
			// The state_key for m.room.topic is always an empty string
			request(
					"PUT",
					"/_matrix/client/v3/rooms/" + rId + "/state/m.room.topic",
					payload);
			return true;
		} catch (MatrixApiException e) {
			// Known API error already logged cleanly with user solution in request()
			return false;
		} catch (Exception e) {
			plugin.getLogger().warning("Unexpected error setting room topic: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	public boolean addReaction(String event_id, String reaction) {
		return addReaction(this.room_id, event_id, reaction);
	}

	public boolean addReaction(String targetRoomId, String event_id, String reaction) {
		String rId = (targetRoomId != null && !targetRoomId.trim().isEmpty()) ? targetRoomId.trim() : room_id;
		if (rId.isEmpty() || access_token.isEmpty()) {
			return false;
		}
		try {
			JSONObject payload = new JSONObject();
			payload.put("m.relates_to", new JSONObject()
					.put("rel_type", "m.annotation")
					.put("event_id", event_id)
					.put("key", reaction));

			String txnId = nextTxnId();
			String endpoint = "/_matrix/client/v3/rooms/" + rId + "/send/m.reaction/" + txnId;
			request("PUT", endpoint, payload);
			return true;
		} catch (MatrixApiException e) {
			// Known API error already logged cleanly with user solution in request()
			return false;
		} catch (Exception e) {
			plugin.getLogger().warning("Unexpected error adding reaction: " + e.getMessage());
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

	public JSONObject getRoomPowerLevels(String roomId) {
		if (roomId == null || roomId.trim().isEmpty())
			return null;
		try {
			String res = get("/_matrix/client/v3/rooms/" + roomId.trim() + "/state/m.room.power_levels");
			return new JSONObject(res);
		} catch (Exception e) {
			return null;
		}
	}

	public int getBotPowerLevel(String roomId) {
		JSONObject pl = getRoomPowerLevels(roomId);
		if (pl == null)
			return -1;

		JSONObject users = pl.optJSONObject("users");
		if (users != null && users.has(user_id)) {
			return users.optInt(user_id, 0);
		}
		return pl.optInt("users_default", 0);
	}

	public int getRequiredPowerLevelForState(String roomId, String stateEventType) {
		JSONObject pl = getRoomPowerLevels(roomId);
		if (pl == null)
			return 50;

		int stateDefault = pl.optInt("state_default", 50);
		JSONObject events = pl.optJSONObject("events");
		if (events != null && events.has(stateEventType)) {
			return events.optInt(stateEventType, stateDefault);
		}
		return stateDefault;
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

		con.setRequestProperty("Accept", "application/json");

		if (!proto.equals("GET")) {
			con.setRequestProperty("Content-Type", "application/json");
			con.setDoOutput(true);
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
			} catch (Exception ignored) {
			}

			String rawErrorStr = response.toString();
			String errcode = "";
			String errorMsg = "";

			try {
				JSONObject errJson = new JSONObject(rawErrorStr);
				errcode = errJson.optString("errcode", "");
				errorMsg = errJson.optString("error", "");
			} catch (Exception ignored) {
			}

			String userSolution = buildUserSolution(statusCode, errcode, errorMsg, url, proto);

			plugin.getLogger().warning("Matrix API Error (" + statusCode + "): " + rawErrorStr);
			if (userSolution != null && !userSolution.trim().isEmpty()) {
				plugin.getLogger().warning("-> Solution: " + userSolution);
			}

			throw new MatrixApiException(statusCode, errcode, errorMsg, userSolution,
					"Server returned HTTP response code: " + statusCode +
							" for URL: " + server + url + " - Error: " + rawErrorStr);
		}

		return response.toString();
	}

	private String buildUserSolution(int statusCode, String errcode, String errorMsg, String url, String proto) {
		if (statusCode == 403 || "M_FORBIDDEN".equalsIgnoreCase(errcode)) {
			if ("PUT".equalsIgnoreCase(proto) && url.contains("/state/m.room.topic")) {
				return "Permission Denied: The bot user does not have permission to modify room topic in Matrix. Please grant Moderator privileges (power level 50+) to the bot user in Matrix room settings.";
			}
			if (url.contains("/knock/") || (errorMsg != null && errorMsg.toLowerCase().contains("knock"))) {
				return "Knock Denied: The room does not accept knock requests (join rule is not set to 'knock') or knocking is disabled in Matrix room settings.";
			}
			if (errorMsg != null && errorMsg.toLowerCase().contains("power level")) {
				return "Permission Denied: The bot user lacks required power level to perform this action in Matrix. Please check user power levels in Matrix room settings.";
			}
			return null;
		} else if (statusCode == 401 || "M_UNAUTHORIZED".equalsIgnoreCase(errcode)) {
			return "Unauthorized: Invalid or expired access token. Please verify 'matrix_access_token' in config.yml or re-authenticate using /msb reload.";
		} else if (statusCode == 404 || "M_NOT_FOUND".equalsIgnoreCase(errcode)) {
			return "Not Found: Requested room or endpoint was not found on the Matrix homeserver. Please verify configured room IDs in config.yml.";
		} else if (statusCode == 429 || "M_LIMIT_EXCEEDED".equalsIgnoreCase(errcode)) {
			return "Rate Limited: Homeserver rate limit exceeded. Please adjust homeserver rate limits or lower sync/poll frequency.";
		}
		return null;
	}
}
