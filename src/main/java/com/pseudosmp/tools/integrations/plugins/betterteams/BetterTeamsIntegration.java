package com.pseudosmp.tools.integrations.plugins.betterteams;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.TeamPlayer;
import com.pseudosmp.tools.integrations.PluginIntegration;
import com.pseudosmp.tools.integrations.hooks.TeamChatHook;

public class BetterTeamsIntegration implements PluginIntegration, TeamChatHook {
    private boolean enabled = false;

    @Override
    public String getName() {
        return "BetterTeams";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setup() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("BetterTeams");
        this.enabled = (plugin != null && plugin.isEnabled());
    }

    @Override
    public boolean isInTeamChat(Player player) {
        if (!enabled || player == null) {
            return false;
        }

        try {
            Team team = Team.getTeam(player);
            if (team == null) {
                return false;
            }

            TeamPlayer teamPlayer = team.getTeamPlayer(player);
            if (teamPlayer != null) {
                return teamPlayer.isInTeamChat();
            }
        } catch (Throwable ignored) {
        }

        return false;
    }
}
