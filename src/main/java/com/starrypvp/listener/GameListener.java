package com.starrypvp.listener;

import com.starrypvp.StarryPvP;
import com.starrypvp.match.Match;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public final class GameListener implements Listener {
    private final StarryPvP plugin;

    public GameListener(StarryPvP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                plugin.getMatchManager().forceSpectatorCleanup(event.getPlayer());
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = resolveAttacker(event.getDamager());

        if (plugin.getMatchManager().isSpectating(victim)) {
            event.setCancelled(true);
            return;
        }

        if (attacker == null) {
            return;
        }

        if (plugin.getMatchManager().isSpectating(attacker)) {
            event.setCancelled(true);
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnyDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player &&
                plugin.getMatchManager().isSpectating((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVelocity(PlayerVelocityEvent event) {
        Match match = plugin.getMatchManager().getMatch(event.getPlayer());

        if (match == null || !match.getSettings().isCustomKnockback()) {
            return;
        }

        double horizontal = plugin.getConfig().getDouble("combat.knockback.horizontal-multiplier", 1.0D);
        double vertical = plugin.getConfig().getDouble("combat.knockback.vertical-multiplier", 1.0D);
        double maximumVertical = plugin.getConfig().getDouble("combat.knockback.maximum-vertical", 0.8D);
        Vector velocity = event.getVelocity().clone();

        velocity.setX(velocity.getX() * horizontal);
        velocity.setZ(velocity.getZ() * horizontal);
        velocity.setY(Math.min(maximumVertical, velocity.getY() * vertical));
        event.setVelocity(velocity);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();

        if (plugin.getMatchManager().getMatch(player) != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepLevel(true);
            plugin.getMatchManager().eliminate(player);
            return;
        }

        if (plugin.getMatchManager().isPublicFfa(player)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepLevel(true);

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

        if (plugin.getMatchManager().isSpectating(player)) {
            plugin.getMatchManager().stopSpectating(player);
        }

        plugin.getPartyManager().leave(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (plugin.getMatchManager().getMatch(player) == null) {
            return;
        }

        String command = event.getMessage().toLowerCase();

        if (command.startsWith("/pvp leave") ||
                command.startsWith("/pvp forfeit") ||
                command.startsWith("/pvp stats") ||
                command.startsWith("/pvp help")) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(plugin.color("&cCommands are blocked during matches. Use /pvp forfeit to leave."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.getMatchManager().isSpectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (plugin.getMatchManager().isSpectating(event.getPlayer())) {
            event.setCancelled(true);
        }
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

        return null;
    }
}
