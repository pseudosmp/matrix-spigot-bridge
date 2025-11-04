package com.pseudosmp.tools.bridge.commands.defaults;

import com.pseudosmp.tools.bridge.commands.MatrixCommand;
import com.pseudosmp.tools.bridge.commands.MatrixCommandHandler;
import com.pseudosmp.tools.game.ServerInfo;

public class ListCommand implements MatrixCommand {
    private final MatrixCommandHandler handler;

    public ListCommand(MatrixCommandHandler handler) {
        this.handler = handler;
    }

    public void execute(String[] args, String sender, String eventId) {
        String listMessage = handler.getConfig().getFormat("matrix_commands.list");
        if (listMessage != null && !listMessage.isEmpty()) {
            try {
                ServerInfo.PlayerStatus status = ServerInfo.getPlayerList();
                StringBuilder names = new StringBuilder();
                for (String name : status.getNames()) {
                    if (names.length() > 0) names.append(", ");
                    names.append(name);
                }

                listMessage = handler.getFormatter().replaceTimePlaceholders(listMessage);
                String finalListMessage = listMessage
                    .replace("{ONLINE}", String.valueOf(status.getOnline()))
                    .replace("{MAX}", String.valueOf(status.getMax()))
                    .replace("{NAMES}", names.toString());

                handler.getMatrix().postMessage(finalListMessage);
            } catch (Exception e) {
                handler.getMatrix().addReaction(eventId, "⚠️");
                String errorMessage = handler.getConfig().getFormat("matrix_commands.error");
                errorMessage = handler.getFormatter().replaceTimePlaceholders(errorMessage);
                handler.getMatrix().postMessage(errorMessage.replace("{ERROR}", e.getMessage()));
            }
        }
    }
}
