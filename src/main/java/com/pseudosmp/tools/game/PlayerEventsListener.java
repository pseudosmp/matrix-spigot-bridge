package com.pseudosmp.tools.game;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

import com.pseudosmp.msb.BaseListener;
import com.pseudosmp.msb.MatrixSpigotBridge;
import com.pseudosmp.tools.integrations.IntegrationManager;

public class PlayerEventsListener extends BaseListener {
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

        sendMatrixMessage(
    		config.getFormat("player.join"),
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
    		config.getFormat("player.death"),
    		message,
    		evt.getEntity()
		);
    }
}
