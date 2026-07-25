package com.pseudosmp.tools.integrations.hooks;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface PlayerEventFilter {
    boolean shouldIgnore(Player player);
}
