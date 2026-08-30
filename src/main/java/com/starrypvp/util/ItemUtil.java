package com.starrypvp.util;

import com.starrypvp.match.MatchSettings;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;

public final class ItemUtil {
    private ItemUtil() {
    }

    public static ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }

        return item;
    }

    public static void giveKit(Player player, MatchSettings settings, boolean redTeam) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        String material = weaponMaterial(settings.getArmorTier());

        if (settings.getWeaponMode().hasSword()) {
            ItemStack sword = new ItemStack(Material.valueOf(material + "_SWORD"));
            enchant(sword, settings.getSwordSharpness(), settings.getSwordUnbreaking());
            player.getInventory().addItem(sword);
        }

        if (settings.getWeaponMode().hasAxe()) {
            ItemStack axe = new ItemStack(Material.valueOf(material + "_AXE"));
            enchant(axe, settings.getAxeSharpness(), settings.getAxeUnbreaking());
            player.getInventory().addItem(axe);
        }

        player.getInventory().setArmorContents(armor(settings.getArmorTier(), redTeam));

        if (settings.getHealingMode() == MatchSettings.HealingMode.GAPPLE) {
            player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
        } else if (settings.getHealingMode() == MatchSettings.HealingMode.POTIONS) {
            player.getInventory().addItem(createHealingPotion(3));
        }

        if (settings.isBuilding()) {
            short color = redTeam ? (short) 14 : (short) 11;
            player.getInventory().addItem(new ItemStack(Material.WOOL, 64, color));
            player.getInventory().addItem(new ItemStack(Material.WOOL, 64, color));
        }

        player.updateInventory();
    }

    private static ItemStack createHealingPotion(int amount) {
        Material splashPotion = Material.matchMaterial("SPLASH_POTION");
        ItemStack potion;

        if (splashPotion != null) {
            potion = new ItemStack(splashPotion, amount);
        } else {
            potion = new ItemStack(Material.POTION, amount, (short) 16384);
        }

        ItemMeta rawMeta = potion.getItemMeta();

        if (rawMeta instanceof PotionMeta) {
            PotionMeta meta = (PotionMeta) rawMeta;
            meta.setDisplayName("Splash Potion of Healing II");
            meta.addCustomEffect(new PotionEffect(PotionEffectType.HEAL, 1, 1), true);
            potion.setItemMeta(meta);
        }

        return potion;
    }

    private static void enchant(ItemStack item, int sharpness, int unbreaking) {
        if (sharpness > 0) {
            item.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, sharpness);
        }

        if (unbreaking > 0) {
            item.addUnsafeEnchantment(Enchantment.DURABILITY, unbreaking);
        }
    }

    private static ItemStack[] armor(MatchSettings.ArmorTier tier, boolean redTeam) {
        if (tier == MatchSettings.ArmorTier.LEATHER) {
            Color color = redTeam
                    ? Color.fromRGB(255, 0, 0)
                    : Color.fromRGB(0, 0, 255);

            return new ItemStack[]{
                    dyed(Material.LEATHER_BOOTS, color),
                    dyed(Material.LEATHER_LEGGINGS, color),
                    dyed(Material.LEATHER_CHESTPLATE, color),
                    dyed(Material.LEATHER_HELMET, color)
            };
        }

        String prefix = tier == MatchSettings.ArmorTier.CHAIN
                ? "CHAINMAIL"
                : tier.name();

        return new ItemStack[]{
                new ItemStack(Material.valueOf(prefix + "_BOOTS")),
                new ItemStack(Material.valueOf(prefix + "_LEGGINGS")),
                new ItemStack(Material.valueOf(prefix + "_CHESTPLATE")),
                new ItemStack(Material.valueOf(prefix + "_HELMET"))
        };
    }

    private static ItemStack dyed(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        ItemMeta rawMeta = item.getItemMeta();

        if (rawMeta instanceof LeatherArmorMeta) {
            LeatherArmorMeta meta = (LeatherArmorMeta) rawMeta;
            meta.setColor(color);
            item.setItemMeta(meta);
        }

        return item;
    }

    private static String weaponMaterial(MatchSettings.ArmorTier tier) {
        if (tier == MatchSettings.ArmorTier.LEATHER) {
            return "WOOD";
        }

        if (tier == MatchSettings.ArmorTier.CHAIN) {
            return "STONE";
        }

        return tier.name();
    }
}
