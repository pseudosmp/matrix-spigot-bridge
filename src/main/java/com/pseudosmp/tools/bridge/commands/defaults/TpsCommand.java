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
        execute(args, sender, eventId, null);
    }

    @Override
    public void execute(String[] args, String sender, String eventId, String roomId) {
        String tpsMessage = handler.getConfig().getFormat("matrix_commands.tps");
        if (tpsMessage != null && !tpsMessage.isEmpty()) {
            tpsMessage = handler.getFormatter().replaceTimePlaceholders(tpsMessage);
            tpsMessage = handler.getFormatter().replacePlaceholderAPI(null, tpsMessage);
            if (tpsMessage.contains("{TPS}")) {
                try {
                    double tps = ServerInfo.getTps();
                    handler.getMatrix().postMessage(roomId, tpsMessage.replace("{TPS}", String.format("%.2f", tps)));
                } catch (Exception e) {
                    handler.getMatrix().addReaction(roomId, eventId, "⚠️");
                    String errorMessage = handler.getConfig().getFormat("matrix_commands.error");
                    errorMessage = handler.getFormatter().replaceTimePlaceholders(errorMessage);
                    handler.getMatrix().postMessage(roomId, errorMessage.replace("{ERROR}", e.getMessage()));
                }
            } else {
                handler.getMatrix().postMessage(roomId, tpsMessage);
            }
        }
    }
}
