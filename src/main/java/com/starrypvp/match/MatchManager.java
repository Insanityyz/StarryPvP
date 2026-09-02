package com.starrypvp.match;

import com.starrypvp.StarryPvP;
import com.starrypvp.arena.Arena;
import com.starrypvp.gui.SetupGui;
import com.starrypvp.party.PartyManager;
import com.starrypvp.util.InventorySnapshot;
import com.starrypvp.util.ItemUtil;
import com.starrypvp.util.SoundUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import com.starrypvp.util.CombatUtil;
import java.util.concurrent.ConcurrentHashMap;

public final class MatchManager {
    private static final class Request {
        private final UUID sender;
        private final UUID receiver;
        private final MatchSettings settings;
        private final SetupGui.ChallengeType type;
        private final long expiresAt;

        private Request(UUID sender, UUID receiver, MatchSettings settings, SetupGui.ChallengeType type, long expiresAt) {
            this.sender = sender;
            this.receiver = receiver;
            this.settings = settings;
            this.type = type;
            this.expiresAt = expiresAt;
        }
    }

    private final StarryPvP plugin;
    private final Map<UUID, Match> matchesByPlayer = new ConcurrentHashMap<UUID, Match>();
    private final Map<UUID, Deque<Request>> requests = new ConcurrentHashMap<UUID, Deque<Request>>();
    private final Map<UUID, Long> forfeitConfirmations = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Location> spectatorReturns = new ConcurrentHashMap<UUID, Location>();
    private final Map<UUID, InventorySnapshot> spectatorSnapshots = new ConcurrentHashMap<UUID, InventorySnapshot>();
    private final Map<UUID, Map<UUID, Boolean>> petSittingStates = new ConcurrentHashMap<UUID, Map<UUID, Boolean>>();
    private final Queue<UUID> practiceQueue = new ArrayDeque<UUID>();
    private final Map<UUID, InventorySnapshot> ffaSnapshots = new ConcurrentHashMap<UUID, InventorySnapshot>();
    private final Set<UUID> publicFfa = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> frozen = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Map<UUID, String[]> matchTeams = new ConcurrentHashMap<UUID, String[]>();
    private final Map<String, int[]> seriesScores = new ConcurrentHashMap<String, int[]>();
    private boolean duelRequestsEnabled = true;
    private Arena publicFfaArena;

    public MatchManager(StarryPvP plugin) {
        this.plugin = plugin;
    }

    public void dispatchFromGui(Player sender, SetupGui.Session session) {
        Player target = session.getTarget() == null ? null : Bukkit.getPlayer(session.getTarget());
        if (session.getType() == SetupGui.ChallengeType.CUSTOM_FFA) {
            return;
        }
        if (target == null) {
            sender.sendMessage(plugin.message("player-not-found"));
            return;
        }

        if (session.getSettings().isOpponentPicksKit()) {
            sender.sendMessage(plugin.color("&dYou let &f" + target.getName() + " &dchoose the loadout."));
            target.sendMessage(plugin.color("&d" + sender.getName() + " &fwants to duel and let you pick the kit."));
            SoundUtil.select(sender);
            SoundUtil.matchFound(target);
            plugin.getSetupGui().openReply(target, sender, session.getType(), session.getSettings());
            return;
        }

        sendRequest(sender, target, session.getSettings(), session.getType());
    }

    public void sendRequest(Player sender, Player receiver, MatchSettings settings, SetupGui.ChallengeType type) {
        if (!duelRequestsEnabled) {
            sender.sendMessage(plugin.message("duels-disabled"));
            return;
        }
        if (sender.equals(receiver)) {
            sender.sendMessage(plugin.color("&cYou cannot challenge yourself."));
            return;
        }
        if (isBusy(sender) || isBusy(receiver)) {
            sender.sendMessage(plugin.message("already-in-match"));
            return;
        }

        long expiration = System.currentTimeMillis() +
                plugin.getConfig().getLong("requests.expiration-seconds", 120L) * 1000L;

        Request request = new Request(sender.getUniqueId(), receiver.getUniqueId(), settings.clone(), type, expiration);
        Deque<Request> pending = requests.get(receiver.getUniqueId());
        if (pending == null) {
            pending = new ArrayDeque<Request>();
            requests.put(receiver.getUniqueId(), pending);
        }
        pending.addLast(request);

        receiver.sendMessage(plugin.color("&8[&dStarryPvP&8] &f" + sender.getName() +
                " challenged you to a duel! &7(" + settings.summary() + ")"));

        TextComponent accept = new TextComponent(">>> [CLICK TO ACCEPT]");
        accept.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        accept.setBold(true);
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp accept " + sender.getName()));

        TextComponent deny = new TextComponent("  [CLICK TO DENY] <<<");
        deny.setColor(net.md_5.bungee.api.ChatColor.RED);
        deny.setBold(true);
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp deny " + sender.getName()));

        accept.addExtra(deny);
        receiver.spigot().sendMessage(accept);
        sender.sendMessage(plugin.message("request-sent").replace("{player}", receiver.getName()));
    }

    public boolean accept(Player receiver, String senderName) {
        Request request = findRequest(receiver, senderName, true);
        if (request == null) {
            receiver.sendMessage(plugin.message("no-request"));
            return false;
        }

        Player sender = Bukkit.getPlayer(request.sender);
        if (sender == null || isBusy(sender) || isBusy(receiver)) {
            receiver.sendMessage(plugin.message("request-expired"));
            return false;
        }

        if (request.type == SetupGui.ChallengeType.TEAM) {
            return startPartyMatch(sender, receiver, request.settings);
        }

        List<Player> red = Collections.singletonList(sender);
        List<Player> blue = Collections.singletonList(receiver);
        return startMatch(Match.Type.DUEL, Arena.Mode.DUEL, request.settings, red, blue);
    }

    public boolean deny(Player receiver, String senderName) {
        Request request = findRequest(receiver, senderName, true);
        if (request == null) {
            receiver.sendMessage(plugin.message("no-request"));
            return false;
        }

        Player sender = Bukkit.getPlayer(request.sender);
        receiver.sendMessage(plugin.message("request-denied"));
        if (sender != null) {
            sender.sendMessage(plugin.color("&c" + receiver.getName() + " denied your duel request."));
        }
        return true;
    }

    private Request findRequest(Player receiver, String senderName, boolean remove) {
        Deque<Request> pending = requests.get(receiver.getUniqueId());
        if (pending == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        Request result = null;

        for (Request request : new ArrayList<Request>(pending)) {
            if (request.expiresAt < now) {
                pending.remove(request);
                continue;
            }

            Player sender = Bukkit.getPlayer(request.sender);
            if (senderName == null || sender != null && sender.getName().equalsIgnoreCase(senderName)) {
                result = request;
            }
        }

        if (result != null && remove) {
            pending.remove(result);
        }
        return result;
    }

    private boolean startPartyMatch(Player sender, Player receiver, MatchSettings settings) {
        PartyManager.Party first = plugin.getPartyManager().get(sender);
        PartyManager.Party second = plugin.getPartyManager().get(receiver);

        if (first == null || second == null || first == second) {
            sender.sendMessage(plugin.color("&cBoth players must lead separate parties."));
            receiver.sendMessage(plugin.color("&cBoth players must lead separate parties."));
            return false;
        }

        if (!plugin.getPartyManager().isLeader(sender) || !plugin.getPartyManager().isLeader(receiver)) {
            sender.sendMessage(plugin.color("&cOnly party leaders can begin a team duel."));
            return false;
        }

        Collection<Player> red = plugin.getPartyManager().onlineMembers(first);
        Collection<Player> blue = plugin.getPartyManager().onlineMembers(second);

        if (red.size() != blue.size()) {
            sender.sendMessage(plugin.color("&cBoth parties must have the same number of online members."));
            return false;
        }

        return startMatch(Match.Type.TEAM, Arena.Mode.TEAM, settings, red, blue);
    }

    public boolean startMatch(Match.Type type, Arena.Mode arenaMode, MatchSettings settings,
                              Collection<Player> redPlayers, Collection<Player> bluePlayers) {
        Arena arena = plugin.getArenaManager().acquire(arenaMode, settings.getArenaName());

        if (arena == null) {
            for (Player player : redPlayers) {
                player.sendMessage(plugin.message("no-arena"));
            }

            for (Player player : bluePlayers) {
                player.sendMessage(plugin.message("no-arena"));
            }

            return false;
        }

        if ((arenaMode == Arena.Mode.DUEL || arenaMode == Arena.Mode.TEAM) &&
                (arena.getRedSpawns().size() < redPlayers.size() ||
                        arena.getBlueSpawns().size() < bluePlayers.size())) {
            plugin.getArenaManager().release(arena);

            for (Player player : redPlayers) {
                player.sendMessage(plugin.color("&cThat arena does not have enough red and blue spawns for this match."));
            }

            for (Player player : bluePlayers) {
                player.sendMessage(plugin.color("&cThat arena does not have enough red and blue spawns for this match."));
            }

            return false;
        }

        for (Player player : redPlayers) {
            if (isBusy(player)) {
                plugin.getArenaManager().release(arena);
                return false;
            }
        }

        for (Player player : bluePlayers) {
            if (isBusy(player)) {
                plugin.getArenaManager().release(arena);
                return false;
            }
        }

        Match match = new Match(type, arena, settings, redPlayers, bluePlayers);

        try {
            for (UUID uuid : match.getParticipants()) {
                Player player = Bukkit.getPlayer(uuid);

                if (player == null) {
                    throw new IllegalStateException("A participant disconnected during match startup");
                }

                plugin.getRecoveryManager().save(player, match.getSnapshot(uuid));
                matchesByPlayer.put(uuid, match);
            }

            plugin.getArenaProtectionManager().begin(match);
            seatParticipantPets(match);
            prepareMatch(match);
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().severe("Could not start match " + match.getId() + ": " + throwable.getMessage());

            try {
                plugin.getArenaProtectionManager().rollback(match);
            } catch (Throwable ignored) {
            }

            for (UUID uuid : match.getParticipants()) {
                matchesByPlayer.remove(uuid);
                Player player = Bukkit.getPlayer(uuid);
                InventorySnapshot snapshot = match.getSnapshot(uuid);

                if (player != null && snapshot != null) {
                    try {
                        snapshot.restore(player);
                        CombatUtil.restoreAttackSpeed(player);
                        plugin.getRecoveryManager().remove(uuid);
                        player.sendMessage(plugin.color("&cThe match could not start. Your inventory and state were restored."));
                    } catch (Throwable restoreFailure) {
                        player.sendMessage(plugin.color("&cThe match failed. Your inventory is saved and will be recovered when you rejoin."));
                    }
                }
            }

            plugin.getArenaManager().release(arena);
            return false;
        }
    }

    private void prepareMatch(final Match match) {
        List<Location> redSpawns = match.getArena().getRedSpawns();
        List<Location> blueSpawns = match.getArena().getBlueSpawns();
        int redIndex = 0;
        int blueIndex = 0;

        for (UUID uuid : match.getRed()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            preparePlayer(player, match, true);
            player.teleport(redSpawns.get(redIndex++ % redSpawns.size()));
        }

        for (UUID uuid : match.getBlue()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            preparePlayer(player, match, false);
            player.teleport(blueSpawns.get(blueIndex++ % blueSpawns.size()));
        }

        applyMatchTeams(match);
        beginCountdown(match);
    }

    private void beginCountdown(final Match match) {
        for (UUID uuid : match.getParticipants()) {
            frozen.add(uuid);
        }

        final int graceSeconds = Math.max(0, plugin.getConfig().getInt("match.load-grace-seconds", 8));
        final int countdownSeconds = Math.max(1, plugin.getConfig().getInt("match.countdown-seconds", 3));

        broadcastToMatch(match, plugin.color("&7Waiting for everyone to finish loading in..."));

        new org.bukkit.scheduler.BukkitRunnable() {
            private int waited;
            private int remaining = countdownSeconds;
            private boolean counting;

            public void run() {
                if (match.isEnded() || !allParticipantsOnline(match)) {
                    releaseFreeze(match);
                    cancel();
                    return;
                }

                if (!counting) {
                    boolean loaded = allParticipantsLoaded(match);

                    if (!loaded && waited < graceSeconds) {
                        waited++;
                        return;
                    }

                    counting = true;
                    broadcastToMatch(match, loaded
                            ? plugin.color("&aEveryone is loaded in.")
                            : plugin.color("&eStarting anyway after " + graceSeconds + " seconds."));
                    return;
                }

                if (remaining > 0) {
                    broadcastToMatch(match, plugin.color("&e&l" + remaining + "..."));

                    for (UUID uuid : match.getParticipants()) {
                        SoundUtil.countdownTick(Bukkit.getPlayer(uuid));
                    }

                    remaining--;
                    return;
                }

                releaseFreeze(match);
                broadcastToMatch(match, plugin.color("&a&lFIGHT!"));

                for (UUID uuid : match.getParticipants()) {
                    SoundUtil.countdownGo(Bukkit.getPlayer(uuid));
                }

                cancel();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private boolean allParticipantsOnline(Match match) {
        for (UUID uuid : match.getParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                return false;
            }
        }
        return true;
    }

    private boolean allParticipantsLoaded(Match match) {
        for (UUID uuid : match.getParticipants()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || player.isDead()) {
                return false;
            }

            Location location = player.getLocation();

            if (location.getWorld() == null) {
                return false;
            }

            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                return false;
            }
        }
        return true;
    }

    private void releaseFreeze(Match match) {
        for (UUID uuid : match.getParticipants()) {
            frozen.remove(uuid);
        }
    }

    public boolean isFrozen(Player player) {
        return frozen.contains(player.getUniqueId());
    }

    private void broadcastToMatch(Match match, String message) {
        for (UUID uuid : match.getParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }

        for (UUID uuid : match.getSpectators()) {
            Player spectator = Bukkit.getPlayer(uuid);
            if (spectator != null) {
                spectator.sendMessage(message);
            }
        }
    }

        private void preparePlayer(Player player, Match match, boolean red) {
        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setFireTicks(0);
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0.0F);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION,
                Integer.MAX_VALUE,
                0,
                true,
                false
        ), true);

        if (match.getSettings().isLegacyCombat()) {
            CombatUtil.enableLegacyAttackSpeed(player);
        }

        ItemUtil.giveKit(player, match.getSettings(), red);
    }

    
    private void applyMatchTeams(Match match) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String suffix = match.getId().toString().replace("-", "").substring(0, 6);
        String redName = "spR" + suffix;
        String blueName = "spB" + suffix;

        Team red = board.getTeam(redName);
        if (red == null) {
            red = board.registerNewTeam(redName);
        }

        Team blue = board.getTeam(blueName);
        if (blue == null) {
            blue = board.registerNewTeam(blueName);
        }

        red.setPrefix(ChatColor.RED.toString());
        blue.setPrefix(ChatColor.BLUE.toString());
        red.setAllowFriendlyFire(false);
        blue.setAllowFriendlyFire(false);
        red.setCanSeeFriendlyInvisibles(true);
        blue.setCanSeeFriendlyInvisibles(true);

        for (UUID uuid : match.getRed()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) {
                red.addEntry(member.getName());
            }
        }

        for (UUID uuid : match.getBlue()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) {
                blue.addEntry(member.getName());
            }
        }

        matchTeams.put(match.getId(), new String[]{redName, blueName});
    }

    private void clearMatchTeams(Match match) {
        String[] names = matchTeams.remove(match.getId());

        if (names == null) {
            return;
        }

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();

        for (String name : names) {
            Team team = board.getTeam(name);

            if (team == null) {
                continue;
            }

            for (String entry : new java.util.HashSet<String>(team.getEntries())) {
                team.removeEntry(entry);
            }

            try {
                team.unregister();
            } catch (Throwable ignored) {
            }
        }
    }

    private void announceVictory(Match match, Set<UUID> winners) {
        if (winners.isEmpty()) {
            return;
        }

        List<String> templates = plugin.getConfig().getStringList("match.win-messages");

        if (templates.isEmpty()) {
            templates = new ArrayList<String>();
            templates.add("&d{winners} &fdefeated &d{losers}&f!");
        }

        String template = templates.get((int) (Math.random() * templates.size()));

        Set<UUID> losers = new java.util.LinkedHashSet<UUID>(match.getParticipants());
        losers.removeAll(winners);

        String message = plugin.color(template
                .replace("{winners}", teamNames(match, winners))
                .replace("{losers}", teamNames(match, losers)));

        if (plugin.getConfig().getBoolean("match.broadcast-results", true)) {
            Bukkit.broadcastMessage(plugin.color("&8[&dStarryPvP&8] &r") + message);
        } else {
            broadcastToMatch(match, message);
        }
    }

    private String teamNames(Match match, Collection<UUID> players) {
        StringBuilder builder = new StringBuilder();

        for (UUID uuid : players) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();

            if (name == null) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(ChatColor.WHITE).append(", ");
            }

            builder.append(match.getRed().contains(uuid) ? ChatColor.RED : ChatColor.BLUE).append(name);
        }

        return builder.length() == 0 ? "Nobody" : builder.toString();
    }

    private String seriesKey(Match match) {
        return seriesKey(match.getRed(), match.getBlue());
    }

    private String seriesKey(Collection<UUID> red, Collection<UUID> blue) {
        List<String> names = new ArrayList<String>();

        for (UUID uuid : red) {
            names.add(uuid.toString());
        }

        for (UUID uuid : blue) {
            names.add(uuid.toString());
        }

        Collections.sort(names);
        StringBuilder builder = new StringBuilder();

        for (String name : names) {
            builder.append(name);
        }

        return builder.toString();
    }

    private void scheduleNextRound(final Match match) {
        final List<UUID> red = new ArrayList<UUID>(match.getRed());
        final List<UUID> blue = new ArrayList<UUID>(match.getBlue());
        final MatchSettings settings = match.getSettings().clone();
        final Match.Type type = match.getType();
        final Arena.Mode mode = match.getArena().getMode();
        long delay = plugin.getConfig().getLong("match.restore-delay-ticks", 40L) + 60L;

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                List<Player> redPlayers = new ArrayList<Player>();
                List<Player> bluePlayers = new ArrayList<Player>();

                for (UUID uuid : red) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        redPlayers.add(player);
                    }
                }

                for (UUID uuid : blue) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        bluePlayers.add(player);
                    }
                }

                if (redPlayers.size() != red.size() || bluePlayers.size() != blue.size()) {
                    seriesScores.remove(seriesKey(red, blue));
                    return;
                }

                startMatch(type, mode, settings, redPlayers, bluePlayers);
            }
        }, delay);
    }

    public void markDirectDamage(Player attacker, Player victim) {
        Match match = matchesByPlayer.get(victim.getUniqueId());
        if (match != null && match == matchesByPlayer.get(attacker.getUniqueId())) {
            match.setDirectDamage(true);
        }
    }

    public boolean canDamage(Player attacker, Player victim) {
        if (frozen.contains(attacker.getUniqueId()) || frozen.contains(victim.getUniqueId())) {
            return false;
        }

        Match first = matchesByPlayer.get(attacker.getUniqueId());
        Match second = matchesByPlayer.get(victim.getUniqueId());
        if (first == null || first != second) {
            return false;
        }
        return !first.sameTeam(attacker.getUniqueId(), victim.getUniqueId());
    }

    public void eliminate(Player player) {
        Match match = matchesByPlayer.get(player.getUniqueId());
        if (match == null || match.isEnded()) {
            return;
        }

        match.eliminate(player.getUniqueId());

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                if (player.isOnline()) {
                    if (player.isDead()) {
                        player.spigot().respawn();
                    }
                    player.setGameMode(GameMode.SPECTATOR);
                    Location location = match.getArena().getSpectatorSpawn();
                    if (location != null) {
                        player.teleport(location);
                    }
                }
            }
        }, 2L);

        evaluateWinner(match);
    }

    private void evaluateWinner(Match match) {
        if (match.getType() == Match.Type.CUSTOM_FFA) {
            if (match.getAlive().size() <= 1) {
                endMatch(match, match.getAlive(), true);
            }
            return;
        }

        if (match.remainingRed().isEmpty()) {
            endMatch(match, match.remainingBlue(), true);
        } else if (match.remainingBlue().isEmpty()) {
            endMatch(match, match.remainingRed(), true);
        }
    }

    public void requestForfeit(Player player) {
        Match match = matchesByPlayer.get(player.getUniqueId());
        if (match == null) {
            player.sendMessage(plugin.color("&cYou are not in an active match."));
            return;
        }

        long now = System.currentTimeMillis();
        Long confirmation = forfeitConfirmations.get(player.getUniqueId());

        if (confirmation == null || confirmation < now) {
            long duration = plugin.getConfig().getLong("match.forfeit-confirmation-seconds", 13L) * 1000L;
            forfeitConfirmations.put(player.getUniqueId(), now + duration);
            player.sendMessage(plugin.message("forfeit-confirm"));
            return;
        }

        forfeitConfirmations.remove(player.getUniqueId());

        if (!match.hasDirectDamage()) {
            endMatch(match, Collections.<UUID>emptySet(), false);
            return;
        }

        match.eliminate(player.getUniqueId());
        evaluateWinner(match);
    }

    public void forceEnd(Player player) {
        Match match = matchesByPlayer.get(player.getUniqueId());
        if (match != null) {
            endMatch(match, Collections.<UUID>emptySet(), false);
        }
    }

    private void endMatch(final Match match, Collection<UUID> winners, boolean updateStats) {
        if (match.isEnded()) {
            return;
        }

        match.setEnded(true);
        releaseFreeze(match);
        clearMatchTeams(match);

        for (UUID spectatorId : new java.util.HashSet<UUID>(match.getSpectators())) {
            Player spectator = Bukkit.getPlayer(spectatorId);

            if (spectator != null) {
                stopSpectating(spectator);
            } else {
                spectatorReturns.remove(spectatorId);
                spectatorSnapshots.remove(spectatorId);
                match.getSpectators().remove(spectatorId);
            }
        }

        restoreParticipantPets(match);
        plugin.getArenaProtectionManager().rollback(match);

        if (!match.hasDirectDamage()) {
            updateStats = false;
            winners = Collections.emptySet();
            for (UUID uuid : match.getParticipants()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.sendMessage(plugin.message("match-voided"));
                }
            }
        }

        Set<UUID> winnerSet = new java.util.HashSet<UUID>(winners);

        if (match.getType() == Match.Type.PRACTICE &&
                !plugin.getConfig().getBoolean("practice.affects-stats", false)) {
            updateStats = false;
        }

        int target = match.getSettings().getBestOf();

        if (updateStats && !winnerSet.isEmpty() && target > 1 &&
                (match.getType() == Match.Type.DUEL || match.getType() == Match.Type.TEAM)) {
            String key = seriesKey(match);
            int[] score = seriesScores.get(key);

            if (score == null) {
                score = new int[2];
                seriesScores.put(key, score);
            }

            boolean redWon = false;

            for (UUID uuid : winnerSet) {
                if (match.getRed().contains(uuid)) {
                    redWon = true;
                    break;
                }
            }

            if (redWon) {
                score[0]++;
            } else {
                score[1]++;
            }

            int needed = target / 2 + 1;

            if (score[0] < needed && score[1] < needed) {
                updateStats = false;
                broadcastToMatch(match, plugin.color("&dSeries &c" + score[0] + " &7- &9" + score[1] +
                        " &7(first to " + needed + ")"));
                scheduleNextRound(match);
            } else {
                seriesScores.remove(key);
                broadcastToMatch(match, plugin.color("&dFinal series score &c" + score[0] + " &7- &9" + score[1]));
            }
        }

        if (updateStats) {
            for (UUID uuid : match.getParticipants()) {
                Player player = Bukkit.getOfflinePlayer(uuid).getPlayer();
                if (winnerSet.contains(uuid)) {
                    plugin.getDataManager().recordWin(Bukkit.getOfflinePlayer(uuid));
                    if (player != null) {
                        player.sendMessage(plugin.color("&aYou won the match!"));
                        SoundUtil.victory(player);
                        launchFirework(player);
                    }
                } else {
                    plugin.getDataManager().recordLoss(Bukkit.getOfflinePlayer(uuid));
                    if (player != null) {
                        player.sendMessage(plugin.color("&cYou lost the match."));
                        SoundUtil.defeat(player);
                    }
                }
            }

            announceVictory(match, winnerSet);
        }

        final List<UUID> participants = new ArrayList<UUID>(match.getParticipants());
        long delay = plugin.getConfig().getLong("match.restore-delay-ticks", 40L);

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                for (UUID uuid : participants) {
                    Player player = Bukkit.getPlayer(uuid);
                    InventorySnapshot snapshot = match.getSnapshot(uuid);
                    matchesByPlayer.remove(uuid);
                    if (player != null && snapshot != null) {
                        if (player.isDead()) {
                            player.spigot().respawn();
                        }
                        snapshot.restore(player);
                        CombatUtil.restoreAttackSpeed(player);
                        plugin.getRecoveryManager().remove(uuid);
                        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                    }
                }
                plugin.getArenaManager().release(match.getArena());
            }
        }, delay);
    }

    private void launchFirework(Player player) {
        if (!plugin.getConfig().getBoolean("match.victory-fireworks", true)) {
            return;
        }

        Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(org.bukkit.Color.FUCHSIA)
                .withFade(org.bukkit.Color.AQUA)
                .with(FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

    public void togglePractice(Player player) {
        practiceQueue.remove(player.getUniqueId());

        if (publicFfa.contains(player.getUniqueId())) {
            leaveFfa(player);
            return;
        }

        joinFfa(player);
    }

    private void processPracticeQueue() {
        while (practiceQueue.size() >= 2) {
            Player first = nextQueuedPlayer();
            Player second = nextQueuedPlayer();

            if (first == null || second == null) {
                return;
            }

            MatchSettings settings = settingsFromConfig("practice");
            if (!startMatch(
                    Match.Type.PRACTICE,
                    Arena.Mode.DUEL,
                    settings,
                    Collections.singletonList(first),
                    Collections.singletonList(second)
            )) {
                practiceQueue.add(first.getUniqueId());
                practiceQueue.add(second.getUniqueId());
                return;
            }
        }
    }

    private Player nextQueuedPlayer() {
        while (!practiceQueue.isEmpty()) {
            UUID uuid = practiceQueue.poll();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !isBusy(player)) {
                return player;
            }
        }
        return null;
    }

    public void joinFfa(Player player) {
        if (publicFfa.contains(player.getUniqueId())) {
            player.sendMessage(plugin.color("&eYou are already in the FFA arena."));
            return;
        }

        if (isBusy(player)) {
            player.sendMessage(plugin.message("already-in-match"));
            return;
        }

        if (publicFfaArena == null) {
            publicFfaArena = plugin.getArenaManager().acquire(
                    Arena.Mode.FFA,
                    plugin.getConfig().getString("practice.arena", "")
            );
        }

        if (publicFfaArena == null || publicFfaArena.getFfaSpawns().isEmpty()) {
            player.sendMessage(plugin.message("no-arena"));
            return;
        }

        ffaSnapshots.put(player.getUniqueId(), new InventorySnapshot(player));
        publicFfa.add(player.getUniqueId());
        respawnFfa(player);
        SoundUtil.queueJoin(player);
        player.sendMessage(plugin.color("&aYou joined Practice &7(free for all)&a."));
        player.sendMessage(plugin.color("&7Wins and losses here do &fnot &7affect your stats."));
        player.sendMessage(plugin.color("&7You respawn at a random spot. Type &f/pvp leave &7to exit."));
    }

    public void leaveFfa(Player player) {
        if (!publicFfa.remove(player.getUniqueId())) {
            player.sendMessage(plugin.color("&cYou are not in the FFA arena."));
            return;
        }

        InventorySnapshot snapshot = ffaSnapshots.remove(player.getUniqueId());
        if (snapshot != null) {
            snapshot.restore(player);
        }

        if (publicFfa.isEmpty() && publicFfaArena != null) {
            plugin.getArenaManager().release(publicFfaArena);
            publicFfaArena = null;
        }

        SoundUtil.queueLeave(player);
        player.sendMessage(plugin.color("&aYou left Practice. No stats were changed."));
    }

    public void respawnFfa(Player player) {
        if (!publicFfa.contains(player.getUniqueId()) || publicFfaArena == null) {
            return;
        }

        List<Location> spawns = publicFfaArena.getFfaSpawns();
        Location spawn = spawns.get((int) (Math.random() * spawns.size()));
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.teleport(spawn);
        ItemUtil.giveKit(player, settingsFromConfig("ffa"), Math.random() >= 0.5D);
    }

    private MatchSettings settingsFromConfig(String section) {
        MatchSettings settings = new MatchSettings();

        try {
            settings.setArmorTier(MatchSettings.ArmorTier.valueOf(
                    plugin.getConfig().getString(section + ".armor", "IRON").toUpperCase()
            ));
        } catch (IllegalArgumentException ignored) {
        }

        try {
            settings.setWeaponMode(MatchSettings.WeaponMode.valueOf(
                    plugin.getConfig().getString(section + ".weapons", "BOTH").toUpperCase()
            ));
        } catch (IllegalArgumentException ignored) {
        }

        try {
            settings.setHealingMode(MatchSettings.HealingMode.valueOf(
                    plugin.getConfig().getString(section + ".healing", "GAPPLE").toUpperCase()
            ));
        } catch (IllegalArgumentException ignored) {
        }

        settings.setBuilding(plugin.getConfig().getBoolean(section + ".building", true));
        return settings;
    }

            public void spectate(Player spectator, Player target) {
        Match match = matchesByPlayer.get(target.getUniqueId());

        if (match == null || match.isEnded()) {
            spectator.sendMessage(plugin.color("&cThat player is not in an active match."));
            return;
        }

        if (matchesByPlayer.containsKey(spectator.getUniqueId()) ||
                publicFfa.contains(spectator.getUniqueId())) {
            spectator.sendMessage(plugin.message("already-in-match"));
            return;
        }

        if (spectator.equals(target)) {
            spectator.sendMessage(plugin.color("&cYou cannot spectate yourself."));
            return;
        }

        if (isSpectating(spectator)) {
            stopSpectating(spectator);
        }

        InventorySnapshot snapshot = new InventorySnapshot(spectator);
        Location returnLocation = spectator.getLocation().clone();

        try {
            spectator.closeInventory();
            spectator.setGameMode(GameMode.SPECTATOR);
            spectator.setSpectatorTarget(null);
            spectator.setAllowFlight(true);
            spectator.setFlying(true);
            spectator.setFireTicks(0);

            spectator.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION,
                    Integer.MAX_VALUE,
                    0,
                    true,
                    false
            ), true);

            spectator.teleport(target.getLocation());
            spectator.setSpectatorTarget(target);

            spectatorReturns.put(spectator.getUniqueId(), returnLocation);
            spectatorSnapshots.put(spectator.getUniqueId(), snapshot);
            match.getSpectators().add(spectator.getUniqueId());

            spectator.sendMessage(plugin.color("&aYou are spectating &f" + target.getName() + "&a."));
            spectator.sendMessage(plugin.color("&7Use &f/pvp spectate leave &7to exit safely."));
        } catch (Throwable throwable) {
            spectatorReturns.remove(spectator.getUniqueId());
            spectatorSnapshots.remove(spectator.getUniqueId());
            match.getSpectators().remove(spectator.getUniqueId());

            try {
                snapshot.restore(spectator);
            } catch (Throwable ignored) {
                emergencySpectatorReset(spectator);
            }

            spectator.sendMessage(plugin.color("&cSpectator mode could not be started. Your previous state was restored."));
            plugin.getLogger().severe("Could not start spectator mode for " +
                    spectator.getName() + ": " + throwable.getMessage());
        }
    }

        public void stopSpectating(Player player) {
        UUID uuid = player.getUniqueId();
        InventorySnapshot snapshot = spectatorSnapshots.remove(uuid);
        spectatorReturns.remove(uuid);

        for (Match match : new java.util.HashSet<Match>(matchesByPlayer.values())) {
            match.getSpectators().remove(uuid);
        }

        try {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(null);
            }
        } catch (Throwable ignored) {
        }

        if (snapshot != null) {
            try {
                snapshot.restore(player);
            } catch (Throwable throwable) {
                emergencySpectatorReset(player);
            }
        } else {
            emergencySpectatorReset(player);
        }

        player.sendMessage(plugin.color("&aYou are no longer spectating."));
    }

       public void forceSpectatorCleanup(Player player) {
        UUID uuid = player.getUniqueId();
        boolean tracked = spectatorReturns.containsKey(uuid) ||
                spectatorSnapshots.containsKey(uuid);

        if (tracked) {
            stopSpectating(player);
            return;
        }

        for (Match match : new java.util.HashSet<Match>(matchesByPlayer.values())) {
            match.getSpectators().remove(uuid);
        }
    }

    public void forceUnstuck(Player player) {
        UUID uuid = player.getUniqueId();
        spectatorReturns.remove(uuid);
        InventorySnapshot snapshot = spectatorSnapshots.remove(uuid);

        for (Match match : new java.util.HashSet<Match>(matchesByPlayer.values())) {
            match.getSpectators().remove(uuid);
        }

        try {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(null);
            }
        } catch (Throwable ignored) {
        }

        if (snapshot != null) {
            try {
                snapshot.restore(player);
                player.sendMessage(plugin.color("&aYour pre-spectator state was restored."));
                return;
            } catch (Throwable ignored) {
            }
        }

        emergencySpectatorReset(player);
        player.sendMessage(plugin.color("&aStarryPvP spectator restrictions were cleared."));
    }

    private void emergencySpectatorReset(Player player) {
        try {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(null);
            }
        } catch (Throwable ignored) {
        }

        if (player.getGameMode() == GameMode.SPECTATOR ||
                player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }

        player.setFireTicks(0);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.updateInventory();
    }

    public boolean isSpectating(Player player) {
        return spectatorReturns.containsKey(player.getUniqueId()) ||
                spectatorSnapshots.containsKey(player.getUniqueId());
    }

    public Map<UUID, Match> activeMatches() {
        Map<UUID, Match> unique = new LinkedHashMap<UUID, Match>();
        for (Match match : matchesByPlayer.values()) {
            unique.put(match.getId(), match);
        }
        return unique;
    }

    public Match getMatch(Player player) {
        return matchesByPlayer.get(player.getUniqueId());
    }

    public boolean isPublicFfa(Player player) {
        return publicFfa.contains(player.getUniqueId());
    }

    public boolean isBusy(Player player) {
        return matchesByPlayer.containsKey(player.getUniqueId()) || publicFfa.contains(player.getUniqueId());
    }

    public boolean isDuelRequestsEnabled() {
        return duelRequestsEnabled;
    }

    public void setDuelRequestsEnabled(boolean duelRequestsEnabled) {
        this.duelRequestsEnabled = duelRequestsEnabled;
    }

    private void seatParticipantPets(Match match) {
        Map<UUID, Boolean> states = new HashMap<UUID, Boolean>();

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Tameable)) {
                    continue;
                }

                Tameable tameable = (Tameable) entity;

                if (!tameable.isTamed() || tameable.getOwner() == null) {
                    continue;
                }

                if (!match.isParticipant(tameable.getOwner().getUniqueId())) {
                    continue;
                }

                if (entity instanceof Wolf) {
                    Wolf wolf = (Wolf) entity;
                    states.put(wolf.getUniqueId(), wolf.isSitting());
                    wolf.setSitting(true);
                } else if (entity instanceof Ocelot) {
                    Ocelot ocelot = (Ocelot) entity;
                    states.put(ocelot.getUniqueId(), ocelot.isSitting());
                    ocelot.setSitting(true);
                }
            }
        }

        petSittingStates.put(match.getId(), states);
    }

    private void restoreParticipantPets(Match match) {
        Map<UUID, Boolean> states = petSittingStates.remove(match.getId());

        if (states == null || states.isEmpty()) {
            return;
        }

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                Boolean sitting = states.get(entity.getUniqueId());

                if (sitting == null) {
                    continue;
                }

                if (entity instanceof Wolf) {
                    ((Wolf) entity).setSitting(sitting.booleanValue());
                } else if (entity instanceof Ocelot) {
                    ((Ocelot) entity).setSitting(sitting.booleanValue());
                }
            }
        }
    }
    
    public void shutdown() {
        for (UUID uuid : new java.util.HashSet<UUID>(spectatorSnapshots.keySet())) {
            Player spectator = Bukkit.getPlayer(uuid);

            if (spectator != null) {
                stopSpectating(spectator);
            }
        }

        for (Match match : new java.util.HashSet<Match>(matchesByPlayer.values())) {
            endMatch(match, Collections.<UUID>emptySet(), false);
        }

        for (UUID uuid : new ArrayList<UUID>(publicFfa)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                leaveFfa(player);
            }
        }

        for (Entity entity : new ArrayList<Entity>(Bukkit.getWorlds().get(0).getEntities())) {
            if (entity instanceof Firework && !entity.isDead()) {
                entity.remove();
            }
        }
    }
}
