package com.pseudosmp.tools.bridge;

import java.util.Objects;

public class MessagePurpose {
    public static final MessagePurpose CHAT = new MessagePurpose("chat");
    public static final MessagePurpose JOIN = new MessagePurpose("join");
    public static final MessagePurpose LEAVE = new MessagePurpose("leave");
    public static final MessagePurpose DEATH = new MessagePurpose("death");
    public static final MessagePurpose SERVER = new MessagePurpose("server");
    public static final MessagePurpose WATCHDOG = new MessagePurpose("watchdog");
    public static final MessagePurpose CONSOLE = new MessagePurpose("console");

    private final String key;

    public MessagePurpose(String key) {
        this.key = key != null ? key.toLowerCase().trim() : "";
    }

    public String getKey() {
        return key;
    }

    public static MessagePurpose fromKey(String key) {
        if (key == null) return CHAT;
        switch (key.toLowerCase().trim()) {
            case "chat": return CHAT;
            case "join": return JOIN;
            case "leave": return LEAVE;
            case "death": return DEATH;
            case "server": return SERVER;
            case "watchdog": return WATCHDOG;
            case "console": return CONSOLE;
            default: return new MessagePurpose(key);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessagePurpose that = (MessagePurpose) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return key;
    }
}
