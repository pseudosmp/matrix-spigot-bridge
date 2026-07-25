package com.pseudosmp.tools.integrations.hooks;

import org.bukkit.event.player.AsyncPlayerChatEvent;

@FunctionalInterface
public interface ChatMessageFilter {
    boolean shouldIgnore(AsyncPlayerChatEvent event);
}
