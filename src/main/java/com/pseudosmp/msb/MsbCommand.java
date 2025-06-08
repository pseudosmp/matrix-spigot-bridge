package com.pseudosmp.msb;

import org.bukkit.command.*;
import com.pseudosmp.msb.commands.*;
// import other command classes as needed

import java.util.*;

public class MsbCommand implements CommandExecutor, TabCompleter {
    private final MatrixSpigotBridge plugin;
    private final Map<String, CommandExecutor> subcommands = new HashMap<>();

    public MsbCommand(MatrixSpigotBridge plugin) {
        this.plugin = plugin;
        subcommands.put("reload", new ReloadConfig(plugin));
        subcommands.put("restart", new RestartBridge(plugin));
        subcommands.put("ping", new PingBridge(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eUsage: /msb <subcommand>");
            return true;
        }
        CommandExecutor sub = subcommands.get(args[0].toLowerCase());
        if (sub != null) {
            // Pass subcommand arguments (excluding the subcommand itself)
            return sub.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
        }
        sender.sendMessage("§cUnknown subcommand. Use /msb for help.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String sub : subcommands.keySet()) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        // Optionally, delegate to subcommand tab completers here
        return Collections.emptyList();
    }
}