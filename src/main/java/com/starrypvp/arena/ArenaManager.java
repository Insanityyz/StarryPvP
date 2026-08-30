package com.starrypvp.arena;

import com.starrypvp.StarryPvP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArenaManager {
    private final StarryPvP plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<String, Arena>();
    private final File file;
    private YamlConfiguration configuration;

    public ArenaManager(StarryPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        reload();
    }

    public void reload() {
        arenas.clear();
        configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = configuration.getConfigurationSection("arenas");
        if (root == null) {
            return;
        }

        for (String name : root.getKeys(false)) {
            String base = "arenas." + name;
            try {
                Arena.Mode mode = Arena.Mode.valueOf(configuration.getString(base + ".mode", "DUEL").toUpperCase());
                Arena arena = new Arena(name, mode);

                for (String value : configuration.getStringList(base + ".red")) {
                    Location location = decode(value);
                    if (location != null) {
                        arena.addRedSpawn(location);
                    }
                }

                for (String value : configuration.getStringList(base + ".blue")) {
                    Location location = decode(value);
                    if (location != null) {
                        arena.addBlueSpawn(location);
                    }
                }

                for (String value : configuration.getStringList(base + ".ffa")) {
                    Location location = decode(value);
                    if (location != null) {
                        arena.addFfaSpawn(location);
                    }
                }

                Location spectator = decode(configuration.getString(base + ".spectator"));
                if (spectator != null) {
                    arena.setSpectatorSpawn(spectator);
                }

                arenas.put(name.toLowerCase(), arena);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid arena mode for " + name);
            }
        }
    }

    public boolean create(String name, Arena.Mode mode) {
        if (arenas.containsKey(name.toLowerCase())) {
            return false;
        }
        Arena arena = new Arena(name, mode);
        arenas.put(name.toLowerCase(), arena);
        configuration.set("arenas." + name + ".mode", mode.name());
        configuration.set("arenas." + name + ".red", new ArrayList<String>());
        configuration.set("arenas." + name + ".blue", new ArrayList<String>());
        configuration.set("arenas." + name + ".ffa", new ArrayList<String>());
        save();
        return true;
    }

    public boolean delete(String name) {
        Arena removed = arenas.remove(name.toLowerCase());
        if (removed == null || removed.isOccupied()) {
            if (removed != null) {
                arenas.put(name.toLowerCase(), removed);
            }
            return false;
        }
        configuration.set("arenas." + removed.getName(), null);
        save();
        return true;
    }

    public boolean addSpawn(String name, String type, Location location) {
        Arena arena = get(name);
        if (arena == null) {
            return false;
        }

        String base = "arenas." + arena.getName();
        String encoded = encode(location);

        if (type.equalsIgnoreCase("RED")) {
            arena.addRedSpawn(location);
            List<String> values = configuration.getStringList(base + ".red");
            values.add(encoded);
            configuration.set(base + ".red", values);
        } else if (type.equalsIgnoreCase("BLUE")) {
            arena.addBlueSpawn(location);
            List<String> values = configuration.getStringList(base + ".blue");
            values.add(encoded);
            configuration.set(base + ".blue", values);
        } else if (type.equalsIgnoreCase("FFA")) {
            arena.addFfaSpawn(location);
            List<String> values = configuration.getStringList(base + ".ffa");
            values.add(encoded);
            configuration.set(base + ".ffa", values);
        } else if (type.equalsIgnoreCase("SPECTATOR")) {
            arena.setSpectatorSpawn(location);
            configuration.set(base + ".spectator", encoded);
        } else {
            return false;
        }

        save();
        return true;
    }

    public Arena acquire(Arena.Mode mode) {
        for (Arena arena : arenas.values()) {
            if (arena.getMode() == mode && arena.isReady() && !arena.isOccupied()) {
                arena.setOccupied(true);
                return arena;
            }
        }
        return null;
    }

    public void release(Arena arena) {
        if (arena != null) {
            arena.setOccupied(false);
        }
    }

    public Arena get(String name) {
        return name == null ? null : arenas.get(name.toLowerCase());
    }

    public Collection<Arena> all() {
        return new ArrayList<Arena>(arenas.values());
    }

    private String encode(Location location) {
        return location.getWorld().getName() + "," +
                location.getX() + "," +
                location.getY() + "," +
                location.getZ() + "," +
                location.getYaw() + "," +
                location.getPitch();
    }

    private Location decode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String[] split = value.split(",");
        if (split.length < 6) {
            return null;
        }

        World world = Bukkit.getWorld(split[0]);
        if (world == null) {
            return null;
        }

        try {
            return new Location(
                    world,
                    Double.parseDouble(split[1]),
                    Double.parseDouble(split[2]),
                    Double.parseDouble(split[3]),
                    Float.parseFloat(split[4]),
                    Float.parseFloat(split[5])
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void save() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning(exception.getMessage());
        }
    }
}
