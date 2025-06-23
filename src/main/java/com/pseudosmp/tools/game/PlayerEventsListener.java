package com.pseudosmp.tools.game;

import java.lang.reflect.Method;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

import com.pseudosmp.msb.BaseListener;
import com.pseudosmp.msb.MatrixSpigotBridge;

public class PlayerEventsListener extends BaseListener {
	public PlayerEventsListener(MatrixSpigotBridge plugin) {
		super(plugin);
	}

	ConfigUtils config = MatrixSpigotBridge.config;
	
	@EventHandler
    public void playerJoined(PlayerJoinEvent evt) {
    	String message = evt.getJoinMessage();
        if (message == null)
        	message = "";

        sendMatrixMessage(
    		config.getMessage("player.join"),
    		message,
    		evt.getPlayer()
		);
    }

	@EventHandler
    public void playerQuit(PlayerQuitEvent evt) {
    	String message = evt.getQuitMessage();
        if (message == null)
        	message = "";
        
        sendMatrixMessage(
    		config.getMessage("player.quit"),
    		message,
    		evt.getPlayer()
		);
    }

    @EventHandler
    public void playerDied(PlayerDeathEvent evt) {
    	String message = evt.getDeathMessage();
        if (message == null)
        	message = "";

        sendMatrixMessage(
    		config.getMessage("player.death"),
    		message,
    		evt.getEntity()
		);
    }

	@EventHandler
	public void playerAdvancementDone(PlayerAdvancementDoneEvent evt) {
        String advancementTitle = null;
        String advancementDescription = null;

        try {
            // Reflection: these methods are 1.17+ only
            Method getDisplay = evt.getAdvancement().getClass().getMethod("getDisplay");
            Object display = getDisplay.invoke(evt.getAdvancement());
            if (display != null) {
                Method getTitle = display.getClass().getMethod("getTitle");
                Method getDescription = display.getClass().getMethod("getDescription");
                Object titleComponent = getTitle.invoke(display);
                Object descComponent = getDescription.invoke(display);
                advancementTitle = titleComponent != null ? titleComponent.toString() : null;
                advancementDescription = descComponent != null ? descComponent.toString() : null;
            }
        } catch (Exception e) {
			// Fallback: use the advancement key (namespace:key)
			String key = evt.getAdvancement().getKey().getKey();
			String advancementKey = key.contains("/") ? key.substring(key.indexOf('/') + 1) : key;
			advancementKey = advancementKey.replaceAll("[_.]", " ");
			if (!advancementKey.isEmpty()) {
				advancementKey = Character.toUpperCase(advancementKey.charAt(0)) + advancementKey.substring(1);
			}
			advancementTitle = advancementKey;

			String namespace = evt.getAdvancement().getKey().getNamespace().replaceAll("[_.]", " ");
			String Namespace = namespace.isEmpty() ? namespace : Character.toUpperCase(namespace.charAt(0)) + namespace.substring(1);
			advancementDescription = "An advancement from " + Namespace;
		}
		sendMatrixMessage(
			config.getMessage("player.advancement").replace("{DESCRIPTION}", 
								ChatColor.stripColor(advancementDescription)),
			ChatColor.stripColor(advancementTitle),
			evt.getPlayer()
		);
	}
}
