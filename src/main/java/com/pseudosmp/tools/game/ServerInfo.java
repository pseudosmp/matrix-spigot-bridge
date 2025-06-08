package com.pseudosmp.tools.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ServerInfo {
	public static class PlayerStatus {
		private final String[] names;
		private final int online;
		private final int max;

		public PlayerStatus(String[] names, int online, int max) {
			this.names = names;
			this.online = online;
			this.max = max;
		}

		public String[] getNames() {
			return names;
		}

		public int getOnline() {
			return online;
		}

		public int getMax() {
			return max;
		}
	}

	public static PlayerStatus getPlayerList() {
		Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
		String[] names = new String[players.length];
		for (int i = 0; i < players.length; i++) {
			names[i] = players[i].getName();
		}
		int online = players.length;
		int max = Bukkit.getMaxPlayers();
		return new PlayerStatus(names, online, max);
	}

    public static double getTps() {
        try {
            Object server = org.bukkit.Bukkit.getServer();
            Object minecraftServer = server.getClass().getMethod("getServer").invoke(server);
            java.lang.reflect.Field recentTpsField = minecraftServer.getClass().getField("recentTps");
            double[] recentTps = (double[]) recentTpsField.get(minecraftServer);
            return recentTps[0];
        } catch (Exception e) {
            // Could not fetch TPS
            return -1;
        }
    }
}
