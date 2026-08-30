package com.starrypvp.util;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
    private final boolean allowFlight;
    private final boolean flying;

    public InventorySnapshot(Player player) {
        this.contents = cloneItems(player.getInventory().getContents());
        this.armor = cloneItems(player.getInventory().getArmorContents());
        this.location = player.getLocation().clone();
        this.gameMode = player.getGameMode();
        this.health = player.getHealth();
        this.food = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.level = player.getLevel();
        this.experience = player.getExp();
        this.allowFlight = player.getAllowFlight();
        this.flying = player.isFlying();
    }

    public void restore(Player player) {
        player.getInventory().clear();
        player.getInventory().setContents(cloneItems(contents));
        player.getInventory().setArmorContents(cloneItems(armor));
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
        player.setFoodLevel(food);
        player.setSaturation(saturation);
        player.setLevel(level);
        player.setExp(experience);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        if (!player.isDead()) {
            player.setHealth(Math.min(health, player.getMaxHealth()));
        }
        player.teleport(location);
        player.updateInventory();
    }

    private ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] cloned = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            cloned[index] = source[index] == null ? null : source[index].clone();
        }
        return cloned;
    }
}
