package com.starrypvp.listener;

import com.starrypvp.StarryPvP;
import com.starrypvp.arena.Arena;
import com.starrypvp.util.KitTag;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiDupeListener implements Listener {
    private final StarryPvP plugin;
    private final Map<UUID, Long> warnings = new ConcurrentHashMap<UUID, Long>();

    public AntiDupeListener(StarryPvP plugin) {
        this.plugin = plugin;
    }

    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long last = warnings.get(player.getUniqueId());

        if (last != null && last > now) {
            return;
        }

        warnings.put(player.getUniqueId(), now + 3000L);
        player.sendMessage(plugin.color(message));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getMatchManager().isProtected(player)) {
            if (KitTag.isTagged(event.getItemDrop().getItemStack())) {
                event.getItemDrop().remove();
                event.setCancelled(true);
                player.updateInventory();
            }
            return;
        }

        if (!plugin.getConfig().getBoolean("security.block-item-drops", true)) {
            return;
        }

        event.setCancelled(true);
        player.updateInventory();
        warn(player, "&cYou cannot drop items while you are in a StarryPvP arena.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        boolean tagged = KitTag.isTagged(event.getItem().getItemStack());
        boolean inside = plugin.getMatchManager().isProtected(player);

        if (tagged && !inside) {
            event.setCancelled(true);
            event.getItem().remove();
            return;
        }

        if (inside && plugin.getConfig().getBoolean("security.block-item-pickup", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("security.block-item-spawn-in-arena", true)) {
            return;
        }

        if (plugin.getArenaProtectionManager().isInsideActiveArena(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();

        if (!plugin.getMatchManager().isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean("security.block-container-access", true)) {
            return;
        }

        InventoryType type = event.getInventory().getType();
        InventoryHolder holder = event.getInventory().getHolder();

        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) {
            return;
        }

        // StarryPvP's own menus are created with a null holder.
        if (type == InventoryType.CHEST && holder == null) {
            return;
        }

        event.setCancelled(true);
        warn(player, "&cContainers are locked while you are in a StarryPvP arena.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (!plugin.getMatchManager().isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean("security.block-item-drops", true)) {
            return;
        }

        InventoryAction action = event.getAction();
        boolean dropping = action == InventoryAction.DROP_ALL_CURSOR
                || action == InventoryAction.DROP_ONE_CURSOR
                || action == InventoryAction.DROP_ALL_SLOT
                || action == InventoryAction.DROP_ONE_SLOT;

        if (dropping || event.getRawSlot() == -999) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getMatchManager().isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean("security.block-container-access", true)) {
            return;
        }

        Entity entity = event.getRightClicked();

        if (entity instanceof ItemFrame || entity instanceof InventoryHolder) {
            event.setCancelled(true);
            warn(player, "&cYou cannot store items while you are in a StarryPvP arena.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("security.contain-players", true)) {
            return;
        }

        Location to = event.getTo();
        Location from = event.getFrom();

        if (to == null) {
            return;
        }

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Arena arena = plugin.getMatchManager().getArenaOf(player);

        if (arena == null) {
            return;
        }

        Location centre = arena.getCenter();

        if (centre == null || centre.getWorld() == null) {
            return;
        }

        double radius = plugin.getConfig().getDouble("security.containment-radius",
                plugin.getConfig().getDouble("arena-protection.radius", 150.0D));

        if (radius <= 0.0D) {
            return;
        }

        boolean sameWorld = centre.getWorld().equals(to.getWorld());

        if (sameWorld && centre.distanceSquared(to) <= radius * radius) {
            return;
        }

        Location recall = plugin.getMatchManager().recallLocation(player, arena);

        if (recall != null && recall.getWorld() != null) {
            event.setTo(recall);
            warn(player, "&cYou cannot leave the arena during a match.");
        }
    }
}
