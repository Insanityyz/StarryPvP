package com.starrypvp.command;

import com.starrypvp.StarryPvP;
import com.starrypvp.arena.ArenaZoneManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ZoneCommand implements CommandExecutor {
    private final StarryPvP plugin;

    public ZoneCommand(StarryPvP plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("starrypvp.admin.zone")) {
            sender.sendMessage(plugin.color("&cYou do not have permission."));
            return true;
        }

        if (args.length == 0) {
            help(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        if (action.equals("reload")) {
            plugin.getArenaZoneManager().load();
            plugin.getArenaZoneManager().start();
            sender.sendMessage(plugin.color("&aArena zones reloaded."));
            return true;
        }

        if (action.equals("list")) {
            for (ArenaZoneManager.Zone zone : plugin.getArenaZoneManager().zones()) {
                sender.sendMessage(plugin.color("&d" + zone.getName() + " &7arena=" + zone.getArenaName()
                        + " radius=" + zone.getRadius() + " y=" + zone.getMinY() + "-" + zone.getMaxY()));
            }
            return true;
        }

        if (action.equals("check")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.color("&cPlayers only."));
                return true;
            }

            Player player = (Player) sender;
            ArenaZoneManager.Zone zone = plugin.getArenaZoneManager().findZone(player.getLocation());
            sender.sendMessage(plugin.color(zone == null
                    ? "&7You are not standing in a zone."
                    : "&dYou are in zone &f" + zone.getName()));
            return true;
        }

        if ((action.equals("allow") || action.equals("deny")) && args.length >= 3) {
            boolean changed = plugin.getArenaZoneManager().whitelist(args[1], args[2], action.equals("allow"));
            sender.sendMessage(plugin.color(changed
                    ? "&aWhitelist updated for &f" + args[1]
                    : "&cNothing changed. Check the zone name and entry."));
            return true;
        }

        help(sender);
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(plugin.color("&d/starryzone list"));
        sender.sendMessage(plugin.color("&d/starryzone check"));
        sender.sendMessage(plugin.color("&d/starryzone allow <zone> <player>"));
        sender.sendMessage(plugin.color("&d/starryzone deny <zone> <player>"));
        sender.sendMessage(plugin.color("&d/starryzone reload"));
    }
}
