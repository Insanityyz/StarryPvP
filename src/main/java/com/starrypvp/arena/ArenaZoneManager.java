package com.starrypvp.arena;

import com.starrypvp.StarryPvP;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaZoneManager implements Listener {
    public static final class Zone {
        private final String key;
        private final String arenaName;
        private final String worldName;
        private final double centerX;
        private final double centerZ;
        private final boolean explicitCenter;
        private final double radius;
        private final int minY;
        private final int maxY;
        private final Set<String> whitelist = new HashSet<String>();
        private Location resolved;

        private Zone(String key, String arenaName, String worldName, double centerX, double centerZ,
                     boolean explicitCenter, double radius, int minY, int maxY) {
            this.key = key;
            this.arenaName = arenaName;
            this.worldName = worldName;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.explicitCenter = explicitCenter;
            this.radius = radius;
            this.minY = minY;
            this.maxY = maxY;
        }

        public String getName() {
            return key;
        }

        public String getArenaName() {
            return arenaName;
        }

        public double getRadius() {
            return radius;
        }

        public int getMinY() {
            return minY;
        }

        public int getMaxY() {
            return maxY;
        }

        private Location center(StarryPvP plugin) {
            if (resolved != null) {
                return resolved;
            }

            if (explicitCenter) {
                World world = Bukkit.getWorld(worldName);

                if (world == null) {
                    return null;
                }

                resolved = new Location(world, centerX, 0.0D, centerZ);
                return resolved;
            }

            Arena arena = plugin.getArenaManager().get(arenaName);

            if (arena == null) {
                return null;
            }

            Location value = arena.getCenter();

            if (value == null || value.getWorld() == null) {
                return null;
            }

            resolved = new Location(value.getWorld(), value.getX(), 0.0D, value.getZ());
            return resolved;
        }

        private boolean contains(StarryPvP plugin, Location location) {
            if (location == null || location.getWorld() == null) {
                return false;
            }

            Location center = center(plugin);

            if (center == null || center.getWorld() == null) {
                return false;
            }

            if (!center.getWorld().equals(location.getWorld())) {
                return false;
            }

            if (location.getBlockY() < minY || location.getBlockY() > maxY) {
                return false;
            }

            double dx = location.getX() - center.getX();
            double dz = location.getZ() - center.getZ();
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    private final StarryPvP plugin;
    private final Map<String, Zone> zones = new LinkedHashMap<String, Zone>();
    private final Map<UUID, Integer> countdowns = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Long> exitGrants = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> fallGrace = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> warnings = new ConcurrentHashMap<UUID, Long>();
    private final Set<UUID> internalTeleports = java.util.Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    private boolean enabled = true;
    private boolean allowOps = true;
    private boolean allowSpectators = true;
    private int warningSeconds = 5;
    private long intervalTicks = 20L;
    private long exitWindow = 20000L;
    private double maxRadius = 5000.0D;
    private double minRadius = 300.0D;
    private int maxAttempts = 40;
    private int scanTop = 256;
    private int scanBottom = 1;
    private long fallImmunity = 8000L;
    private BukkitTask task;

    public ArenaZoneManager(StarryPvP plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        zones.clear();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("arena-zones");

        if (root == null) {
            enabled = false;
            return;
        }

        enabled = root.getBoolean("enabled", true);
        allowOps = root.getBoolean("allow-ops", true);
        allowSpectators = root.getBoolean("allow-spectators", true);
        warningSeconds = Math.max(1, root.getInt("warning-seconds", 5));
        intervalTicks = Math.max(10L, root.getLong("check-interval-ticks", 20L));
        exitWindow = Math.max(5L, root.getLong("exit-confirmation-seconds", 20L)) * 1000L;
        maxRadius = Math.max(64.0D, root.getDouble("teleport.max-radius", 5000.0D));
        minRadius = Math.max(16.0D, root.getDouble("teleport.min-radius", 300.0D));
        maxAttempts = Math.max(1, root.getInt("teleport.max-attempts", 40));
        scanTop = root.getInt("teleport.scan-from-y", 256);
        scanBottom = Math.max(1, root.getInt("teleport.scan-to-y", 1));
        fallImmunity = Math.max(1L, root.getLong("teleport.fall-immunity-seconds", 8L)) * 1000L;

        ConfigurationSection list = root.getConfigurationSection("zones");

        if (list == null) {
            return;
        }

        for (String key : list.getKeys(false)) {
            ConfigurationSection section = list.getConfigurationSection(key);

            if (section == null) {
                continue;
            }

            String world = section.getString("world", "");
            boolean explicit = world != null && world.trim().length() > 0
                    && section.contains("x") && section.contains("z");

            Zone zone = new Zone(
                    key,
                    section.getString("arena", key),
                    world,
                    section.getDouble("x", 0.0D),
                    section.getDouble("z", 0.0D),
                    explicit,
                    section.getDouble("radius", 150.0D),
                    section.getInt("y-min", -1),
                    section.getInt("y-max", 256)
            );

            for (String entry : section.getStringList("whitelist")) {
                if (entry != null && entry.trim().length() > 0) {
                    zone.whitelist.add(entry.toLowerCase().trim());
                }
            }

            zones.put(key.toLowerCase(), zone);
        }
    }

    public void start() {
        stop();

        if (!enabled) {
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            public void run() {
                tick();
            }
        }, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public Collection<Zone> zones() {
        return new ArrayList<Zone>(zones.values());
    }

    public Zone findZone(Location location) {
        if (!enabled || location == null) {
            return null;
        }

        for (Zone zone : zones.values()) {
            if (zone.contains(plugin, location)) {
                return zone;
            }
        }

        return null;
    }

    public boolean isRestricted(Player player) {
        if (!enabled || player == null) {
            return false;
        }

        Zone zone = findZone(player.getLocation());
        return zone != null && !isPermitted(player, zone);
    }

    public boolean whitelist(String zoneKey, String name, boolean add) {
        Zone zone = zones.get(zoneKey.toLowerCase());

        if (zone == null || name == null || name.trim().length() == 0) {
            return false;
        }

        String path = "arena-zones.zones." + zone.key + ".whitelist";
        List<String> current = new ArrayList<String>(plugin.getConfig().getStringList(path));
        String value = name.toLowerCase().trim();

        if (add) {
            if (current.contains(value)) {
                return false;
            }
            current.add(value);
        } else if (!current.remove(value)) {
            return false;
        }

        plugin.getConfig().set(path, current);
        plugin.saveConfig();
        load();
        return true;
    }

    public void beginExitConfirmation(Player player) {
        if (player == null) {
            return;
        }

        exitGrants.put(player.getUniqueId(), Long.valueOf(System.currentTimeMillis() + exitWindow));
        countdowns.remove(player.getUniqueId());
    }

    public void confirmExit(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Zone zone = findZone(player.getLocation());

        if (zone == null) {
            exitGrants.remove(uuid);
            countdowns.remove(uuid);
            return;
        }

        Location exit = plugin.getMatchManager().safeExitLocation(player);

        if (exit != null && exit.getWorld() != null && findZone(exit) == null) {
            teleportSafely(player, exit);
        } else {
            Location fallback = randomSafeLocation(player, zone);

            if (fallback != null) {
                teleportSafely(player, fallback);
            }
        }

        exitGrants.remove(uuid);
        countdowns.remove(uuid);
        player.sendMessage(plugin.color("&7You were moved out of the arena zone."));
    }

    private boolean isPermitted(Player player, Zone zone) {
        if (player.hasPermission("starrypvp.zone.bypass")) {
            return true;
        }

        if (allowOps && player.isOp()) {
            return true;
        }

        if (zone.whitelist.contains(player.getName().toLowerCase())
                || zone.whitelist.contains(player.getUniqueId().toString().toLowerCase())) {
            return true;
        }

        Long grant = exitGrants.get(player.getUniqueId());

        if (grant != null && grant.longValue() >= System.currentTimeMillis()) {
            return true;
        }

        Arena arena = plugin.getMatchManager().getArenaOf(player);

        if (arena != null) {
            if (arena.getName().equalsIgnoreCase(zone.arenaName)) {
                return true;
            }

            Location center = arena.getCenter();

            if (center != null && zone.contains(plugin, center)) {
                return true;
            }
        }

        return allowSpectators && plugin.getMatchManager().isSpectating(player);
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (Player player : new ArrayList<Player>(Bukkit.getOnlinePlayers())) {
            UUID uuid = player.getUniqueId();
            Long grant = exitGrants.get(uuid);

            if (grant != null && grant.longValue() < now) {
                exitGrants.remove(uuid);
            }

            Long grace = fallGrace.get(uuid);

            if (grace != null && grace.longValue() < now) {
                fallGrace.remove(uuid);
            }

            Zone zone = findZone(player.getLocation());

            if (zone == null) {
                countdowns.remove(uuid);
                exitGrants.remove(uuid);
                continue;
            }

            if (exitGrants.containsKey(uuid)) {
                continue;
            }

            if (isPermitted(player, zone)) {
                countdowns.remove(uuid);
                continue;
            }

            if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
                plugin.getMatchManager().forceSpectatorCleanup(player);

                if (isPermitted(player, zone)) {
                    continue;
                }

                countdowns.remove(uuid);
                player.setGameMode(GameMode.SURVIVAL);
                eject(player, zone);
                continue;
            }

            Integer remaining = countdowns.get(uuid);

            if (remaining == null) {
                countdowns.put(uuid, Integer.valueOf(warningSeconds));
                player.sendMessage(plugin.color("&cYou are inside the &f" + zone.getName()
                        + " &carena and you are not in a match. You will be teleported away."));
                continue;
            }

            int value = remaining.intValue();

            if (value <= 0) {
                eject(player, zone);
                continue;
            }

            player.sendMessage(plugin.color("&cTeleporting in &f" + value + "&c..."));
            countdowns.put(uuid, Integer.valueOf(value - 1));
        }
    }

    private void eject(Player player, Zone zone) {
        countdowns.remove(player.getUniqueId());
        Location destination = randomSafeLocation(player, zone);

        if (destination == null) {
            destination = plugin.getMatchManager().safeExitLocation(player);
        }

        if (destination == null || destination.getWorld() == null) {
            return;
        }

        teleportSafely(player, destination);
        player.sendMessage(plugin.color("&7You were teleported out of the arena."));
    }

    private void teleportSafely(final Player player, Location destination) {
        final UUID uuid = player.getUniqueId();
        internalTeleports.add(uuid);
        fallGrace.put(uuid, Long.valueOf(System.currentTimeMillis() + fallImmunity));

        try {
            destination.getWorld().loadChunk(destination.getBlockX() >> 4, destination.getBlockZ() >> 4);
            player.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
            player.setFallDistance(0.0F);
            player.teleport(destination);
            player.setFallDistance(0.0F);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not teleport " + player.getName() + " out of an arena zone: "
                    + throwable.getMessage());
        } finally {
            internalTeleports.remove(uuid);
        }

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                if (player.isOnline()) {
                    player.setFallDistance(0.0F);
                }
            }
        }, 1L);
    }

    private Location randomSafeLocation(Player player, Zone zone) {
        Location center = zone == null ? null : zone.center(plugin);
        World world = center == null ? player.getWorld() : center.getWorld();

        if (world == null) {
            return null;
        }

        double originX = center == null ? player.getLocation().getX() : center.getX();
        double originZ = center == null ? player.getLocation().getZ() : center.getZ();
        double span = Math.max(1.0D, maxRadius - minRadius);

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double angle = Math.random() * Math.PI * 2.0D;
            double distance = minRadius + Math.random() * span;
            int x = (int) Math.floor(originX + Math.cos(angle) * distance);
            int z = (int) Math.floor(originZ + Math.sin(angle) * distance);
            Location candidate = safeGround(world, x, z);

            if (candidate == null) {
                continue;
            }

            if (findZone(candidate) != null) {
                continue;
            }

            if (plugin.getArenaProtectionManager().isInsideActiveArena(candidate)) {
                continue;
            }

            candidate.setYaw(player.getLocation().getYaw());
            candidate.setPitch(0.0F);
            return candidate;
        }

        return null;
    }

    private Location safeGround(World world, int x, int z) {
        try {
            world.loadChunk(x >> 4, z >> 4);
        } catch (Throwable throwable) {
            return null;
        }

        int ceiling = Math.min(scanTop, world.getMaxHeight() - 3);
        int floor = Math.max(1, scanBottom);

        for (int y = ceiling; y >= floor; y--) {
            Block ground = world.getBlockAt(x, y, z);
            Material type = ground.getType();

            if (type == Material.AIR || !type.isSolid() || isUnsafe(type)) {
                continue;
            }

            if (world.getBlockAt(x, y + 1, z).getType() != Material.AIR) {
                continue;
            }

            if (world.getBlockAt(x, y + 2, z).getType() != Material.AIR) {
                continue;
            }

            return new Location(world, x + 0.5D, y + 1.0D, z + 0.5D);
        }

        return null;
    }

    private boolean isUnsafe(Material type) {
        return type == Material.LAVA
                || type == Material.STATIONARY_LAVA
                || type == Material.WATER
                || type == Material.STATIONARY_WATER
                || type == Material.FIRE
                || type == Material.CACTUS;
    }

    private void warn(Player player, String message) {
        long now = System.currentTimeMillis();
        Long last = warnings.get(player.getUniqueId());

        if (last != null && last.longValue() > now) {
            return;
        }

        warnings.put(player.getUniqueId(), Long.valueOf(now + 2500L));
        player.sendMessage(plugin.color(message));
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) {
            return;
        }

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Zone zone = findZone(to);

        if (zone == null || findZone(from) != null) {
            return;
        }

        Player player = event.getPlayer();

        if (isPermitted(player, zone)) {
            return;
        }

        Location hold = from.clone();
        hold.setYaw(to.getYaw());
        hold.setPitch(to.getPitch());
        event.setTo(hold);
        warn(player, "&cYou cannot enter &f" + zone.getName() + " &cunless you are in a match.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!enabled) {
            return;
        }

        Player player = event.getPlayer();

        if (internalTeleports.contains(player.getUniqueId())) {
            return;
        }

        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && cause != PlayerTeleportEvent.TeleportCause.COMMAND
                && cause != PlayerTeleportEvent.TeleportCause.SPECTATE
                && cause != PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return;
        }

        Zone zone = findZone(event.getTo());

        if (zone == null || isPermitted(player, zone)) {
            return;
        }

        event.setCancelled(true);
        warn(player, "&cYou cannot teleport into &f" + zone.getName() + "&c.");
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        if (!enabled) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() {
                Player player = event.getPlayer();

                if (!player.isOnline()) {
                    return;
                }

                Zone zone = findZone(player.getLocation());

                if (zone == null || isPermitted(player, zone)) {
                    return;
                }

                eject(player, zone);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        Player player = (Player) event.getEntity();
        Long until = fallGrace.get(player.getUniqueId());

        if (until == null) {
            return;
        }

        if (until.longValue() < System.currentTimeMillis()) {
            fallGrace.remove(player.getUniqueId());
            return;
        }

        event.setCancelled(true);
        player.setFallDistance(0.0F);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZoneDamage(EntityDamageByEntityEvent event) {
        if (!enabled) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());

        if (attacker != null && isRestricted(attacker)) {
            event.setCancelled(true);
            warn(attacker, "&cYou cannot fight inside &f" + findZone(attacker.getLocation()).getName() + "&c.");
            return;
        }

        if (event.getEntity() instanceof Player && isRestricted((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZoneBreak(BlockBreakEvent event) {
        if (isRestricted(event.getPlayer())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "&cYou cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZonePlace(BlockPlaceEvent event) {
        if (isRestricted(event.getPlayer())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "&cYou cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZoneInteract(PlayerInteractEvent event) {
        if (isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZoneEntityInteract(PlayerInteractEntityEvent event) {
        if (isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZoneDrop(PlayerDropItemEvent event) {
        if (isRestricted(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZonePickup(PlayerPickupItemEvent event) {
        if (isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZoneBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZoneBucketFill(PlayerBucketFillEvent event) {
        if (isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZoneInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();

        if (!isRestricted(player)) {
            return;
        }

        InventoryType type = event.getInventory().getType();
        InventoryHolder holder = event.getInventory().getHolder();

        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) {
            return;
        }

        if (type == InventoryType.CHEST && holder == null) {
            return;
        }

        event.setCancelled(true);
        warn(player, "&cYou cannot open containers here.");
    }
}
