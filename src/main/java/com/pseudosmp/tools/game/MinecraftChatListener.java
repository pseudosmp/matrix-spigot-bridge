package com.pseudosmp.tools.game;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import com.pseudosmp.msb.BaseListener;
import com.pseudosmp.msb.MatrixSpigotBridge;
import com.pseudosmp.tools.bridge.MessagePurpose;
import com.pseudosmp.tools.integrations.IntegrationManager;

public class MinecraftChatListener extends BaseListener {
    public MinecraftChatListener(MatrixSpigotBridge plugin) {
		super(plugin);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void messageReceived(AsyncPlayerChatEvent evt) {
        if (evt.isCancelled() || IntegrationManager.isMuted(evt.getPlayer()) || IntegrationManager.isInTeamChat(evt.getPlayer())) {
            return;
        }

        sendMatrixMessage(
    		MessagePurpose.CHAT,
    		_plugin.getConfig().getString("format.player.chat"),
    		evt.getMessage(),
    		evt.getPlayer()
		);
    }
}
