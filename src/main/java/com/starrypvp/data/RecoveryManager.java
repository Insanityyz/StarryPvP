package com.starrypvp.data;

import com.starrypvp.StarryPvP;
import com.starrypvp.util.CombatUtil;
import com.starrypvp.util.InventorySnapshot;
import com.starrypvp.util.KitTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class RecoveryManager implements Listener {
    private final StarryPvP plugin;
    private final File file;
    private final YamlConfiguration data;

    public RecoveryManager(StarryPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "recovery.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void save(Player player, InventorySnapshot snapshot) {
        snapshot.write(data, "players." + player.getUniqueId());
        flush();
    }

    public void remove(UUID uuid) {
        data.set("players." + uuid, null);
        flush();
    }

    public void restoreIfPresent(Player player) {
        String path = "players." + player.getUniqueId();
        if (!data.contains(path)) {
            return;
        }

        try {
            InventorySnapshot snapshot = InventorySnapshot.read(data, path);
            snapshot.restore(player);
            CombatUtil.restoreAttackSpeed(player);
            data.set(path, null);
            flush();
            player.sendMessage(plugin.color("&aYour inventory and player state were recovered from an interrupted PvP match."));
        } catch (Throwable throwable) {
            plugin.getLogger().severe("Could not recover " + player.getName() + ": " + throwable.getMessage());
        }
    }

    public void restoreOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreIfPresent(player);
        }
    }

    /**
     * Stores a "you still owe this player a teleport out of the arena" note.
     * Used when someone disconnects mid match: the inventory is restored in
     * the quit event, but a teleport during a logout is not reliable, so the
     * position is re-applied (and verified) on the next login instead.
     */
    public void savePendingExit(UUID uuid, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        String path = "pending-exit." + uuid;
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
        flush();
    }

    public void clearPendingExit(UUID uuid) {
        if (data.contains("pending-exit." + uuid)) {
            data.set("pending-exit." + uuid, null);
            flush();
        }
    }

    private void applyPendingExit(final Player player, final int attempt) {
        final String path = "pending-exit." + player.getUniqueId();

        if (!data.contains(path)) {
            return;
        }

        World world = Bukkit.getWorld(data.getString(path + ".world", ""));

        if (world == null) {
            clearPendingExit(player.getUniqueId());
            return;
        }

        Location destination = new Location(
                world,
                data.getDouble(path + ".x"),
                data.getDouble(path + ".y"),
                data.getDouble(path + ".z"),
                (float) data.getDouble(path + ".yaw"),
                (float) data.getDouble(path + ".pitch")
        );

        try {
            world.loadChunk(destination.getBlockX() >> 4, destination.getBlockZ() >> 4);
            player.teleport(destination);
        } catch (Throwable ignored) {
        }

        Location now = player.getLocation();
        boolean arrived = now.getWorld() != null
                && now.getWorld().equals(world)
                && now.distanceSquared(destination) < 9.0D;

        if (arrived) {
            clearPendingExit(player.getUniqueId());
            player.sendMessage(plugin.color("&aYou were returned to where you were before your last match."));
            return;
        }

        if (attempt >= 6) {
            clearPendingExit(player.getUniqueId());
            plugin.getLogger().warning("Gave up returning " + player.getName() + " to their pre-match location.");
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                if (player.isOnline()) {
                    applyPendingExit(player, attempt + 1);
                }
            }
        }, 20L);
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        // 2 ticks was far too early: the client has not finished spawning, so
        // the teleport in restore() was being dropped and players woke up
        // inside the arena they had quit from.
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                Player player = event.getPlayer();

                if (!player.isOnline()) {
                    return;
                }

                if (plugin.getConfig().getBoolean("security.purge-kit-items-on-join", true)) {
                    KitTag.purge(player);
                }

                restoreIfPresent(player);
                applyPendingExit(player, 0);
            }
        }, 20L);
    }

    private void flush() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save recovery.yml: " + exception.getMessage());
        }
    }
}
