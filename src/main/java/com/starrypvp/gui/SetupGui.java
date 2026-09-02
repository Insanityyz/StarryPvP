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
        session.getSettings().setBestOf(plugin.getQueueManager().getBestOf(player));
        session.getSettings().setArenaName(plugin.getQueueManager().getPreferredArena(player));
        sessions.put(player.getUniqueId(), session);
        render(player, session);
        com.starrypvp.util.SoundUtil.menuOpen(player);
    }

    public void openReply(Player player, Player challenger, ChallengeType type, MatchSettings base) {
        Session session = new Session(challenger.getUniqueId(), type);
        session.getSettings().setBestOf(base.getBestOf());
        session.getSettings().setArenaName(base.getArenaName());
        sessions.put(player.getUniqueId(), session);
        render(player, session);
        com.starrypvp.util.SoundUtil.menuOpen(player);
    }

    private void render(Player player, Session session) {
        MatchSettings settings = session.getSettings();
        Inventory inventory = plugin.getServer().createInventory(null, 45, "StarryPvP Kit Setup");

        inventory.setItem(4, ItemUtil.named(
                Material.CHEST,
                ChatColor.LIGHT_PURPLE + "Kit Configuration",
                ChatColor.GRAY + settings.summary(),
                ChatColor.GRAY + "Configure equipment, healing,",
                ChatColor.GRAY + "enchantments, and combat rules."
        ));

        inventory.setItem(10, ItemUtil.named(
                Material.DIAMOND_CHESTPLATE,
                ChatColor.AQUA + "Armor Kit: " + display(settings.getArmorTier().name()),
                ChatColor.GRAY + armorDescription(settings.getArmorTier()),
                ChatColor.YELLOW + "Click to change kit"
        ));

        inventory.setItem(11, ItemUtil.named(
                Material.DIAMOND_SWORD,
                ChatColor.AQUA + "Weapon Kit: " + display(settings.getWeaponMode().name()),
                ChatColor.GRAY + weaponDescription(settings),
                ChatColor.YELLOW + "Click to change weapons"
        ));

        inventory.setItem(12, ItemUtil.named(
                Material.GOLDEN_APPLE,
                ChatColor.AQUA + "Healing Kit: " + display(settings.getHealingMode().name()),
                ChatColor.GRAY + healingDescription(settings.getHealingMode()),
                ChatColor.YELLOW + "Click to change healing"
        ));

        inventory.setItem(13, ItemUtil.named(
                Material.WOOL,
                ChatColor.AQUA + "Building",
                toggle(settings.isBuilding()),
                ChatColor.GRAY + "Enabled gives two stacks",
                ChatColor.GRAY + "of team-colored wool.",
                ChatColor.YELLOW + "Click to toggle"
        ));

        inventory.setItem(14, ItemUtil.named(
                Material.BOW,
                ChatColor.AQUA + "Bow and Arrows",
                toggle(settings.isBow()),
                ChatColor.GRAY + "Adds a bow and 16 arrows.",
                ChatColor.YELLOW + "Click to toggle"
        ));

        inventory.setItem(23, ItemUtil.named(
                Material.SHEARS,
                ChatColor.AQUA + "Shears",
                toggle(settings.isShears()),
                ChatColor.GRAY + "Breaks wool almost instantly.",
                ChatColor.YELLOW + "Click to toggle"
        ));

        inventory.setItem(24, ItemUtil.named(
                Material.SKULL_ITEM,
                ChatColor.LIGHT_PURPLE + "Opponent Picks Kit",
                toggle(settings.isOpponentPicksKit()),
                ChatColor.GRAY + "Sends this menu to them instead,",
                ChatColor.GRAY + "and they choose the loadout.",
                ChatColor.YELLOW + "Click to toggle"
        ));

        inventory.setItem(25, ItemUtil.named(
                Material.BOOK,
                ChatColor.LIGHT_PURPLE + "Best of " + settings.getBestOf(),
                ChatColor.GRAY + "Cycles 1, 3, and 5.",
                ChatColor.GRAY + "Only the final round counts for stats.",
                ChatColor.YELLOW + "Click to change"
        ));

        inventory.setItem(32, ItemUtil.named(
                Material.MAP,
                ChatColor.LIGHT_PURPLE + "Map: " +
                        (settings.getArenaName() == null ? "Random" : settings.getArenaName()),
                ChatColor.GRAY + "Cycles every ready arena.",
                ChatColor.YELLOW + "Click to change"
        ));

        inventory.setItem(15, ItemUtil.named(
                Material.WOOD_SWORD,
                ChatColor.GOLD + "Legacy 1.8 Combat",
                toggle(settings.isLegacyCombat()),
                ChatColor.GRAY + "Removes the 1.9 attack cooldown",
                ChatColor.GRAY + "on servers where it exists.",
                ChatColor.YELLOW + "Click to toggle"
        ));

        inventory.setItem(16, ItemUtil.named(
                Material.SLIME_BALL,
                ChatColor.GOLD + "Custom Knockback",
                toggle(settings.isCustomKnockback()),
                ChatColor.GRAY + "Uses the configured horizontal",
                ChatColor.GRAY + "and vertical multipliers.",
                ChatColor.YELLOW + "Click to toggle"
        ));

        inventory.setItem(19, ItemUtil.named(
                Material.ENCHANTED_BOOK,
                ChatColor.LIGHT_PURPLE + "Sword Sharpness: " + settings.getSwordSharpness(),
                ChatColor.GRAY + "Shared Sharpness remaining: " +
                        (3 - settings.getSwordSharpness() - settings.getAxeSharpness()),
                ChatColor.YELLOW + "Click to allocate points"
        ));

        inventory.setItem(20, ItemUtil.named(
                Material.ENCHANTED_BOOK,
                ChatColor.LIGHT_PURPLE + "Axe Sharpness: " + settings.getAxeSharpness(),
                ChatColor.GRAY + "Shared Sharpness remaining: " +
                        (3 - settings.getSwordSharpness() - settings.getAxeSharpness()),
                ChatColor.YELLOW + "Click to allocate points"
        ));

        inventory.setItem(21, ItemUtil.named(
                Material.ANVIL,
                ChatColor.YELLOW + "Sword Unbreaking: " + settings.getSwordUnbreaking(),
                ChatColor.GRAY + "Shared Unbreaking remaining: " +
                        (3 - settings.getSwordUnbreaking() - settings.getAxeUnbreaking()),
                ChatColor.YELLOW + "Click to allocate points"
        ));

        inventory.setItem(22, ItemUtil.named(
                Material.ANVIL,
                ChatColor.YELLOW + "Axe Unbreaking: " + settings.getAxeUnbreaking(),
                ChatColor.GRAY + "Shared Unbreaking remaining: " +
                        (3 - settings.getSwordUnbreaking() - settings.getAxeUnbreaking()),
                ChatColor.YELLOW + "Click to allocate points"
        ));

        inventory.setItem(31, ItemUtil.named(
                Material.PAPER,
                ChatColor.WHITE + "Complete Kit",
                ChatColor.GRAY + settings.summary(),
                ChatColor.GRAY + armorDescription(settings.getArmorTier()),
                ChatColor.GRAY + weaponDescription(settings),
                ChatColor.GRAY + healingDescription(settings.getHealingMode()),
                ChatColor.GRAY + "Building: " + yesNo(settings.isBuilding()),
                ChatColor.GRAY + "Legacy combat: " + yesNo(settings.isLegacyCombat()),
                ChatColor.GRAY + "Custom knockback: " + yesNo(settings.isCustomKnockback())
        ));

        inventory.setItem(40, ItemUtil.named(
                Material.EMERALD_BLOCK,
                ChatColor.GREEN + "Send Challenge",
                ChatColor.GRAY + settings.summary(),
                ChatColor.YELLOW + "Click to submit this kit"
        ));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!"StarryPvP Kit Setup".equals(event.getInventory().getTitle())) {
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
        com.starrypvp.util.SoundUtil.click(player);

        if (slot == 14) {
            session.getSettings().setBow(!session.getSettings().isBow());
        } else if (slot == 23) {
            session.getSettings().setShears(!session.getSettings().isShears());
        } else if (slot == 24) {
            session.getSettings().setOpponentPicksKit(!session.getSettings().isOpponentPicksKit());
        } else if (slot == 25) {
            int current = session.getSettings().getBestOf();
            session.getSettings().setBestOf(current == 1 ? 3 : current == 3 ? 5 : 1);
        } else if (slot == 32) {
            cycleArena(session);
        } else if (slot == 10) {
            session.getSettings().setArmorTier(session.getSettings().getArmorTier().next());
        } else if (slot == 11) {
            session.getSettings().setWeaponMode(session.getSettings().getWeaponMode().next());
        } else if (slot == 12) {
            session.getSettings().setHealingMode(session.getSettings().getHealingMode().next());
        } else if (slot == 13) {
            session.getSettings().setBuilding(!session.getSettings().isBuilding());
        } else if (slot == 15) {
            session.getSettings().setLegacyCombat(!session.getSettings().isLegacyCombat());
        } else if (slot == 16) {
            session.getSettings().setCustomKnockback(!session.getSettings().isCustomKnockback());
        } else if (slot == 19) {
            session.getSettings().cycleSwordSharpness();
        } else if (slot == 20) {
            session.getSettings().cycleAxeSharpness();
        } else if (slot == 21) {
            session.getSettings().cycleSwordUnbreaking();
        } else if (slot == 22) {
            session.getSettings().cycleAxeUnbreaking();
        } else if (slot == 40) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();

            try {
                plugin.getMatchManager().dispatchFromGui(player, session);
            } catch (Throwable throwable) {
                player.sendMessage(plugin.color("&cThe challenge could not be created. Your items were not changed."));
                plugin.getLogger().severe("Could not dispatch challenge for " + player.getName() + ": " + throwable.getMessage());
            }

            return;
        }

        render(player, session);
    }

    private void cycleArena(Session session) {
        com.starrypvp.arena.Arena.Mode mode = session.getType() == ChallengeType.TEAM
                ? com.starrypvp.arena.Arena.Mode.TEAM
                : com.starrypvp.arena.Arena.Mode.DUEL;

        java.util.List<String> names = new java.util.ArrayList<String>();

        for (com.starrypvp.arena.Arena arena : plugin.getArenaManager().all()) {
            if (arena.getMode() == mode && arena.isReady()) {
                names.add(arena.getName());
            }
        }

        if (names.isEmpty()) {
            session.getSettings().setArenaName(null);
            return;
        }

        String current = session.getSettings().getArenaName();

        if (current == null) {
            session.getSettings().setArenaName(names.get(0));
            return;
        }

        int index = names.indexOf(current);

        if (index < 0 || index == names.size() - 1) {
            session.getSettings().setArenaName(null);
        } else {
            session.getSettings().setArenaName(names.get(index + 1));
        }
    }

    private String armorDescription(MatchSettings.ArmorTier tier) {
        if (tier == MatchSettings.ArmorTier.LEATHER) {
            return "Dyed leather armor with wooden weapons";
        }
        if (tier == MatchSettings.ArmorTier.CHAIN) {
            return "Chainmail armor with stone weapons";
        }
        if (tier == MatchSettings.ArmorTier.GOLD) {
            return "Gold armor with gold weapons";
        }
        if (tier == MatchSettings.ArmorTier.IRON) {
            return "Iron armor with iron weapons";
        }
        return "Diamond armor with diamond weapons";
    }

    private String weaponDescription(MatchSettings settings) {
        if (settings.getWeaponMode() == MatchSettings.WeaponMode.SWORDS) {
            return "Includes one sword";
        }
        if (settings.getWeaponMode() == MatchSettings.WeaponMode.AXES) {
            return "Includes one axe";
        }
        return "Includes one sword and one axe";
    }

    private String healingDescription(MatchSettings.HealingMode mode) {
        if (mode == MatchSettings.HealingMode.GAPPLE) {
            return "Includes one golden apple";
        }
        if (mode == MatchSettings.HealingMode.POTIONS) {
            return "Includes three splash Instant Health II potions";
        }
        return "Includes no healing items";
    }

    private String toggle(boolean value) {
        return value ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled";
    }

    private String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private String display(String value) {
        String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
