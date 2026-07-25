package com.pseudosmp.tools.bridge.commands;

public interface MatrixCommand {
    void execute(String[] args, String sender, String eventId);

    default void execute(String[] args, String sender, String eventId, String roomId) {
        execute(args, sender, eventId);
    }
}
