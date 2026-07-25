package com.pseudosmp.tools.integrations;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import com.pseudosmp.tools.integrations.hooks.MuteHook;
import com.pseudosmp.tools.integrations.hooks.TeamChatHook;
import com.pseudosmp.tools.integrations.hooks.VanishHook;
import com.pseudosmp.tools.integrations.plugins.betterteams.BetterTeamsIntegration;
import com.pseudosmp.tools.integrations.plugins.essentials.EssentialsIntegration;

public class IntegrationManager {
    private static final List<PluginIntegration> integrations = new ArrayList<>();
    private static final List<VanishHook> vanishHooks = new ArrayList<>();
    private static final List<MuteHook> muteHooks = new ArrayList<>();
    private static final List<TeamChatHook> teamChatHooks = new ArrayList<>();

    public static void setupIntegrations(Logger logger) {
        integrations.clear();
        vanishHooks.clear();
        muteHooks.clear();
        teamChatHooks.clear();

        // Register EssentialsX integration
        try {
            EssentialsIntegration essentials = new EssentialsIntegration();
            essentials.setup();
            if (essentials.isEnabled()) {
                integrations.add(essentials);
                vanishHooks.add(essentials);
                muteHooks.add(essentials);
                if (logger != null) {
                    logger.info("Hooked into EssentialsX for Vanish and Mute status detection.");
                }
            }
        } catch (Throwable t) {
            if (logger != null) {
                logger.warning("Could not initialize EssentialsX integration: " + t.getMessage());
            }
        }

        // Register BetterTeams integration
        try {
            BetterTeamsIntegration betterTeams = new BetterTeamsIntegration();
            betterTeams.setup();
            if (betterTeams.isEnabled()) {
                integrations.add(betterTeams);
                teamChatHooks.add(betterTeams);
                if (logger != null) {
                    logger.info("Hooked into BetterTeams for Team Chat status detection.");
                }
            }
        } catch (Throwable t) {
            if (logger != null) {
                logger.warning("Could not initialize BetterTeams integration: " + t.getMessage());
            }
        }
    }

    public static boolean isVanished(Player player) {
        if (player == null) {
            return false;
        }

        // 1. Check standard Bukkit metadata "vanished" (used by SuperVanish, PremiumVanish, VanishNoPacket)
        if (player.hasMetadata("vanished")) {
            for (MetadataValue meta : player.getMetadata("vanished")) {
                if (meta.asBoolean()) {
                    return true;
                }
            }
        }

        // 2. Check registered Vanish Hooks (e.g. EssentialsX)
        for (VanishHook hook : vanishHooks) {
            if (hook.isVanished(player)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isMuted(Player player) {
        if (player == null) {
            return false;
        }

        for (MuteHook hook : muteHooks) {
            if (hook.isMuted(player)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isInTeamChat(Player player) {
        if (player == null) {
            return false;
        }

        for (TeamChatHook hook : teamChatHooks) {
            if (hook.isInTeamChat(player)) {
                return true;
            }
        }

        return false;
    }
}
