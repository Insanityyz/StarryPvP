package com.starrypvp.data;

import com.starrypvp.StarryPvP;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class DataManager {
    public static final class Profile {
        private final UUID uuid;
        private final String name;
        private final int wins;
        private final int losses;
        private final int streak;
        private final int highestStreak;

        public Profile(UUID uuid, String name, int wins, int losses, int streak, int highestStreak) {
            this.uuid = uuid;
            this.name = name;
            this.wins = wins;
            this.losses = losses;
            this.streak = streak;
            this.highestStreak = highestStreak;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public int getWins() {
            return wins;
        }

        public int getLosses() {
            return losses;
        }

        public int getStreak() {
            return streak;
        }

        public int getHighestStreak() {
            return highestStreak;
        }

        public double getRatio() {
            return losses == 0 ? wins : (double) wins / (double) losses;
        }
    }

    private final StarryPvP plugin;
    private final File file;
    private final YamlConfiguration data;

    public DataManager(StarryPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void register(OfflinePlayer player) {
        String base = "players." + player.getUniqueId();
        data.set(base + ".name", player.getName());
        if (!data.contains(base + ".wins")) {
            data.set(base + ".wins", 0);
            data.set(base + ".losses", 0);
            data.set(base + ".streak", 0);
            data.set(base + ".highest-streak", 0);
        }
    }

    public Profile get(OfflinePlayer player) {
        register(player);
        return read(player.getUniqueId());
    }

    public void recordWin(OfflinePlayer player) {
        register(player);
        String base = "players." + player.getUniqueId();
        int wins = data.getInt(base + ".wins") + 1;
        int streak = data.getInt(base + ".streak") + 1;
        data.set(base + ".wins", wins);
        data.set(base + ".streak", streak);
        data.set(base + ".highest-streak", Math.max(streak, data.getInt(base + ".highest-streak")));
        save();
    }

    public void recordLoss(OfflinePlayer player) {
        register(player);
        String base = "players." + player.getUniqueId();
        data.set(base + ".losses", data.getInt(base + ".losses") + 1);
        data.set(base + ".streak", 0);
        save();
    }

    public void resetStats(OfflinePlayer player) {
        register(player);
        String base = "players." + player.getUniqueId();
        data.set(base + ".wins", 0);
        data.set(base + ".losses", 0);
        data.set(base + ".streak", 0);
        data.set(base + ".highest-streak", 0);
        save();
    }

    public void resetProfile(OfflinePlayer player) {
        data.set("players." + player.getUniqueId(), null);
        register(player);
        save();
    }

    public List<Profile> leaderboard(final boolean ratio) {
        List<Profile> profiles = new ArrayList<Profile>();
        if (data.getConfigurationSection("players") == null) {
            return profiles;
        }

        for (String key : data.getConfigurationSection("players").getKeys(false)) {
            try {
                profiles.add(read(UUID.fromString(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }

        Collections.sort(profiles, new Comparator<Profile>() {
            public int compare(Profile first, Profile second) {
                if (ratio) {
                    return Double.compare(second.getRatio(), first.getRatio());
                }
                return Integer.compare(second.getWins(), first.getWins());
            }
        });

        return profiles;
    }

    private Profile read(UUID uuid) {
        String base = "players." + uuid;
        return new Profile(
                uuid,
                data.getString(base + ".name", "Unknown"),
                data.getInt(base + ".wins"),
                data.getInt(base + ".losses"),
                data.getInt(base + ".streak"),
                data.getInt(base + ".highest-streak")
        );
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning(exception.getMessage());
        }
    }
}
