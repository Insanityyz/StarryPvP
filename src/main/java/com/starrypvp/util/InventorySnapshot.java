package com.starrypvp.util;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class InventorySnapshot {
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final Location location;
    private final GameMode gameMode;
    private final double health;
    private final int food;
    private final float saturation;
    private final int level;
    private final float experience;
    private final int totalExperience;
    private final boolean allowFlight;
    private final boolean flying;
    private final List<PotionEffect> effects;

    public InventorySnapshot(Player player) {
        this(
                cloneItems(player.getInventory().getContents()),
                cloneItems(player.getInventory().getArmorContents()),
                player.getLocation().clone(),
                player.getGameMode(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getLevel(),
                player.getExp(),
                player.getTotalExperience(),
                player.getAllowFlight(),
                player.isFlying(),
                new ArrayList<PotionEffect>(player.getActivePotionEffects())
        );
    }

    private InventorySnapshot(ItemStack[] contents, ItemStack[] armor, Location location,
                              GameMode gameMode, double health, int food, float saturation,
                              int level, float experience, int totalExperience,
                              boolean allowFlight, boolean flying,
                              Collection<PotionEffect> effects) {
        this.contents = contents;
        this.armor = armor;
        this.location = location;
        this.gameMode = gameMode;
        this.health = health;
        this.food = food;
        this.saturation = saturation;
        this.level = level;
        this.experience = experience;
        this.totalExperience = totalExperience;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.effects = new ArrayList<PotionEffect>(effects);
    }

    public void restore(Player player) {
        player.closeInventory();

        try {
            player.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setContents(cloneItems(contents));
        player.getInventory().setArmorContents(cloneItems(armor));
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);

        try {
            player.setFlying(allowFlight && flying);
        } catch (Throwable ignored) {
        }

        player.setFoodLevel(food);
        player.setSaturation(saturation);
        player.setTotalExperience(0);
        player.setLevel(level);
        player.setExp(experience);
        player.setTotalExperience(totalExperience);
        player.setFireTicks(0);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect, true);
        }

        if (!player.isDead()) {
            player.setHealth(Math.min(health, player.getMaxHealth()));
        }

        if (location != null && location.getWorld() != null) {
            player.teleport(location);
        }

        player.updateInventory();
    }

    public void write(ConfigurationSection configuration, String path) {
        configuration.set(path + ".contents", Arrays.asList(cloneItems(contents)));
        configuration.set(path + ".armor", Arrays.asList(cloneItems(armor)));
        configuration.set(path + ".world", location.getWorld().getName());
        configuration.set(path + ".x", location.getX());
        configuration.set(path + ".y", location.getY());
        configuration.set(path + ".z", location.getZ());
        configuration.set(path + ".yaw", location.getYaw());
        configuration.set(path + ".pitch", location.getPitch());
        configuration.set(path + ".gamemode", gameMode.name());
        configuration.set(path + ".health", health);
        configuration.set(path + ".food", food);
        configuration.set(path + ".saturation", saturation);
        configuration.set(path + ".level", level);
        configuration.set(path + ".experience", experience);
        configuration.set(path + ".total-experience", totalExperience);
        configuration.set(path + ".allow-flight", allowFlight);
        configuration.set(path + ".flying", flying);
        configuration.set(path + ".effects", new ArrayList<PotionEffect>(effects));
    }

    public static InventorySnapshot read(ConfigurationSection configuration, String path) {
        World world = Bukkit.getWorld(configuration.getString(path + ".world"));
        Location location = null;

        if (world != null) {
            location = new Location(
                    world,
                    configuration.getDouble(path + ".x"),
                    configuration.getDouble(path + ".y"),
                    configuration.getDouble(path + ".z"),
                    (float) configuration.getDouble(path + ".yaw"),
                    (float) configuration.getDouble(path + ".pitch")
            );
        }

        GameMode gameMode;

        try {
            gameMode = GameMode.valueOf(configuration.getString(path + ".gamemode", "SURVIVAL"));
        } catch (IllegalArgumentException exception) {
            gameMode = GameMode.SURVIVAL;
        }

        return new InventorySnapshot(
                readItems(configuration.getList(path + ".contents")),
                readItems(configuration.getList(path + ".armor")),
                location,
                gameMode,
                configuration.getDouble(path + ".health", 20.0D),
                configuration.getInt(path + ".food", 20),
                (float) configuration.getDouble(path + ".saturation", 5.0D),
                configuration.getInt(path + ".level"),
                (float) configuration.getDouble(path + ".experience"),
                configuration.getInt(path + ".total-experience"),
                configuration.getBoolean(path + ".allow-flight"),
                configuration.getBoolean(path + ".flying"),
                readEffects(configuration.getList(path + ".effects"))
        );
    }

    private static List<PotionEffect> readEffects(List<?> values) {
        List<PotionEffect> effects = new ArrayList<PotionEffect>();

        if (values == null) {
            return effects;
        }

        for (Object value : values) {
            if (value instanceof PotionEffect) {
                effects.add((PotionEffect) value);
            }
        }

        return effects;
    }

    private static ItemStack[] readItems(List<?> values) {
        if (values == null) {
            return new ItemStack[0];
        }

        List<ItemStack> items = new ArrayList<ItemStack>();

        for (Object value : values) {
            items.add(value instanceof ItemStack ? ((ItemStack) value).clone() : null);
        }

        return items.toArray(new ItemStack[items.size()]);
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] cloned = new ItemStack[source.length];

        for (int index = 0; index < source.length; index++) {
            cloned[index] = source[index] == null ? null : source[index].clone();
        }

        return cloned;
    }
}
