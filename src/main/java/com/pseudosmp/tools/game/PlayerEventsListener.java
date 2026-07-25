package com.pseudosmp.tools.game;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

import com.pseudosmp.msb.BaseListener;
import com.pseudosmp.msb.MatrixSpigotBridge;
import com.pseudosmp.tools.bridge.MessagePurpose;
import com.pseudosmp.tools.integrations.IntegrationManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerEventsListener extends BaseListener {
	private static final Pattern NAME_CHANGE_PATTERN = Pattern.compile("\\(formerly known as\\s+([^)]+)\\)", Pattern.CASE_INSENSITIVE);

	public PlayerEventsListener(MatrixSpigotBridge plugin) {
		super(plugin);
	}

	ConfigUtils config = MatrixSpigotBridge.config;
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void playerJoined(PlayerJoinEvent evt) {
    	String message = evt.getJoinMessage();
        if (message == null || message.isEmpty() || IntegrationManager.isVanished(evt.getPlayer())) {
        	return;
        }

        String formatKey;
        String oldName = null;

        if (!evt.getPlayer().hasPlayedBefore()) {
        	formatKey = "player.first_join";
        } else {
        	String plainMessage = ChatColor.stripColor(message);
        	Matcher matcher = NAME_CHANGE_PATTERN.matcher(plainMessage);
        	if (matcher.find()) {
        		formatKey = "player.join_name_changed";
        		oldName = matcher.group(1);
        	} else {
        		formatKey = "player.join";
        	}
        }

        String format = config.getFormat(formatKey)
                .replace("{OLD_NAME}", oldName != null ? oldName : "");

        sendMatrixMessage(
    		MessagePurpose.JOIN,
    		format,
    		message,
    		evt.getPlayer()
		);
    }

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void playerQuit(PlayerQuitEvent evt) {
    	String message = evt.getQuitMessage();
        if (message == null || message.isEmpty() || IntegrationManager.isVanished(evt.getPlayer())) {
        	return;
        }

        sendMatrixMessage(
    		MessagePurpose.LEAVE,
    		config.getFormat("player.quit"),
    		message,
    		evt.getPlayer()
		);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void playerDied(PlayerDeathEvent evt) {
    	String message = evt.getDeathMessage();
        if (message == null || message.isEmpty() || IntegrationManager.isVanished(evt.getEntity())) {
        	return;
        }

        sendMatrixMessage(
    		MessagePurpose.DEATH,
    		config.getFormat("player.death"),
    		message,
    		evt.getEntity()
		);
    }
}
