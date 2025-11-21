package com.pseudosmp.tools.bridge.commands;

import java.util.HashMap;
import java.util.Map;

import com.pseudosmp.tools.bridge.Matrix;
import com.pseudosmp.tools.bridge.commands.defaults.IpCommand;
import com.pseudosmp.tools.bridge.commands.defaults.ListCommand;
import com.pseudosmp.tools.bridge.commands.defaults.PingCommand;
import com.pseudosmp.tools.bridge.commands.defaults.TpsCommand;
import com.pseudosmp.tools.bridge.commands.defaults.HelpCommand;
import com.pseudosmp.tools.formatting.MessageFormatter;
import com.pseudosmp.tools.game.ConfigUtils;

public class MatrixCommandHandler {
    private final Map<String, MatrixCommand> commands;
    private final Matrix matrix;
    private final ConfigUtils config;
    private final MessageFormatter formatter;

    public MatrixCommandHandler(Matrix matrix, ConfigUtils config, MessageFormatter formatter) {
        this.matrix = matrix;
        this.config = config;
        this.formatter = formatter;
        this.commands = new HashMap<String, MatrixCommand>();
    }

    public void registerCommand(String name, MatrixCommand command) {
        commands.put(name.toLowerCase(), command);
    }

    public void registerDefaultCommands() {
        // Register all available default commands
        for (String commandName : config.matrixAvailableCommands) {
            MatrixCommand command = createCommand(commandName);
            if (command != null) {
                commands.put(commandName, command);
            }
        }
    }

    private MatrixCommand createCommand(String name) {
        switch (name.toLowerCase()) {
            case "ping": return new PingCommand(this);
            case "list": return new ListCommand(this);
            case "tps": return new TpsCommand(this);
            case "ip": return new IpCommand(this);
            case "help": return new HelpCommand(this);
            default: return null;
        }
    }

    public void handleCommand(String command, String sender, String eventId) {
        if (command == null || command.trim().isEmpty()) return;

        String[] parts = command.trim().split("\\s+");
        String cmd = parts[0].toLowerCase();

        MatrixCommand matrixCommand = commands.get(cmd);
        if (matrixCommand != null) {
            matrixCommand.execute(parts, sender, eventId);
        } else {
            // Unknown command
            String unknownMessage = config.getFormat("matrix_commands.unknown");
            if (unknownMessage != null && !unknownMessage.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String cmdName : config.matrixAvailableCommands) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(config.matrixCommandPrefix).append(cmdName);
                }
                matrix.addReaction(eventId, "❓");
                unknownMessage = formatter.replaceTimePlaceholders(unknownMessage);
                matrix.postMessage(unknownMessage.replace("{COMMANDS}", sb.toString()));
            }
        }
    }

    public Matrix getMatrix() {
        return matrix;
    }

    public ConfigUtils getConfig() {
        return config;
    }

    public MessageFormatter getFormatter() {
        return formatter;
    }
}
