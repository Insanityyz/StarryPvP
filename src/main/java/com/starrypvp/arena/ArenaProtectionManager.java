package com.starrypvp.arena;

import com.starrypvp.StarryPvP;
import com.starrypvp.match.Match;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaProtectionManager implements Listener {
    private static final class Journal {
        private final Match match;
        private final Map<String, BlockState> blocks = new LinkedHashMap<String, BlockState>();

        private Journal(Match match) {
            this.match = match;
        }
    }

    private final StarryPvP plugin;
    private final Map<UUID, Journal> journals = new ConcurrentHashMap<UUID, Journal>();

    public ArenaProtectionManager(StarryPvP plugin) {
        this.plugin = plugin;
    }

    public void begin(Match match) {
        Journal journal = new Journal(match);
        journals.put(match.getId(), journal);
        clearMobs(match.getArena());
    }

    public void rollback(Match match) {
        Journal journal = journals.remove(match.getId());
        if (journal == null) {
            return;
        }

        List<BlockState> states = new ArrayList<BlockState>(journal.blocks.values());

        for (int index = states.size() - 1; index >= 0; index--) {
            try {
                states.get(index).update(true, false);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Could not restore block at " + format(states.get(index).getLocation()));
            }
        }
    }

    public boolean isInsideActiveArena(Location location) {
        return find(location) != null;
    }

    private void clearMobs(Arena arena) {
        Location center = arena.getCenter();
        if (center == null || center.getWorld() == null) {
            return;
        }

        double radius = plugin.getConfig().getDouble("arena-protection.radius", 150.0D);
        double squared = radius * radius;

        for (Entity entity : new ArrayList<Entity>(center.getWorld().getEntities())) {
            if (!(entity instanceof LivingEntity) || entity instanceof Player || entity instanceof ArmorStand) {
                continue;
            }

            if (entity.getLocation().distanceSquared(center) <= squared) {
                entity.remove();
            }
        }
    }

    private Journal find(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        double radius = plugin.getConfig().getDouble("arena-protection.radius", 150.0D);
        double squared = radius * radius;

        for (Journal journal : journals.values()) {
            Location center = journal.match.getArena().getCenter();
            if (center == null || center.getWorld() == null) {
                continue;
            }

            if (!center.getWorld().equals(location.getWorld())) {
                continue;
            }

            if (center.distanceSquared(location) <= squared) {
                return journal;
            }
        }

        return null;
    }

    private void capture(Block block) {
        if (block == null) {
            return;
        }

        Journal journal = find(block.getLocation());
        if (journal == null) {
            return;
        }

        String key = key(block);
        if (!journal.blocks.containsKey(key)) {
            journal.blocks.put(key, block.getState());
        }
    }

    private void capture(BlockState state) {
        if (state == null) {
            return;
        }

        Journal journal = find(state.getLocation());
        if (journal == null) {
            return;
        }

        String key = key(state.getBlock());
        if (!journal.blocks.containsKey(key)) {
            journal.blocks.put(key, state);
        }
    }

    private String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private String format(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        capture(event.getBlockReplacedState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        for (BlockState state : event.getReplacedBlockStates()) {
            capture(state);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        capture(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        capture(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        capture(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        capture(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        capture(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            capture(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            capture(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        capture(event.getBlock());

        for (Block block : event.getBlocks()) {
            capture(block);
            capture(block.getRelative(event.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        capture(event.getBlock());

        for (Block block : event.getBlocks()) {
            capture(block);
            capture(block.getRelative(event.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        capture(event.getBlockClicked().getRelative(event.getBlockFace()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        capture(event.getBlockClicked());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockState) {
            capture((BlockState) holder);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isInsideActiveArena(event.getLocation())) {
            return;
        }

        if (event.getEntity() instanceof Ambient || event.getEntity() instanceof WaterMob || event.getEntity() instanceof LivingEntity) {
            event.setCancelled(true);
        }
    }
}
