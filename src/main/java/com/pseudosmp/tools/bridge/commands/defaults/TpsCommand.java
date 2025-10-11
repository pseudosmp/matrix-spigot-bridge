package com.pseudosmp.tools.bridge.commands.defaults;

import com.pseudosmp.tools.bridge.commands.MatrixCommand;
import com.pseudosmp.tools.bridge.commands.MatrixCommandHandler;
import com.pseudosmp.tools.game.ServerInfo;

public class TpsCommand implements MatrixCommand {
    private final MatrixCommandHandler handler;

    public TpsCommand(MatrixCommandHandler handler) {
        this.handler = handler;
    }

    public void execute(String[] args, String sender, String eventId) {
        String tpsMessage = handler.getConfig().getFormat("matrix_commands.tps");
        if (tpsMessage != null && !tpsMessage.isEmpty()) {
            try {
                double tps = ServerInfo.getTps();
                tpsMessage = handler.getFormatter().replaceTimePlaceholders(tpsMessage);
                handler.getMatrix().postMessage(tpsMessage.replace("{TPS}", String.format("%.2f", tps)));
            } catch (Exception e) {
                handler.getMatrix().addReaction(eventId, "⚠️");
                String errorMessage = handler.getConfig().getFormat("matrix_commands.error");
                errorMessage = handler.getFormatter().replaceTimePlaceholders(errorMessage);
                handler.getMatrix().postMessage(errorMessage.replace("{ERROR}", e.getMessage()));
            }
        }
    }
}
