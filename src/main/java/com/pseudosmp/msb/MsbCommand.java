package com.pseudosmp.msb;

import org.bukkit.command.*;
import com.pseudosmp.msb.commands.*;

import java.util.*;

public class MsbCommand implements CommandExecutor, TabCompleter {
    private final Map<String, CommandExecutor> subcommands = new HashMap<>();

    public MsbCommand(MatrixSpigotBridge plugin) {
        subcommands.put("reload", new ReloadConfig(plugin));
        subcommands.put("restart", new RestartBridge(plugin));
        subcommands.put("ping", new PingBridge(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(command.getUsage());
            return true;
        }
        CommandExecutor sub = subcommands.get(args[0].toLowerCase());
        if (sub != null) {
            // Pass subcommand arguments (excluding the subcommand itself)
            return sub.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
        }
        sender.sendMessage(command.getUsage());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String sub : subcommands.keySet()) {
                // Only show subcommands the sender has permission for
                String perm = "msb.command." + sub;
                if (sub.startsWith(args[0].toLowerCase()) && sender.hasPermission(perm)) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}