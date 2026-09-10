package com.starrypvp.match;

import com.starrypvp.arena.Arena;
import com.starrypvp.util.EventColor;
import com.starrypvp.util.InventorySnapshot;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Match {
    public enum Type {
        DUEL,
        TEAM,
        PRACTICE,
        CUSTOM_FFA
    }

    private final UUID id = UUID.randomUUID();
    private final Type type;
    private final Arena arena;
    private final MatchSettings settings;
    private final Set<UUID> red = new LinkedHashSet<UUID>();
    private final Set<UUID> blue = new LinkedHashSet<UUID>();
    private final Set<UUID> alive = new LinkedHashSet<UUID>();
    private final Set<UUID> spectators = new LinkedHashSet<UUID>();
    private final Map<UUID, InventorySnapshot> snapshots = new LinkedHashMap<UUID, InventorySnapshot>();
    private final Map<UUID, Integer> kills = new LinkedHashMap<UUID, Integer>();
    private final Map<UUID, EventColor> colors = new LinkedHashMap<UUID, EventColor>();
    private final long startedAt = System.currentTimeMillis();
    private boolean directDamage;
    private boolean ended;

    public Match(Type type, Arena arena, MatchSettings settings, Collection<Player> redPlayers, Collection<Player> bluePlayers) {
        this.type = type;
        this.arena = arena;
        this.settings = settings.clone();

        for (Player player : redPlayers) {
            red.add(player.getUniqueId());
            alive.add(player.getUniqueId());
            snapshots.put(player.getUniqueId(), new InventorySnapshot(player));
        }

        for (Player player : bluePlayers) {
            blue.add(player.getUniqueId());
            alive.add(player.getUniqueId());
            snapshots.put(player.getUniqueId(), new InventorySnapshot(player));
        }
    }

    public UUID getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public Arena getArena() {
        return arena;
    }

    public MatchSettings getSettings() {
        return settings;
    }

    public Set<UUID> getRed() {
        return new LinkedHashSet<UUID>(red);
    }

    public Set<UUID> getBlue() {
        return new LinkedHashSet<UUID>(blue);
    }

    public Set<UUID> getAlive() {
        return new LinkedHashSet<UUID>(alive);
    }

    public Set<UUID> getParticipants() {
        Set<UUID> participants = new LinkedHashSet<UUID>(red);
        participants.addAll(blue);
        return participants;
    }

    public boolean isParticipant(UUID uuid) {
        return red.contains(uuid) || blue.contains(uuid);
    }

    public boolean isRed(UUID uuid) {
        return red.contains(uuid);
    }

    public boolean sameTeam(UUID first, UUID second) {
        if (type == Type.CUSTOM_FFA) {
            return false;
        }
        return red.contains(first) && red.contains(second) || blue.contains(first) && blue.contains(second);
    }

    public void eliminate(UUID uuid) {
        alive.remove(uuid);
    }

    public InventorySnapshot getSnapshot(UUID uuid) {
        return snapshots.get(uuid);
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void addKill(UUID uuid) {
        if (uuid == null) {
            return;
        }

        Integer current = kills.get(uuid);
        kills.put(uuid, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
    }

    public int getKills(UUID uuid) {
        Integer current = kills.get(uuid);
        return current == null ? 0 : current.intValue();
    }

    public EventColor getColor(UUID uuid) {
        return colors.get(uuid);
    }

    public void setColor(UUID uuid, EventColor color) {
        if (uuid != null && color != null) {
            colors.put(uuid, color);
        }
    }

    public boolean hasDirectDamage() {
        return directDamage;
    }

    public void setDirectDamage(boolean directDamage) {
        this.directDamage = directDamage;
    }

    public boolean isEnded() {
        return ended;
    }

    public void setEnded(boolean ended) {
        this.ended = ended;
    }

    public Set<UUID> getSpectators() {
        return spectators;
    }

    public Collection<UUID> remainingRed() {
        Collection<UUID> values = new ArrayList<UUID>();
        for (UUID uuid : red) {
            if (alive.contains(uuid)) {
                values.add(uuid);
            }
        }
        return values;
    }

    public Collection<UUID> remainingBlue() {
        Collection<UUID> values = new ArrayList<UUID>();
        for (UUID uuid : blue) {
            if (alive.contains(uuid)) {
                values.add(uuid);
            }
        }
        return values;
    }
}
