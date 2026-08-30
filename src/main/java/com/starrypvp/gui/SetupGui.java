package com.starrypvp.gui;

import com.starrypvp.StarryPvP;
import com.starrypvp.match.MatchSettings;
import com.starrypvp.util.ItemUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SetupGui implements Listener {
    public enum ChallengeType {
        DUEL,
        TEAM,
        CUSTOM_FFA
    }

    public static final class Session {
        private final UUID target;
        private final ChallengeType type;
        private final MatchSettings settings;

        private Session(UUID target, ChallengeType type) {
            this.target = target;
            this.type = type;
            this.settings = new MatchSettings();
        }

        public UUID getTarget() {
            return target;
        }

        public ChallengeType getType() {
            return type;
        }

        public MatchSettings getSettings() {
            return settings;
        }
    }

    private final StarryPvP plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<UUID, Session>();

    public SetupGui(StarryPvP plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Player target, ChallengeType type) {
        Session session = new Session(target == null ? null : target.getUniqueId(), type);
        sessions.put(player.getUniqueId(), session);
        render(player, session);
    }

    private void render(Player player, Session session) {
        MatchSettings settings = session.getSettings();
        Inventory inventory = plugin.getServer().createInventory(null, 27, "StarryPvP Setup");

        inventory.setItem(10, ItemUtil.named(
                Material.DIAMOND_CHESTPLATE,
                ChatColor.AQUA + "Armor: " + settings.getArmorTier().name(),
                ChatColor.GRAY + "Click to cycle armor tiers"
        ));

        inventory.setItem(11, ItemUtil.named(
                Material.DIAMOND_SWORD,
                ChatColor.AQUA + "Weapons: " + settings.getWeaponMode().name(),
                ChatColor.GRAY + "Click to cycle weapon modes"
        ));

        inventory.setItem(12, ItemUtil.named(
                Material.GOLDEN_APPLE,
                ChatColor.AQUA + "Healing: " + settings.getHealingMode().name(),
                ChatColor.GRAY + "None, one gapple, or three Health II potions"
        ));

        inventory.setItem(13, ItemUtil.named(
                Material.WOOL,
                ChatColor.AQUA + "Building: " + (settings.isBuilding() ? "ENABLED" : "DISABLED"),
                ChatColor.GRAY + "Provides two stacks of team-colored wool"
        ));

        inventory.setItem(14, ItemUtil.named(
                Material.ENCHANTED_BOOK,
                ChatColor.LIGHT_PURPLE + "Sword Sharpness: " + settings.getSwordSharpness(),
                ChatColor.GRAY + "Shared remaining: " + (3 - settings.getSwordSharpness() - settings.getAxeSharpness())
        ));

        inventory.setItem(15, ItemUtil.named(
                Material.ENCHANTED_BOOK,
                ChatColor.LIGHT_PURPLE + "Axe Sharpness: " + settings.getAxeSharpness(),
                ChatColor.GRAY + "Shared remaining: " + (3 - settings.getSwordSharpness() - settings.getAxeSharpness())
        ));

        inventory.setItem(16, ItemUtil.named(
                Material.ANVIL,
                ChatColor.YELLOW + "Sword Unbreaking: " + settings.getSwordUnbreaking(),
                ChatColor.GRAY + "Shared remaining: " + (3 - settings.getSwordUnbreaking() - settings.getAxeUnbreaking())
        ));

        inventory.setItem(17, ItemUtil.named(
                Material.ANVIL,
                ChatColor.YELLOW + "Axe Unbreaking: " + settings.getAxeUnbreaking(),
                ChatColor.GRAY + "Shared remaining: " + (3 - settings.getSwordUnbreaking() - settings.getAxeUnbreaking())
        ));

        inventory.setItem(22, ItemUtil.named(
                Material.EMERALD_BLOCK,
                ChatColor.GREEN + "Send Challenge",
                ChatColor.GRAY + settings.summary()
        ));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!"StarryPvP Setup".equals(event.getInventory().getTitle())) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();

        if (slot == 10) {
            session.getSettings().setArmorTier(session.getSettings().getArmorTier().next());
        } else if (slot == 11) {
            session.getSettings().setWeaponMode(session.getSettings().getWeaponMode().next());
        } else if (slot == 12) {
            session.getSettings().setHealingMode(session.getSettings().getHealingMode().next());
        } else if (slot == 13) {
            session.getSettings().setBuilding(!session.getSettings().isBuilding());
        } else if (slot == 14) {
            session.getSettings().cycleSwordSharpness();
        } else if (slot == 15) {
            session.getSettings().cycleAxeSharpness();
        } else if (slot == 16) {
            session.getSettings().cycleSwordUnbreaking();
        } else if (slot == 17) {
            session.getSettings().cycleAxeUnbreaking();
        } else if (slot == 22) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            plugin.getMatchManager().dispatchFromGui(player, session);
            return;
        }

        render(player, session);
    }
}
