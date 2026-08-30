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
        this.spectatorSpawn = spectatorSpawn.clone();
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
}
