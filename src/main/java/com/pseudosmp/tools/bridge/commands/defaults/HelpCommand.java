package com.pseudosmp.tools.bridge.commands.defaults;

import com.pseudosmp.tools.bridge.commands.MatrixCommand;
import com.pseudosmp.tools.bridge.commands.MatrixCommandHandler;

public class HelpCommand implements MatrixCommand {
    private final MatrixCommandHandler handler;

    public HelpCommand(MatrixCommandHandler handler) {
        this.handler = handler;
    }

    public void execute(String[] args, String sender, String eventId) {
        execute(args, sender, eventId, null);
    }

    @Override
    public void execute(String[] args, String sender, String eventId, String roomId) {
        String helpMessage = handler.getConfig().getFormat("matrix_commands.help");
        if (helpMessage != null && !helpMessage.isEmpty()) {
            helpMessage = handler.getFormatter().replaceTimePlaceholders(helpMessage);
            helpMessage = handler.getFormatter().replacePlaceholderAPI(null, helpMessage);
            
            StringBuilder sb = new StringBuilder();
            for (String cmdName : handler.getConfig().matrixAvailableCommands) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(handler.getConfig().matrixCommandPrefix).append(cmdName);
            }
            handler.getMatrix().postMessage(roomId, helpMessage.replace("{COMMANDS}", sb.toString()));
        }
    }
}
