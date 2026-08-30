package com.starrypvp.command;

import com.starrypvp.StarryPvP;
import com.starrypvp.arena.Arena;
import com.starrypvp.data.DataManager;
import com.starrypvp.gui.SetupGui;
import com.starrypvp.match.Match;
import com.starrypvp.party.PartyManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PvpCommand implements CommandExecutor, TabCompleter {
    private final StarryPvP plugin;
    private final Map<UUID, Long> profileReset = new HashMap<UUID, Long>();
    private final Map<UUID, Long> statsReset = new HashMap<UUID, Long>();
    private final DecimalFormat ratioFormat = new DecimalFormat("0.00");

    public PvpCommand(StarryPvP plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.message("players-only"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("starrypvp.use")) {
            player.sendMessage(plugin.message("no-permission"));
            return true;
        }

        if (args.length == 0) {
            showQuickStart(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("help")) {
            showHelp(player);
        } else if (sub.equals("accept")) {
            plugin.getMatchManager().accept(player, args.length > 1 ? args[1] : null);
        } else if (sub.equals("deny")) {
            plugin.getMatchManager().deny(player, args.length > 1 ? args[1] : null);
        } else if (sub.equals("leave") || sub.equals("forfeit")) {
            if (plugin.getMatchManager().isPublicFfa(player)) {
                plugin.getMatchManager().leaveFfa(player);
            } else {
                plugin.getMatchManager().requestForfeit(player);
            }
        } else if (sub.equals("practice")) {
            require(player, "starrypvp.practice", new Runnable() {
                public void run() {
                    plugin.getMatchManager().togglePractice(player);
                }
            });
        } else if (sub.equals("stats")) {
            showStats(player, args.length > 1 ? Bukkit.getOfflinePlayer(args[1]) : player);
        } else if (sub.equals("leaderboard") || sub.equals("top")) {
            showLeaderboard(player, args.length > 1 && args[1].equalsIgnoreCase("ratio"));
        } else if (sub.equals("spectate") || sub.equals("view")) {
            spectate(player, args);
        } else if (sub.equals("party")) {
            party(player, args);
        } else if (sub.equals("team")) {
            chooseTeam(player, args);
        } else if (sub.equals("ffa")) {
            ffa(player, args);
        } else if (sub.equals("toggle")) {
            adminToggle(player);
        } else if (sub.equals("end")) {
            adminEnd(player, args);
        } else if (sub.equals("reset")) {
            adminReset(player, args);
        } else if (sub.equals("reload")) {
            adminReload(player);
        } else if (sub.equals("arena")) {
            arena(player, args);
        } else {
            challenge(player, args);
        }

        return true;
    }

    private void showQuickStart(Player player) {
        player.sendMessage(plugin.color("&8&m--------------------------------"));
        player.sendMessage(plugin.color("&d&lStarryPvP Match Creation"));
        player.sendMessage(plugin.color("&f/pvp <player> &7- Configure and send a duel"));
        player.sendMessage(plugin.color("&f/pvp <player> 2v2|3v3|5v5 &7- Party duel"));
        player.sendMessage(plugin.color("&f/pvp practice &7- Toggle practice matchmaking"));
        player.sendMessage(plugin.color("&f/pvp ffa join &7- Enter persistent FFA"));
        player.sendMessage(plugin.color("&7Use &f/pvp help &7for the complete guide."));
        player.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    private void showHelp(Player player) {
        player.sendMessage(plugin.color("&8&m--------------------------------"));
        player.sendMessage(plugin.color("&d&lStarryPvP Commands"));

        sendHelp(player, "starrypvp.duel", "/pvp <player>", "Configure and send a duel");
        sendHelp(player, "starrypvp.duel", "/pvp accept [player]", "Accept a challenge");
        sendHelp(player, "starrypvp.duel", "/pvp deny [player]", "Deny a challenge");
        sendHelp(player, "starrypvp.use", "/pvp leave", "Confirm and forfeit a match");
        sendHelp(player, "starrypvp.practice", "/pvp practice", "Toggle practice queue");
        sendHelp(player, "starrypvp.use", "/pvp stats [player]", "View PvP statistics");
        sendHelp(player, "starrypvp.use", "/pvp top [ratio]", "View rankings");
        sendHelp(player, "starrypvp.spectate", "/pvp spectate [player]", "View active matches");
        sendHelp(player, "starrypvp.party", "/pvp party", "Manage your party");
        sendHelp(player, "starrypvp.ffa", "/pvp ffa join|leave", "Manage FFA participation");

        if (player.hasPermission("starrypvp.admin")) {
            player.sendMessage(plugin.color("&cAdmin: &f/pvp toggle, end, reset, reload, arena"));
        }

        player.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    private void sendHelp(Player player, String permission, String command, String description) {
        if (!player.hasPermission(permission)) {
            return;
        }

        TextComponent line = new TextComponent(command + " - " + description);
        line.setColor(net.md_5.bungee.api.ChatColor.WHITE);
        line.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
        player.spigot().sendMessage(line);
    }

    private void challenge(Player player, String[] args) {
        if (!player.hasPermission("starrypvp.duel")) {
            player.sendMessage(plugin.message("no-permission"));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(plugin.message("player-not-found"));
            return;
        }

        SetupGui.ChallengeType type = args.length > 1
                ? SetupGui.ChallengeType.TEAM
                : SetupGui.ChallengeType.DUEL;

        plugin.getSetupGui().open(player, target, type);
    }

    private void showStats(Player viewer, OfflinePlayer target) {
        DataManager.Profile profile = plugin.getDataManager().get(target);

        viewer.sendMessage(plugin.color("&8&m--------------------------------"));
        viewer.sendMessage(plugin.color("&d&lPvP Stats: &f" + profile.getName()));
        viewer.sendMessage(plugin.color("&aWins: &f" + profile.getWins()));
        viewer.sendMessage(plugin.color("&cLosses: &f" + profile.getLosses()));
        viewer.sendMessage(plugin.color("&bW/L Ratio: &f" + ratioFormat.format(profile.getRatio())));
        viewer.sendMessage(plugin.color("&eCurrent Streak: &f" + profile.getStreak()));
        viewer.sendMessage(plugin.color("&6Highest Streak: &f" + profile.getHighestStreak()));
        viewer.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    private void showLeaderboard(Player player, boolean ratio) {
        List<DataManager.Profile> profiles = plugin.getDataManager().leaderboard(ratio);
        player.sendMessage(plugin.color("&8&m--------------------------------"));
        player.sendMessage(plugin.color("&d&lStarryPvP " + (ratio ? "Ratio" : "Wins") + " Leaderboard"));

        int limit = Math.min(10, profiles.size());
        for (int index = 0; index < limit; index++) {
            DataManager.Profile profile = profiles.get(index);
            String value = ratio
                    ? ratioFormat.format(profile.getRatio())
                    : String.valueOf(profile.getWins());
            player.sendMessage(plugin.color("&f" + (index + 1) + ". &d" + profile.getName() + " &7- &f" + value));
        }

        player.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    private void spectate(Player player, String[] args) {
        if (!player.hasPermission("starrypvp.spectate")) {
            player.sendMessage(plugin.message("no-permission"));
            return;
        }

        if (args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(plugin.message("player-not-found"));
                return;
            }
            plugin.getMatchManager().spectate(player, target);
            return;
        }

        player.sendMessage(plugin.color("&d&lLive StarryPvP Matches"));

        for (Match match : plugin.getMatchManager().activeMatches().values()) {
            Player target = null;
            for (UUID uuid : match.getParticipants()) {
                target = Bukkit.getPlayer(uuid);
                if (target != null) {
                    break;
                }
            }

            if (target == null) {
                continue;
            }

            TextComponent line = new TextComponent(
                    match.getArena().getName() + " | " + match.getType().name() + " | [SPECTATE]"
            );
            line.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            line.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/pvp spectate " + target.getName()
            ));
            player.spigot().sendMessage(line);
        }
    }

    private void party(Player player, String[] args) {
        if (!player.hasPermission("starrypvp.party")) {
            player.sendMessage(plugin.message("no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.color("&f/pvp party create"));
            player.sendMessage(plugin.color("&f/pvp party invite <player>"));
            player.sendMessage(plugin.color("&f/pvp party accept"));
            player.sendMessage(plugin.color("&f/pvp party kick <player>"));
            player.sendMessage(plugin.color("&f/pvp party leave"));
            player.sendMessage(plugin.color("&f/pvp party disband"));
            player.sendMessage(plugin.color("&f/pvp party match <leader>"));
            return;
        }

        String action = args[1].toLowerCase();

        if (action.equals("create")) {
            plugin.getPartyManager().create(player);
            player.sendMessage(plugin.color("&aYour party was created."));
        } else if (action.equals("accept")) {
            player.sendMessage(plugin.getPartyManager().acceptInvite(player)
                    ? plugin.color("&aYou joined the party.")
                    : plugin.color("&cYou have no valid party invitation."));
        } else if (action.equals("invite") && args.length > 2) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                player.sendMessage(plugin.message("player-not-found"));
                return;
            }

            if (plugin.getPartyManager().invite(player, target)) {
                target.sendMessage(plugin.color("&d" + player.getName() + " invited you to a PvP party."));
                target.sendMessage(plugin.color("&fUse /pvp party accept to join."));
                player.sendMessage(plugin.color("&aParty invitation sent."));
            } else {
                player.sendMessage(plugin.color("&cYou must be the leader of a party."));
            }
        } else if (action.equals("kick") && args.length > 2) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                player.sendMessage(plugin.message("player-not-found"));
                return;
            }
            player.sendMessage(plugin.getPartyManager().kick(player, target)
                    ? plugin.color("&aPlayer removed from the party.")
                    : plugin.color("&cUnable to remove that player."));
        } else if (action.equals("leave")) {
            player.sendMessage(plugin.getPartyManager().leave(player)
                    ? plugin.color("&eYou left the party.")
                    : plugin.color("&cYou are not in a party."));
        } else if (action.equals("disband")) {
            player.sendMessage(plugin.getPartyManager().disband(player)
                    ? plugin.color("&eYour party was disbanded.")
                    : plugin.color("&cOnly a party leader can disband the party."));
        } else if (action.equals("match") && args.length > 2) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                player.sendMessage(plugin.message("player-not-found"));
                return;
            }
            plugin.getSetupGui().open(player, target, SetupGui.ChallengeType.TEAM);
        } else {
            player.sendMessage(plugin.color("&cInvalid party command."));
        }
    }

    private void chooseTeam(Player player, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("red") && !args[1].equalsIgnoreCase("blue")) {
            player.sendMessage(plugin.color("&cUsage: /pvp team <red|blue>"));
            return;
        }

        plugin.getPartyManager().setSide(player, args[1]);
        player.sendMessage(plugin.color(
                args[1].equalsIgnoreCase("red")
                        ? "&cYou selected the Red team."
                        : "&9You selected the Blue team."
        ));
    }

    private void ffa(Player player, String[] args) {
        if (!player.hasPermission("starrypvp.ffa")) {
            player.sendMessage(plugin.message("no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.color("&f/pvp ffa join [kit]"));
            player.sendMessage(plugin.color("&f/pvp ffa leave"));
            player.sendMessage(plugin.color("&f/pvp ffa custom <players...>"));
            return;
        }

        if (args[1].equalsIgnoreCase("join")) {
            plugin.getMatchManager().joinFfa(player);
        } else if (args[1].equalsIgnoreCase("leave")) {
            plugin.getMatchManager().leaveFfa(player);
        } else if (args[1].equalsIgnoreCase("custom")) {
            player.sendMessage(plugin.color("&ePrivate FFA setup uses the standard GUI and requires configured FFA spawns."));
            plugin.getSetupGui().open(player, null, SetupGui.ChallengeType.CUSTOM_FFA);
        }
    }

    private void adminToggle(Player player) {
        if (!player.hasPermission("starrypvp.admin.toggle")) {
            player.sendMessage(plugin.message("no-permission"));
            return;
        }

        boolean enabled = !plugin.getMatchManager().isDuelRequestsEnabled();
        plugin.getMatchManager().setDuelRequestsEnabled(enabled);
        Bukkit.broadcastMessage(plugin.color("&8[&dStarryPvP&8] &fDuel requests are now " +
                (enabled ? "&aenabled" : "&cdisabled") + "&f."));
    }

    private void adminEnd(Player player, String[] args) {
        if (!player.hasPermission("starrypvp.admin.end")) {
            player.sendMessage(plugin.message("no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.color("&cUsage: /pvp end <player>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.message("player-not-found"));
            return;
        }

        plugin.getMatchManager().forceEnd(target);
        player.sendMessage(plugin.color("&aThe match was terminated."));
    }

    private void adminReset(Player player, String[] args) {
        if (!player.hasPermission("starrypvp.admin.reset")) {
            player.sendMessage(plugin.message("no-permission"));
            return;
        }

        boolean statsOnly = args.length > 1 && args[1].equalsIgnoreCase("stats");
        int targetIndex = statsOnly ? 2 : 1;

        if (args.length <= targetIndex) {
            player.sendMessage(plugin.color(statsOnly
                    ? "&cUsage: /pvp reset stats <player>"
                    : "&cUsage: /pvp reset <player>"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[targetIndex]);
        Map<UUID, Long> confirmations = statsOnly ? statsReset : profileReset;
        long now = System.currentTimeMillis();
        Long expiration = confirmations.get(player.getUniqueId());

        if (expiration == null || expiration < now) {
            confirmations.put(player.getUniqueId(), now + 13000L);
            player.sendMessage(plugin.color("&cRun the same command again within 13 seconds to confirm."));
            return;
        }

        confirmations.remove(player.getUniqueId());

        if (statsOnly) {
            plugin.getDataManager().resetStats(target);
            player.sendMessage(plugin.color("&aThe player's PvP statistics were reset."));
        } else {
            plugin.getDataManager().resetProfile(target);
            player.sendMessage(plugin.color("&aThe player's complete StarryPvP profile was reset."));
        }
    }

    private void adminReload(Player player) {
        if (!player.hasPermission("starrypvp.admin.reload")) {
            player.sendMessage(plugin.message("no-permission"));
            return;
        }

        plugin.reloadFiles();
        player.sendMessage(plugin.message("reload-complete"));
    }

    private void arena(Player player, String[] args) {
        if (!player.hasPermission("starrypvp.admin.arena")) {
            player.sendMessage(plugin.message("no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.color("&f/pvp arena create <name> <DUEL|TEAM|FFA>"));
            player.sendMessage(plugin.color("&f/pvp arena delete <name>"));
            player.sendMessage(plugin.color("&f/pvp arena list"));
            player.sendMessage(plugin.color("&f/pvp arena addspawn <name> <RED|BLUE|FFA|SPECTATOR>"));
            player.sendMessage(plugin.color("&f/pvp arena tp <name>"));
            return;
        }

        String action = args[1].toLowerCase();

        if (action.equals("create") && args.length > 3) {
            try {
                Arena.Mode mode = Arena.Mode.valueOf(args[3].toUpperCase());
                player.sendMessage(plugin.getArenaManager().create(args[2], mode)
                        ? plugin.color("&aArena created.")
                        : plugin.color("&cAn arena with that name already exists."));
            } catch (IllegalArgumentException exception) {
                player.sendMessage(plugin.color("&cMode must be DUEL, TEAM, or FFA."));
            }
        } else if (action.equals("delete") && args.length > 2) {
            player.sendMessage(plugin.getArenaManager().delete(args[2])
                    ? plugin.color("&aArena deleted.")
                    : plugin.color("&cArena not found or currently occupied."));
        } else if (action.equals("list")) {
            player.sendMessage(plugin.color("&d&lConfigured Arenas"));
            for (Arena arena : plugin.getArenaManager().all()) {
                player.sendMessage(plugin.color("&f" + arena.getName() + " &7- " +
                        arena.getMode().name() + " - " + (arena.isReady() ? "&aReady" : "&cIncomplete")));
            }
        } else if (action.equals("addspawn") && args.length > 3) {
            player.sendMessage(plugin.getArenaManager().addSpawn(args[2], args[3], player.getLocation())
                    ? plugin.color("&aArena spawn point added.")
                    : plugin.color("&cInvalid arena or spawn type."));
        } else if (action.equals("tp") && args.length > 2) {
            Arena arena = plugin.getArenaManager().get(args[2]);
            if (arena == null) {
                player.sendMessage(plugin.color("&cArena not found."));
                return;
            }

            if (!arena.getRedSpawns().isEmpty()) {
                player.teleport(arena.getRedSpawns().get(0));
            } else if (!arena.getFfaSpawns().isEmpty()) {
                player.teleport(arena.getFfaSpawns().get(0));
            } else if (arena.getSpectatorSpawn() != null) {
                player.teleport(arena.getSpectatorSpawn());
            } else {
                player.sendMessage(plugin.color("&cThat arena has no spawn points."));
            }
        } else {
            player.sendMessage(plugin.color("&cInvalid arena command."));
        }
    }

    private void require(Player player, String permission, Runnable action) {
        if (!player.hasPermission(permission)) {
            player.sendMessage(plugin.message("no-permission"));
            return;
        }
        action.run();
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<String>(Arrays.asList(
                    "help", "accept", "deny", "leave", "forfeit", "practice", "stats",
                    "leaderboard", "top", "spectate", "view", "party", "team", "ffa"
            ));

            if (sender.hasPermission("starrypvp.admin")) {
                values.addAll(Arrays.asList("toggle", "end", "reset", "reload", "arena"));
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                values.add(player.getName());
            }

            return filter(values, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("party")) {
            return filter(Arrays.asList("create", "disband", "invite", "accept", "kick", "leave", "match"), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("ffa")) {
            return filter(Arrays.asList("join", "leave", "custom"), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("team")) {
            return filter(Arrays.asList("red", "blue"), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            return filter(Arrays.asList("create", "delete", "list", "addspawn", "tp"), args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String input) {
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            if (value.toLowerCase().startsWith(input.toLowerCase())) {
                result.add(value);
            }
        }
        return result;
    }
}
