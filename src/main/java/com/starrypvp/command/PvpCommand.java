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
import org.bukkit.Location;
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
            plugin.getMenuGui().openModes(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("help")) {
            showHelp(player);
        } else if (sub.equals("duel") || sub.equals("duels") || sub.equals("challenge")) {
            if (args.length < 2) {
                player.sendMessage(plugin.color("&cUsage: /pvp duel <player> [2v2|3v3|5v5]"));
            } else {
                challenge(player, Arrays.copyOfRange(args, 1, args.length));
            }
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
        } else if (sub.equals("practice") || sub.equals("random")) {
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
        } else if (sub.equals("unstuck")) {
            plugin.getMatchManager().forceUnstuck(player);
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
        sendHelp(player, "starrypvp.use", "/pvp unstuck", "Clear broken spectator state");
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

        if (args.length == 0) {
            player.sendMessage(plugin.color("&cUsage: /pvp duel <player> [2v2|3v3|5v5]"));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null) {
            player.sendMessage(plugin.message("player-not-found"));
            return;
        }

        SetupGui.ChallengeType type = SetupGui.ChallengeType.DUEL;

        if (args.length > 1) {
            String size = args[1].toLowerCase();

            if (!size.equals("2v2") && !size.equals("3v3") && !size.equals("5v5")) {
                player.sendMessage(plugin.color("&cTeam size must be 2v2, 3v3, or 5v5."));
                return;
            }

            int expectedSize = Integer.parseInt(size.substring(0, 1));
            PartyManager.Party first = plugin.getPartyManager().get(player);
            PartyManager.Party second = plugin.getPartyManager().get(target);

            if (first == null || second == null || first == second) {
                player.sendMessage(plugin.color("&cBoth players must lead separate parties."));
                return;
            }

            if (!plugin.getPartyManager().isLeader(player) ||
                    !plugin.getPartyManager().isLeader(target)) {
                player.sendMessage(plugin.color("&cBoth selected players must be party leaders."));
                return;
            }

            int firstSize = plugin.getPartyManager().onlineMembers(first).size();
            int secondSize = plugin.getPartyManager().onlineMembers(second).size();

            if (firstSize != expectedSize || secondSize != expectedSize) {
                player.sendMessage(plugin.color("&cBoth parties must have exactly " +
                        expectedSize + " online members for " + size + "."));
                return;
            }

            type = SetupGui.ChallengeType.TEAM;
        }

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

        if (args.length > 1 &&
                (args[1].equalsIgnoreCase("leave") ||
                        args[1].equalsIgnoreCase("exit") ||
                        args[1].equalsIgnoreCase("stop"))) {
            plugin.getMatchManager().stopSpectating(player);
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

        if (plugin.getMatchManager().isSpectating(player)) {
            player.sendMessage(plugin.color("&7Use &f/pvp spectate leave &7to stop spectating."));
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
            player.sendMessage(plugin.color("&f/pvp arena list [name]"));
            player.sendMessage(plugin.color("&f/pvp arena info <name>"));
            player.sendMessage(plugin.color("&f/pvp arena spawn add <name> <RED|BLUE|FFA|SPECTATOR>"));
            player.sendMessage(plugin.color("&f/pvp arena spawn set <name> <type> [index]"));
            player.sendMessage(plugin.color("&f/pvp arena spawn delete <name> <type> [index]"));
            player.sendMessage(plugin.color("&f/pvp arena spawn tp <name> <type> [index]"));
            player.sendMessage(plugin.color("&f/pvp arena spawn list <name> [type]"));
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
        } else if (action.equals("list") && args.length > 2) {
            showArenaInfo(player, args[2]);
        } else if (action.equals("list")) {
            player.sendMessage(plugin.color("&d&lConfigured Arenas"));

            for (Arena arena : plugin.getArenaManager().all()) {
                player.sendMessage(plugin.color("&f" + arena.getName() + " &7- " +
                        arena.getMode().name() + " - " +
                        (arena.isReady() ? "&aReady" : "&cIncomplete")));
            }
        } else if (action.equals("info") && args.length > 2) {
            showArenaInfo(player, args[2]);
        } else if (action.equals("addspawn") && args.length > 3) {
            player.sendMessage(plugin.getArenaManager().addSpawn(
                    args[2],
                    args[3],
                    player.getLocation()
            )
                    ? plugin.color("&aArena spawn point added.")
                    : plugin.color("&cInvalid arena, spawn type, or the arena is occupied."));
        } else if (action.equals("spawn")) {
            arenaSpawn(player, args);
        } else if (action.equals("tp") && args.length > 2) {
            Arena arena = plugin.getArenaManager().get(args[2]);

            if (arena == null) {
                player.sendMessage(plugin.color("&cArena not found."));
                return;
            }

            Location destination = arena.getSpawn("RED", 1);

            if (destination == null) {
                destination = arena.getSpawn("FFA", 1);
            }

            if (destination == null) {
                destination = arena.getSpawn("SPECTATOR", 1);
            }

            if (destination == null) {
                player.sendMessage(plugin.color("&cThat arena has no spawn points."));
                return;
            }

            player.teleport(destination);
            player.sendMessage(plugin.color("&aTeleported to arena &f" + arena.getName() + "&a."));
        } else {
            player.sendMessage(plugin.color("&cInvalid arena command."));
        }
    }

    private void arenaSpawn(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(plugin.color("&cUsage: /pvp arena spawn <add|set|delete|tp|list> <arena> <type> [index]"));
            return;
        }

        String operation = args[2].toLowerCase();
        String arenaName = args[3];
        Arena arena = plugin.getArenaManager().get(arenaName);

        if (arena == null) {
            player.sendMessage(plugin.color("&cArena not found."));
            return;
        }

        if (operation.equals("list")) {
            if (args.length > 4) {
                showSpawnList(player, arena, args[4]);
            } else {
                showSpawnList(player, arena, "RED");
                showSpawnList(player, arena, "BLUE");
                showSpawnList(player, arena, "FFA");
                showSpawnList(player, arena, "SPECTATOR");
            }
            return;
        }

        if (args.length < 5) {
            player.sendMessage(plugin.color("&cA spawn type is required: RED, BLUE, FFA, or SPECTATOR."));
            return;
        }

        String type = args[4].toUpperCase();

        if (!type.equals("RED") &&
                !type.equals("BLUE") &&
                !type.equals("FFA") &&
                !type.equals("SPECTATOR")) {
            player.sendMessage(plugin.color("&cSpawn type must be RED, BLUE, FFA, or SPECTATOR."));
            return;
        }

        int index = 1;

        if (args.length > 5) {
            try {
                index = Integer.parseInt(args[5]);
            } catch (NumberFormatException exception) {
                player.sendMessage(plugin.color("&cThe spawn index must be a positive number."));
                return;
            }
        }

        if (index < 1) {
            player.sendMessage(plugin.color("&cThe spawn index must be a positive number."));
            return;
        }

        if (operation.equals("add")) {
            boolean changed = plugin.getArenaManager().addSpawn(
                    arenaName,
                    type,
                    player.getLocation()
            );

            player.sendMessage(changed
                    ? plugin.color("&aAdded a new &f" + type + " &aspawn.")
                    : plugin.color("&cThe spawn could not be added. The arena may be occupied."));
        } else if (operation.equals("set") || operation.equals("move")) {
            boolean changed = plugin.getArenaManager().setSpawn(
                    arenaName,
                    type,
                    index,
                    player.getLocation()
            );

            player.sendMessage(changed
                    ? plugin.color("&aSet &f" + type + " #" + index + " &ato your current location.")
                    : plugin.color("&cThat spawn cannot be set. Use the next available index or edit an existing spawn."));
        } else if (operation.equals("delete") || operation.equals("remove")) {
            boolean changed = plugin.getArenaManager().removeSpawn(
                    arenaName,
                    type,
                    index
            );

            player.sendMessage(changed
                    ? plugin.color("&aDeleted &f" + type + " #" + index + "&a.")
                    : plugin.color("&cThat spawn does not exist or the arena is occupied."));
        } else if (operation.equals("tp")) {
            Location location = plugin.getArenaManager().getSpawn(
                    arenaName,
                    type,
                    index
            );

            if (location == null) {
                player.sendMessage(plugin.color("&cThat spawn does not exist."));
                return;
            }

            player.teleport(location);
            player.sendMessage(plugin.color("&aTeleported to &f" + type + " #" + index + "&a."));
        } else {
            player.sendMessage(plugin.color("&cSpawn operation must be add, set, move, delete, remove, tp, or list."));
        }
    }

    private void showSpawnList(Player player, Arena arena, String type) {
        String normalized = type.toUpperCase();
        int count = arena.getSpawnCount(normalized);

        player.sendMessage(plugin.color("&d" + normalized + " spawns for &f" + arena.getName() + "&d:"));

        if (count == 0) {
            player.sendMessage(plugin.color("&7None"));
            return;
        }

        for (int index = 1; index <= count; index++) {
            Location location = arena.getSpawn(normalized, index);

            if (location == null || location.getWorld() == null) {
                continue;
            }

            player.sendMessage(plugin.color(
                    "&f#" + index +
                            " &7- &d" + location.getWorld().getName() +
                            " &7(" + location.getBlockX() +
                            ", " + location.getBlockY() +
                            ", " + location.getBlockZ() + ")"
            ));
        }
    }

    private void showArenaInfo(Player player, String name) {
        Arena arena = plugin.getArenaManager().get(name);

        if (arena == null) {
            player.sendMessage(plugin.color("&cArena not found."));
            return;
        }

        player.sendMessage(plugin.color("&8&m--------------------------------"));
        player.sendMessage(plugin.color("&d&lArena: &f" + arena.getName()));
        player.sendMessage(plugin.color("&fMode: &d" + arena.getMode().name()));
        player.sendMessage(plugin.color("&fReady: " + (arena.isReady() ? "&aYes" : "&cNo")));
        player.sendMessage(plugin.color("&fOccupied: " + (arena.isOccupied() ? "&cYes" : "&aNo")));
        player.sendMessage(plugin.color("&fRed spawns: &d" + arena.getRedSpawns().size()));
        player.sendMessage(plugin.color("&fBlue spawns: &d" + arena.getBlueSpawns().size()));
        player.sendMessage(plugin.color("&fFFA spawns: &d" + arena.getFfaSpawns().size()));
        player.sendMessage(plugin.color("&fSpectator spawn: " +
                (arena.getSpectatorSpawn() == null ? "&cNot set" : "&aSet")));
        player.sendMessage(plugin.color("&fProtection radius: &d" +
                plugin.getConfig().getDouble("arena-protection.radius", 150.0D)));
        player.sendMessage(plugin.color("&8&m--------------------------------"));
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
                    "help", "duel", "duels", "accept", "deny", "leave", "forfeit",
                    "practice", "random", "stats", "leaderboard", "top",
                    "spectate", "view", "unstuck", "party", "team", "ffa"
            ));

            if (sender.hasPermission("starrypvp.admin")) {
                values.addAll(Arrays.asList("toggle", "end", "reset", "reload", "arena"));
            }

            return filter(values, args[0]);
        }

        if (args.length == 2 &&
                (args[0].equalsIgnoreCase("spectate") ||
                        args[0].equalsIgnoreCase("view"))) {
            List<String> values = onlinePlayerNames();
            values.add("leave");
            values.add("exit");
            values.add("stop");
            return filter(values, args[1]);
        }

        if (args.length == 2 &&
                (args[0].equalsIgnoreCase("duel") ||
                        args[0].equalsIgnoreCase("duels") ||
                        args[0].equalsIgnoreCase("challenge") ||
                        args[0].equalsIgnoreCase("accept") ||
                        args[0].equalsIgnoreCase("deny") ||
                        args[0].equalsIgnoreCase("stats") ||
                        args[0].equalsIgnoreCase("end"))) {
            return filter(onlinePlayerNames(), args[1]);
        }

        if (args.length == 3 &&
                (args[0].equalsIgnoreCase("duel") ||
                        args[0].equalsIgnoreCase("duels") ||
                        args[0].equalsIgnoreCase("challenge"))) {
            return filter(Arrays.asList("2v2", "3v3", "5v5"), args[2]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("party")) {
            return filter(Arrays.asList(
                    "create", "disband", "invite", "accept", "kick", "leave", "match"
            ), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("ffa")) {
            return filter(Arrays.asList("join", "leave", "custom"), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("team")) {
            return filter(Arrays.asList("red", "blue"), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            return filter(Arrays.asList(
                    "create", "delete", "list", "info", "addspawn", "spawn", "tp"
            ), args[1]);
        }

        if (args.length == 3 &&
                args[0].equalsIgnoreCase("party") &&
                (args[1].equalsIgnoreCase("invite") ||
                        args[1].equalsIgnoreCase("kick") ||
                        args[1].equalsIgnoreCase("match"))) {
            return filter(onlinePlayerNames(), args[2]);
        }

        if (args.length == 3 &&
                args[0].equalsIgnoreCase("arena") &&
                args[1].equalsIgnoreCase("spawn")) {
            return filter(Arrays.asList(
                    "add", "set", "move", "delete", "remove", "tp", "list"
            ), args[2]);
        }

        if (args.length == 3 &&
                args[0].equalsIgnoreCase("arena") &&
                (args[1].equalsIgnoreCase("delete") ||
                        args[1].equalsIgnoreCase("info") ||
                        args[1].equalsIgnoreCase("list") ||
                        args[1].equalsIgnoreCase("tp") ||
                        args[1].equalsIgnoreCase("addspawn"))) {
            return filter(arenaNames(), args[2]);
        }

        if (args.length == 4 &&
                args[0].equalsIgnoreCase("arena") &&
                args[1].equalsIgnoreCase("spawn")) {
            return filter(arenaNames(), args[3]);
        }

        if (args.length == 4 &&
                args[0].equalsIgnoreCase("arena") &&
                args[1].equalsIgnoreCase("addspawn")) {
            return filter(Arrays.asList("RED", "BLUE", "FFA", "SPECTATOR"), args[3]);
        }

        if (args.length == 5 &&
                args[0].equalsIgnoreCase("arena") &&
                args[1].equalsIgnoreCase("spawn")) {
            return filter(Arrays.asList("RED", "BLUE", "FFA", "SPECTATOR"), args[4]);
        }

        if (args.length == 6 &&
                args[0].equalsIgnoreCase("arena") &&
                args[1].equalsIgnoreCase("spawn")) {
            Arena arena = plugin.getArenaManager().get(args[3]);

            if (arena == null) {
                return Collections.emptyList();
            }

            List<String> indexes = new ArrayList<String>();
            int count = arena.getSpawnCount(args[4]);

            for (int index = 1; index <= count; index++) {
                indexes.add(String.valueOf(index));
            }

            if (args[2].equalsIgnoreCase("set") ||
                    args[2].equalsIgnoreCase("move")) {
                indexes.add(String.valueOf(count + 1));
            }

            return filter(indexes, args[5]);
        }

        return Collections.emptyList();
    }

    private List<String> arenaNames() {
        List<String> names = new ArrayList<String>();

        for (Arena arena : plugin.getArenaManager().all()) {
            names.add(arena.getName());
        }

        return names;
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<String>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }

        return names;
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
