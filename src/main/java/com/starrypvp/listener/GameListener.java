package com.starrypvp.listener;

import com.starrypvp.StarryPvP;
import com.starrypvp.match.Match;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class GameListener implements Listener {
    private final StarryPvP plugin;

    public GameListener(StarryPvP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = resolveAttacker(event.getDamager());

        if (attacker == null) {
            return;
        }

        Match victimMatch = plugin.getMatchManager().getMatch(victim);
        Match attackerMatch = plugin.getMatchManager().getMatch(attacker);

        if (victimMatch != null || attackerMatch != null) {
            if (!plugin.getMatchManager().canDamage(attacker, victim)) {
                event.setCancelled(true);
                return;
            }
            plugin.getMatchManager().markDirectDamage(attacker, victim);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);

        if (plugin.getMatchManager().getMatch(player) != null) {
            plugin.getMatchManager().eliminate(player);
            return;
        }

        if (plugin.getMatchManager().isPublicFfa(player)) {
            long delay = plugin.getConfig().getLong("ffa.respawn-delay-ticks", 20L);
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                public void run() {
                    if (player.isOnline()) {
                        if (player.isDead()) {
                            player.spigot().respawn();
                        }
                        plugin.getMatchManager().respawnFfa(player);
                    }
                }
            }, delay);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Match match = plugin.getMatchManager().getMatch(player);
        if (match != null && match.getArena().getSpectatorSpawn() != null) {
            event.setRespawnLocation(match.getArena().getSpectatorSpawn());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getMatchManager().getMatch(player) != null) {
            plugin.getMatchManager().requestForfeit(player);
            plugin.getMatchManager().requestForfeit(player);
        }

        if (plugin.getMatchManager().isPublicFfa(player)) {
            plugin.getMatchManager().leaveFfa(player);
        }

        plugin.getPartyManager().leave(player);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Match match = plugin.getMatchManager().getMatch(event.getPlayer());
        if (match != null && !match.getSettings().isBuilding()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Match match = plugin.getMatchManager().getMatch(event.getPlayer());
        if (match != null && !match.getSettings().isBuilding()) {
            event.setCancelled(true);
        }
    }

    private Player resolveAttacker(Entity entity) {
        if (entity instanceof Player) {
            return (Player) entity;
        }

        if (entity instanceof Projectile) {
            ProjectileSource source = ((Projectile) entity).getShooter();
            if (source instanceof Player) {
                return (Player) source;
            }
        }

        if (entity instanceof Arrow) {
            ProjectileSource source = ((Arrow) entity).getShooter();
            if (source instanceof Player) {
                return (Player) source;
            }
        }

        return null;
    }
}
