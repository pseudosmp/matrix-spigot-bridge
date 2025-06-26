package com.pseudosmp.tools.game;

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
    		config.getFormat("player.join"),
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
    		config.getFormat("player.quit"),
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
    		config.getFormat("player.death"),
    		message,
    		evt.getEntity()
		);
    }
}
