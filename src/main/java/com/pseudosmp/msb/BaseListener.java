package com.pseudosmp.msb;

import com.pseudosmp.tools.bridge.MessagePurpose;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class BaseListener implements Listener {
	protected MatrixSpigotBridge _plugin;

    public BaseListener(MatrixSpigotBridge plugin) {
    	this._plugin = plugin;
    }

    protected void sendMatrixMessage(String format, String message) {
    	sendMatrixMessage(MessagePurpose.CHAT, format, message, null);
    }

    protected void sendMatrixMessage(String format, String message, Player player) {
    	sendMatrixMessage(MessagePurpose.CHAT, format, message, player);
    }

    protected void sendMatrixMessage(MessagePurpose purpose, String format, String message) {
    	sendMatrixMessage(purpose, format, message, null);
    }

    protected void sendMatrixMessage(MessagePurpose purpose, String format, String message, Player player) {
    	if (format == null || format.isEmpty())
    		return;

        _plugin.sendMessageToMatrix(purpose, format, message, player);
    }
}
