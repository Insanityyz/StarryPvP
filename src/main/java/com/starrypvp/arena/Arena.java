package com.starrypvp.arena;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Arena {
    public enum Mode {
        DUEL,
        TEAM,
        FFA
    }

    private final String name;
    private final Mode mode;
    private final List<Location> redSpawns = new ArrayList<Location>();
    private final List<Location> blueSpawns = new ArrayList<Location>();
    private final List<Location> ffaSpawns = new ArrayList<Location>();
    private Location spectatorSpawn;
    private boolean occupied;

    public Arena(String name, Mode mode) {
        this.name = name;
        this.mode = mode;
    }

    public String getName() {
        return name;
    }

    public Mode getMode() {
        return mode;
    }

    public List<Location> getRedSpawns() {
        return Collections.unmodifiableList(redSpawns);
    }

    public List<Location> getBlueSpawns() {
        return Collections.unmodifiableList(blueSpawns);
    }

    public List<Location> getFfaSpawns() {
        return Collections.unmodifiableList(ffaSpawns);
    }

    public Location getSpectatorSpawn() {
        return spectatorSpawn == null ? null : spectatorSpawn.clone();
    }

    public void addRedSpawn(Location location) {
        redSpawns.add(location.clone());
    }

    public void addBlueSpawn(Location location) {
        blueSpawns.add(location.clone());
    }

    public void addFfaSpawn(Location location) {
        ffaSpawns.add(location.clone());
    }

    public void setSpectatorSpawn(Location spectatorSpawn) {
        this.spectatorSpawn = spectatorSpawn == null ? null : spectatorSpawn.clone();
    }

    public boolean setSpawn(String type, int index, Location location) {
        if (location == null || index < 1) {
            return false;
        }

        if (type.equalsIgnoreCase("SPECTATOR")) {
            if (index != 1) {
                return false;
            }

            spectatorSpawn = location.clone();
            return true;
        }

        List<Location> spawns = mutableSpawns(type);

        if (spawns == null || index > spawns.size() + 1) {
            return false;
        }

        if (index == spawns.size() + 1) {
            spawns.add(location.clone());
        } else {
            spawns.set(index - 1, location.clone());
        }

        return true;
    }

    public boolean removeSpawn(String type, int index) {
        if (index < 1) {
            return false;
        }

        if (type.equalsIgnoreCase("SPECTATOR")) {
            if (index != 1 || spectatorSpawn == null) {
                return false;
            }

            spectatorSpawn = null;
            return true;
        }

        List<Location> spawns = mutableSpawns(type);

        if (spawns == null || index > spawns.size()) {
            return false;
        }

        spawns.remove(index - 1);
        return true;
    }

    public Location getSpawn(String type, int index) {
        if (index < 1) {
            return null;
        }

        if (type.equalsIgnoreCase("SPECTATOR")) {
            return index == 1 && spectatorSpawn != null ? spectatorSpawn.clone() : null;
        }

        List<Location> spawns = mutableSpawns(type);

        if (spawns == null || index > spawns.size()) {
            return null;
        }

        return spawns.get(index - 1).clone();
    }

    public int getSpawnCount(String type) {
        if (type.equalsIgnoreCase("SPECTATOR")) {
            return spectatorSpawn == null ? 0 : 1;
        }

        List<Location> spawns = mutableSpawns(type);
        return spawns == null ? 0 : spawns.size();
    }

    private List<Location> mutableSpawns(String type) {
        if (type.equalsIgnoreCase("RED")) {
            return redSpawns;
        }

        if (type.equalsIgnoreCase("BLUE")) {
            return blueSpawns;
        }

        if (type.equalsIgnoreCase("FFA")) {
            return ffaSpawns;
        }

        return null;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    public boolean isReady() {
        if (mode == Mode.DUEL || mode == Mode.TEAM) {
            return !redSpawns.isEmpty() && !blueSpawns.isEmpty();
        }
        return !ffaSpawns.isEmpty();
    }
        public Location getCenter() {
        List<Location> locations = new ArrayList<Location>();
        locations.addAll(redSpawns);
        locations.addAll(blueSpawns);
        locations.addAll(ffaSpawns);

        if (spectatorSpawn != null) {
            locations.add(spectatorSpawn);
        }

        if (locations.isEmpty()) {
            return null;
        }

        Location first = locations.get(0);
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        int count = 0;

        for (Location location : locations) {
            if (location.getWorld() != null && location.getWorld().equals(first.getWorld())) {
                x += location.getX();
                y += location.getY();
                z += location.getZ();
                count++;
            }
        }

        if (count == 0) {
            return null;
        }

        return new Location(first.getWorld(), x / count, y / count, z / count);
    }
}
