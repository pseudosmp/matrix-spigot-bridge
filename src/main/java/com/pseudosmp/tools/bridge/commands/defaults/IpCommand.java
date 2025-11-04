package com.pseudosmp.tools.bridge.commands.defaults;

import com.pseudosmp.tools.bridge.commands.MatrixCommand;
import com.pseudosmp.tools.bridge.commands.MatrixCommandHandler;

public class IpCommand implements MatrixCommand {
    private final MatrixCommandHandler handler;

    public IpCommand(MatrixCommandHandler handler) {
        this.handler = handler;
    }

    public void execute(String[] args, String sender, String eventId) {
        String ipMessage = handler.getConfig().getFormat("matrix_commands.ip");
        if (ipMessage != null && !ipMessage.isEmpty()) {
            ipMessage = handler.getFormatter().replaceTimePlaceholders(ipMessage);
            handler.getMatrix().postMessage(ipMessage);
        }
    }
}
