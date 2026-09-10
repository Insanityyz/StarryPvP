package com.starrypvp.util;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Every item StarryPvP hands out is marked with an invisible lore line.
 * Tagged items can never be dropped, stored, or carried out of an arena,
 * and any that escape anyway are destroyed on contact.
 */
public final class KitTag {
    private static final String MARKER = ChatColor.BLACK.toString() + ChatColor.RESET;

    private KitTag() {
    }

    public static ItemStack tag(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        List<String> lore = meta.hasLore()
                ? new ArrayList<String>(meta.getLore())
                : new ArrayList<String>();

        if (!lore.contains(MARKER)) {
            lore.add(MARKER);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    public static ItemStack[] tagAll(ItemStack[] items) {
        if (items == null) {
            return null;
        }

        for (int index = 0; index < items.length; index++) {
            items[index] = tag(items[index]);
        }

        return items;
    }

    public static boolean isTagged(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasLore() && meta.getLore().contains(MARKER);
    }

    /**
     * Strips every tagged item from a player. Safe to call at any time.
     */
    public static int purge(Player player) {
        if (player == null) {
            return 0;
        }

        int removed = 0;
        ItemStack[] contents = player.getInventory().getContents();

        for (int index = 0; index < contents.length; index++) {
            if (isTagged(contents[index])) {
                contents[index] = null;
                removed++;
            }
        }

        player.getInventory().setContents(contents);

        ItemStack[] armor = player.getInventory().getArmorContents();

        for (int index = 0; index < armor.length; index++) {
            if (isTagged(armor[index])) {
                armor[index] = null;
                removed++;
            }
        }

        player.getInventory().setArmorContents(armor);

        if (isTagged(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed++;
        }

        if (removed > 0) {
            player.updateInventory();
        }

        return removed;
    }
}
