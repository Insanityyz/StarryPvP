package com.starrypvp.event;

import com.starrypvp.StarryPvP;
import com.starrypvp.arena.Arena;
import com.starrypvp.match.Match;
import com.starrypvp.match.MatchSettings;
import com.starrypvp.util.EventColor;
import com.starrypvp.util.SoundUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class EventFfaManager {
    public static final int HARD_PLAYER_CAP = 10;

    private final StarryPvP plugin;
    private final Set<UUID> accepted = new LinkedHashSet<UUID>();

    private BukkitTask loop;
    private BukkitTask signup;
    private boolean open;
    private boolean running;
    private UUID activeMatch;
    private int startingRoster;

    public EventFfaManager(StarryPvP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        if (!plugin.getConfig().getBoolean("event-ffa.enabled", true)) {
            return;
        }

        long minutes = Math.max(1L, plugin.getConfig().getLong("event-ffa.interval-minutes", 20L));
        long ticks = minutes * 60L * 20L;

        loop = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            public void run() {
                announce(false);
            }
        }, ticks, ticks);
    }

    public void stop() {
        if (loop != null) {
            loop.cancel();
            loop = null;
        }

        if (signup != null) {
            signup.cancel();
            signup = null;
        }

        open = false;
        accepted.clear();
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isRunning() {
        return running;
    }

    public int maxPlayers() {
        int configured = plugin.getConfig().getInt("event-ffa.max-players", HARD_PLAYER_CAP);
        return Math.max(2, Math.min(HARD_PLAYER_CAP, configured));
    }

    public int minimumPlayers() {
        int configured = plugin.getConfig().getInt("event-ffa.minimum-players", 3);
        return Math.max(2, Math.min(maxPlayers(), configured));
    }

    private Arena availableArena() {
        String preferred = plugin.getConfig().getString("event-ffa.arena", "");

        if (preferred != null && !preferred.trim().isEmpty()) {
            Arena arena = plugin.getArenaManager().get(preferred);

            if (arena != null && arena.getMode() == Arena.Mode.FFA
                    && arena.isReady() && !arena.isOccupied()) {
                return arena;
            }
        }

        for (Arena arena : plugin.getArenaManager().all()) {
            if (arena.getMode() == Arena.Mode.FFA && arena.isReady() && !arena.isOccupied()) {
                return arena;
            }
        }

        return null;
    }

    public void announce(boolean forced) {
        if (open || running) {
            return;
        }

        if (!forced && !plugin.getConfig().getBoolean("event-ffa.enabled", true)) {
            return;
        }

        if (Bukkit.getOnlinePlayers().size() < minimumPlayers()) {
            return;
        }

        if (availableArena() == null) {
            plugin.getLogger().warning("Skipped the FFA event: no free FFA arena is configured.");
            return;
        }

        open = true;
        accepted.clear();

        final int seconds = Math.max(10, plugin.getConfig().getInt("event-ffa.signup-seconds", 60));
        int reward = plugin.getConfig().getInt("event-ffa.reward.amount", 250);

        Bukkit.broadcastMessage(plugin.color("&8&m--------------------------------"));
        Bukkit.broadcastMessage(plugin.color("&d&lFFA EVENT &fis starting!"));
        Bukkit.broadcastMessage(plugin.color("&fLast player standing wins &a$" + reward + "&f."));
        Bukkit.broadcastMessage(plugin.color("&7Up to &f" + maxPlayers()
                + " &7players. Sign ups close in &f" + seconds + "s&7."));

        TextComponent join = new TextComponent(">>> [CLICK TO JOIN THE FFA EVENT] <<<");
        join.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        join.setBold(true);
        join.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp event join"));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(join);
            SoundUtil.matchFound(player);
        }

        Bukkit.broadcastMessage(plugin.color("&8&m--------------------------------"));

        signup = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                close();
            }
        }, seconds * 20L);
    }

    public void join(Player player) {
        if (!open) {
            player.sendMessage(plugin.color("&cThere is no FFA event taking sign ups right now."));
            SoundUtil.deny(player);
            return;
        }

        if (accepted.contains(player.getUniqueId())) {
            player.sendMessage(plugin.color("&eYou are already signed up."));
            return;
        }

        if (accepted.size() >= maxPlayers()) {
            player.sendMessage(plugin.color("&cThe event is full (" + maxPlayers() + " players)."));
            SoundUtil.deny(player);
            return;
        }

        if (plugin.getMatchManager().isBusy(player) || plugin.getQueueManager().isQueued(player)) {
            player.sendMessage(plugin.color("&cFinish what you are doing first."));
            SoundUtil.deny(player);
            return;
        }

        accepted.add(player.getUniqueId());
        SoundUtil.queueJoin(player);

        Bukkit.broadcastMessage(plugin.color("&8[&dStarryPvP&8] &d" + player.getName()
                + " &fjoined the FFA event &7(" + accepted.size() + "/" + maxPlayers() + ")"));

        if (accepted.size() >= maxPlayers()) {
            close();
        }
    }

    public void handleQuit(Player player) {
        accepted.remove(player.getUniqueId());
    }

    public void cancel(String reason) {
        if (signup != null) {
            signup.cancel();
            signup = null;
        }

        if (!open) {
            return;
        }

        open = false;
        accepted.clear();
        Bukkit.broadcastMessage(plugin.color("&8[&dStarryPvP&8] &cThe FFA event was cancelled. &7" + reason));
    }

    private void close() {
        if (!open) {
            return;
        }

        open = false;

        if (signup != null) {
            signup.cancel();
            signup = null;
        }

        List<Player> roster = new ArrayList<Player>();

        for (UUID uuid : accepted) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline() || plugin.getMatchManager().isBusy(player)) {
                continue;
            }

            roster.add(player);

            if (roster.size() >= maxPlayers()) {
                break;
            }
        }

        accepted.clear();

        if (roster.size() < minimumPlayers()) {
            Bukkit.broadcastMessage(plugin.color("&8[&dStarryPvP&8] &cThe FFA event was cancelled. &7Not enough players signed up."));
            return;
        }

        running = true;
        startingRoster = roster.size();

        if (!plugin.getMatchManager().startEventFfa(roster, settings())) {
            running = false;
            startingRoster = 0;
            Bukkit.broadcastMessage(plugin.color("&8[&dStarryPvP&8] &cThe FFA event could not start. &7No arena was free."));
            return;
        }

        Match match = plugin.getMatchManager().getMatch(roster.get(0));

        if (match == null) {
            running = false;
            return;
        }

        activeMatch = match.getId();

        StringBuilder line = new StringBuilder();

        for (Player player : roster) {
            EventColor colour = match.getColor(player.getUniqueId());

            if (line.length() > 0) {
                line.append(org.bukkit.ChatColor.WHITE).append(", ");
            }

            line.append(colour == null ? org.bukkit.ChatColor.WHITE : colour.getChat()).append(player.getName());
        }

        Bukkit.broadcastMessage(plugin.color("&8[&dStarryPvP&8] &fThe FFA event has begun: ") + line.toString());
    }

    public void handleFinish(Match match, Set<UUID> winners) {
        if (!running || activeMatch == null || !activeMatch.equals(match.getId())) {
            return;
        }

        running = false;
        activeMatch = null;

        int roster = startingRoster;
        startingRoster = 0;

        if (winners == null || winners.isEmpty()) {
            Bukkit.broadcastMessage(plugin.color("&8[&dStarryPvP&8] &fThe FFA event ended with no winner."));
            return;
        }

        UUID winner = winners.iterator().next();
        OfflinePlayer profile = Bukkit.getOfflinePlayer(winner);
        String name = profile == null ? null : profile.getName();

        if (name == null) {
            return;
        }

        int amount = plugin.getConfig().getInt("event-ffa.reward.amount", 250);
        int requirement = Math.max(2, plugin.getConfig().getInt("event-ffa.reward.minimum-players", minimumPlayers()));

        EventColor colour = match.getColor(winner);
        String display = (colour == null ? "" : colour.getChat().toString()) + name;

        Bukkit.broadcastMessage(plugin.color("&8&m--------------------------------"));
        Bukkit.broadcastMessage(plugin.color("&d&lFFA EVENT WINNER"));
        Bukkit.broadcastMessage(plugin.color("&f") + display
                + plugin.color(" &fwon with &d" + match.getKills(winner) + " &fkills!"));

        if (roster < requirement) {
            Bukkit.broadcastMessage(plugin.color("&7No payout: the event needed at least "
                    + requirement + " players."));
            Bukkit.broadcastMessage(plugin.color("&8&m--------------------------------"));
            return;
        }

        Bukkit.broadcastMessage(plugin.color("&fReward: &a$" + amount));
        Bukkit.broadcastMessage(plugin.color("&8&m--------------------------------"));

        List<String> commands = plugin.getConfig().getStringList("event-ffa.reward.commands");

        if (commands.isEmpty()) {
            commands = new ArrayList<String>();
            commands.add("eco give {player} {amount}");
        }

        for (String template : commands) {
            if (template == null || template.trim().isEmpty()) {
                continue;
            }

            String command = template
                    .replace("{player}", name)
                    .replace("{amount}", String.valueOf(amount));

            if (command.startsWith("/")) {
                command = command.substring(1);
            }

            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Throwable throwable) {
                plugin.getLogger().severe("FFA event reward command failed (" + command + "): "
                        + throwable.getMessage());
            }
        }

        Player online = Bukkit.getPlayer(winner);

        if (online != null) {
            SoundUtil.victory(online);
        }
    }

    public void handleMatchAborted(Match match) {
        if (activeMatch != null && activeMatch.equals(match.getId())) {
            running = false;
            activeMatch = null;
            startingRoster = 0;
        }
    }

    private MatchSettings settings() {
        MatchSettings settings = new MatchSettings();

        try {
            settings.setArmorTier(MatchSettings.ArmorTier.valueOf(
                    plugin.getConfig().getString("event-ffa.kit.armor", "LEATHER").toUpperCase()));
        } catch (IllegalArgumentException ignored) {
        }

        try {
            settings.setWeaponMode(MatchSettings.WeaponMode.valueOf(
                    plugin.getConfig().getString("event-ffa.kit.weapons", "BOTH").toUpperCase()));
        } catch (IllegalArgumentException ignored) {
        }

        try {
            settings.setHealingMode(MatchSettings.HealingMode.valueOf(
                    plugin.getConfig().getString("event-ffa.kit.healing", "GAPPLE").toUpperCase()));
        } catch (IllegalArgumentException ignored) {
        }

        settings.setBuilding(plugin.getConfig().getBoolean("event-ffa.kit.building", false));
        settings.setBow(plugin.getConfig().getBoolean("event-ffa.kit.bow", false));
        settings.setArenaName(plugin.getConfig().getString("event-ffa.arena", ""));
        return settings;
    }

    public List<UUID> signedUp() {
        return Collections.unmodifiableList(new ArrayList<UUID>(accepted));
    }
}
