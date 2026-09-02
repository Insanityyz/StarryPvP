package com.starrypvp.queue;

import com.starrypvp.StarryPvP;
import com.starrypvp.gui.SetupGui;
import com.starrypvp.party.PartyManager;
import com.starrypvp.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QueueManager {
    public enum Mode {
        DUEL("Duels", 1),
        TEAM2("2v2", 2),
        TEAM3("3v3", 3),
        TEAM5("5v5", 5),
        PRACTICE("Practice", 1);

        private final String label;
        private final int perSide;

        Mode(String label, int perSide) {
            this.label = label;
            this.perSide = perSide;
        }

        public String getLabel() {
            return label;
        }

        public int getPerSide() {
            return perSide;
        }

        public boolean isTeam() {
            return this == TEAM2 || this == TEAM3 || this == TEAM5;
        }
    }

    private final StarryPvP plugin;
    private final Map<UUID, Mode> queued = new ConcurrentHashMap<UUID, Mode>();
    private final Map<Mode, List<UUID>> rooms = new LinkedHashMap<Mode, List<UUID>>();
    private final Map<UUID, Integer> bestOf = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, String> preferredArena = new ConcurrentHashMap<UUID, String>();

    public QueueManager(StarryPvP plugin) {
        this.plugin = plugin;

        for (Mode mode : Mode.values()) {
            rooms.put(mode, new ArrayList<UUID>());
        }
    }

    public boolean isQueued(Player player) {
        return queued.containsKey(player.getUniqueId());
    }

    public Mode getMode(Player player) {
        return queued.get(player.getUniqueId());
    }

    public int getBestOf(Player player) {
        Integer value = bestOf.get(player.getUniqueId());
        return value == null ? 1 : value.intValue();
    }

    public void setBestOf(Player player, int value) {
        if (value != 1 && value != 3 && value != 5) {
            return;
        }
        bestOf.put(player.getUniqueId(), Integer.valueOf(value));
    }

    public String getPreferredArena(Player player) {
        return preferredArena.get(player.getUniqueId());
    }

    public void setPreferredArena(Player player, String arena) {
        if (arena == null) {
            preferredArena.remove(player.getUniqueId());
        } else {
            preferredArena.put(player.getUniqueId(), arena);
        }
    }

    public int waiting(Mode mode) {
        return rooms.get(mode).size();
    }

    public boolean isBusy(Player player) {
        return plugin.getMatchManager().getMatch(player) != null
                || plugin.getMatchManager().isSpectating(player)
                || plugin.getMatchManager().isPublicFfa(player);
    }

    public void join(Player player, Mode mode) {
        if (isBusy(player)) {
            player.sendMessage(plugin.color("&cFinish what you are doing first."));
            SoundUtil.deny(player);
            return;
        }

        if (isQueued(player)) {
            cancel(player, true);
        }

        if (mode == Mode.PRACTICE) {
            queued.put(player.getUniqueId(), mode);
            SoundUtil.queueJoin(player);
            player.sendMessage(plugin.color("&dPractice&f: searching for an opponent."));
            player.sendMessage(plugin.color("&7You can move around while waiting. &f/pvp cancel &7to stop."));
            plugin.getMatchManager().togglePractice(player);
            return;
        }

        if (mode.isTeam() && !hasFullParty(player, mode.getPerSide())) {
            player.sendMessage(plugin.color("&cYou need a party of exactly " + mode.getPerSide()
                    + " online members for " + mode.getLabel() + "."));
            player.sendMessage(plugin.color("&7Use &f/pvp party create &7and &f/pvp party invite <player>&7."));
            SoundUtil.deny(player);
            return;
        }

        List<UUID> room = rooms.get(mode);
        room.add(player.getUniqueId());
        queued.put(player.getUniqueId(), mode);

        SoundUtil.queueJoin(player);
        player.sendMessage(plugin.color("&dQueued&f: " + mode.getLabel()
                + " &7(" + room.size() + " waiting)"));
        player.sendMessage(plugin.color("&7You can move around while waiting. &f/pvp cancel &7to stop."));

        tryMatch(mode);
    }

    private boolean hasFullParty(Player player, int size) {
        PartyManager.Party party = plugin.getPartyManager().get(player);

        if (party == null || !plugin.getPartyManager().isLeader(player)) {
            return false;
        }

        return plugin.getPartyManager().onlineMembers(party).size() == size;
    }

    private void tryMatch(Mode mode) {
        List<UUID> room = rooms.get(mode);

        while (room.size() >= 2) {
            Player first = Bukkit.getPlayer(room.get(0));
            Player second = Bukkit.getPlayer(room.get(1));

            if (first == null || !first.isOnline()) {
                room.remove(0);
                continue;
            }

            if (second == null || !second.isOnline()) {
                room.remove(1);
                continue;
            }

            room.remove(0);
            room.remove(0);
            queued.remove(first.getUniqueId());
            queued.remove(second.getUniqueId());

            pair(first, second, mode);
        }
    }

    private void pair(Player first, Player second, Mode mode) {
        SoundUtil.matchFound(first);
        SoundUtil.matchFound(second);

        first.sendMessage(plugin.color("&aOpponent found&f: &d" + second.getName()));
        second.sendMessage(plugin.color("&aOpponent found&f: &d" + first.getName()));

        SetupGui.ChallengeType type = mode.isTeam()
                ? SetupGui.ChallengeType.TEAM
                : SetupGui.ChallengeType.DUEL;

        second.sendMessage(plugin.color("&7" + first.getName()
                + " is choosing the loadout. Use &f/pvp accept " + first.getName() + " &7when it arrives."));

        plugin.getSetupGui().open(first, second, type);
    }

    public boolean cancel(Player player, boolean silent) {
        Mode mode = queued.remove(player.getUniqueId());

        if (mode == null) {
            if (!silent) {
                player.sendMessage(plugin.color("&cYou are not in a queue."));
                SoundUtil.deny(player);
            }
            return false;
        }

        rooms.get(mode).remove(player.getUniqueId());

        if (mode == Mode.PRACTICE) {
            plugin.getMatchManager().togglePractice(player);
        }

        if (!silent) {
            player.sendMessage(plugin.color("&eYou left the " + mode.getLabel() + " queue."));
            SoundUtil.queueLeave(player);
        }

        return true;
    }

    public void handleQuit(Player player) {
        cancel(player, true);
        bestOf.remove(player.getUniqueId());
        preferredArena.remove(player.getUniqueId());
    }

    public void clear() {
        queued.clear();
        bestOf.clear();
        preferredArena.clear();

        for (Mode mode : Mode.values()) {
            rooms.get(mode).clear();
        }
    }
}
