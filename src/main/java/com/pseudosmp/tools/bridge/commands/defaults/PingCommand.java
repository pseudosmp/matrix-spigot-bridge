package com.pseudosmp.tools.bridge.commands.defaults;

import com.pseudosmp.tools.bridge.commands.MatrixCommand;
import com.pseudosmp.tools.bridge.commands.MatrixCommandHandler;

public class PingCommand implements MatrixCommand {
    private final MatrixCommandHandler handler;

    public PingCommand(MatrixCommandHandler handler) {
        this.handler = handler;
    }

    public void execute(String[] args, String sender, String eventId) {
        String pingMessage = handler.getConfig().getFormat("matrix_commands.ping");
        int ping = handler.getMatrix().ping();
        if (pingMessage != null) {
            pingMessage = handler.getFormatter().replaceTimePlaceholders(pingMessage);
            if (ping > 0) {
                handler.getMatrix().postMessage(pingMessage.replace("{PING}", String.valueOf(ping)));
            } else {
                String errorMessage = handler.getConfig().getFormat("matrix_commands.error");
                errorMessage = handler.getFormatter().replaceTimePlaceholders(errorMessage);
                handler.getMatrix().postMessage(errorMessage.replace("{ERROR}", "Could not ping Matrix server."));
            }
        }
    }
}
