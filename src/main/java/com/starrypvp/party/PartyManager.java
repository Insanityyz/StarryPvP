package com.starrypvp.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyManager {
    public static final class Party {
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<UUID>();

        private Party(UUID leader) {
            this.leader = leader;
            members.add(leader);
        }

        public UUID getLeader() {
            return leader;
        }

        public Set<UUID> getMembers() {
            return new LinkedHashSet<UUID>(members);
        }
    }

    private final Map<UUID, Party> membership = new ConcurrentHashMap<UUID, Party>();
    private final Map<UUID, UUID> invitations = new ConcurrentHashMap<UUID, UUID>();
    private final Map<UUID, String> sidePreferences = new ConcurrentHashMap<UUID, String>();

    public Party create(Player player) {
        Party current = membership.get(player.getUniqueId());
        if (current != null) {
            return current;
        }

        Party party = new Party(player.getUniqueId());
        membership.put(player.getUniqueId(), party);
        return party;
    }

    public Party get(Player player) {
        return membership.get(player.getUniqueId());
    }

    public Party get(UUID uuid) {
        return membership.get(uuid);
    }

    public boolean invite(Player leader, Player target) {
        Party party = membership.get(leader.getUniqueId());
        if (party == null || !party.leader.equals(leader.getUniqueId())) {
            return false;
        }
        invitations.put(target.getUniqueId(), leader.getUniqueId());
        return true;
    }

    public boolean acceptInvite(Player player) {
        UUID leaderId = invitations.remove(player.getUniqueId());
        if (leaderId == null || membership.containsKey(player.getUniqueId())) {
            return false;
        }

        Party party = membership.get(leaderId);
        if (party == null) {
            return false;
        }

        party.members.add(player.getUniqueId());
        membership.put(player.getUniqueId(), party);
        return true;
    }

    public boolean kick(Player leader, Player target) {
        Party party = membership.get(leader.getUniqueId());
        if (party == null || !party.leader.equals(leader.getUniqueId()) || leader.equals(target)) {
            return false;
        }

        if (!party.members.remove(target.getUniqueId())) {
            return false;
        }

        membership.remove(target.getUniqueId());
        sidePreferences.remove(target.getUniqueId());
        return true;
    }

    public boolean leave(Player player) {
        Party party = membership.get(player.getUniqueId());
        if (party == null) {
            return false;
        }

        if (party.leader.equals(player.getUniqueId())) {
            disband(player);
            return true;
        }

        party.members.remove(player.getUniqueId());
        membership.remove(player.getUniqueId());
        sidePreferences.remove(player.getUniqueId());
        return true;
    }

    public boolean disband(Player leader) {
        Party party = membership.get(leader.getUniqueId());
        if (party == null || !party.leader.equals(leader.getUniqueId())) {
            return false;
        }

        for (UUID uuid : party.members) {
            membership.remove(uuid);
            sidePreferences.remove(uuid);
        }
        return true;
    }

    public boolean isLeader(Player player) {
        Party party = membership.get(player.getUniqueId());
        return party != null && party.leader.equals(player.getUniqueId());
    }

    public Collection<Player> onlineMembers(Party party) {
        Collection<Player> players = new ArrayList<Player>();
        if (party == null) {
            return players;
        }

        for (UUID uuid : party.members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    public void setSide(Player player, String side) {
        sidePreferences.put(player.getUniqueId(), side.toUpperCase());
    }

    public String getSide(Player player) {
        return sidePreferences.get(player.getUniqueId());
    }
}
