package com.pseudosmp.tools.integrations.plugins.essentials;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.pseudosmp.tools.integrations.PluginIntegration;
import com.pseudosmp.tools.integrations.hooks.MuteHook;
import com.pseudosmp.tools.integrations.hooks.VanishHook;

public class EssentialsIntegration implements PluginIntegration, VanishHook, MuteHook {
    private Essentials essentials;
    private boolean enabled = false;

    @Override
    public String getName() {
        return "Essentials";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setup() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
        if (plugin instanceof Essentials && plugin.isEnabled()) {
            this.essentials = (Essentials) plugin;
            this.enabled = true;
        } else {
            this.essentials = null;
            this.enabled = false;
        }
    }

    @Override
    public boolean isVanished(Player player) {
        if (!enabled || essentials == null || player == null) {
            return false;
        }
        try {
            User user = essentials.getUser(player.getUniqueId());
            return user != null && user.isVanished();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isMuted(Player player) {
        if (!enabled || essentials == null || player == null) {
            return false;
        }
        try {
            User user = essentials.getUser(player.getUniqueId());
            return user != null && user.isMuted();
        } catch (Exception e) {
            return false;
        }
    }
}
