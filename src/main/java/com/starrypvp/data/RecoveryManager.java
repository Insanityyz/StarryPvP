package com.starrypvp.data;

import com.starrypvp.StarryPvP;
import com.starrypvp.util.CombatUtil;
import com.starrypvp.util.InventorySnapshot;
import org.bukkit.Bukkit;
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

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                restoreIfPresent(event.getPlayer());
            }
        }, 2L);
    }

    private void flush() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save recovery.yml: " + exception.getMessage());
        }
    }
}
