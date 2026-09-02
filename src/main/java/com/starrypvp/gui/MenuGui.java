package com.starrypvp.gui;

import com.starrypvp.StarryPvP;
import com.starrypvp.arena.Arena;
import com.starrypvp.queue.QueueManager;
import com.starrypvp.util.ItemUtil;
import com.starrypvp.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuGui implements Listener {
    private static final String MODE_TITLE = ChatColor.DARK_PURPLE + "StarryPvP Modes";
    private static final String PLAYER_TITLE = ChatColor.DARK_PURPLE + "Choose Opponent";
    private static final String OPTION_TITLE = ChatColor.DARK_PURPLE + "Match Options";
    private static final String ARENA_TITLE = ChatColor.DARK_PURPLE + "Choose Map";

    private final StarryPvP plugin;
    private final Map<UUID, QueueManager.Mode> viewing = new ConcurrentHashMap<UUID, QueueManager.Mode>();

    public MenuGui(StarryPvP plugin) {
        this.plugin = plugin;
    }

    public void openModes(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, MODE_TITLE);
        QueueManager queue = plugin.getQueueManager();

        inventory.setItem(10, ItemUtil.named(Material.DIAMOND_SWORD,
                ChatColor.AQUA + "Duels",
                ChatColor.GRAY + "Classic 1v1.",
                ChatColor.GRAY + "Waiting: " + ChatColor.WHITE + queue.waiting(QueueManager.Mode.DUEL),
                ChatColor.YELLOW + "Click to open"));

        inventory.setItem(11, ItemUtil.named(Material.IRON_CHESTPLATE,
                ChatColor.AQUA + "2v2",
                ChatColor.GRAY + "Party of 2 required.",
                ChatColor.GRAY + "Waiting: " + ChatColor.WHITE + queue.waiting(QueueManager.Mode.TEAM2),
                ChatColor.YELLOW + "Click to open"));

        inventory.setItem(12, ItemUtil.named(Material.DIAMOND_CHESTPLATE,
                ChatColor.AQUA + "3v3",
                ChatColor.GRAY + "Party of 3 required.",
                ChatColor.GRAY + "Waiting: " + ChatColor.WHITE + queue.waiting(QueueManager.Mode.TEAM3),
                ChatColor.YELLOW + "Click to open"));

        inventory.setItem(13, ItemUtil.named(Material.GOLD_CHESTPLATE,
                ChatColor.AQUA + "5v5",
                ChatColor.GRAY + "Party of 5 required.",
                ChatColor.GRAY + "Waiting: " + ChatColor.WHITE + queue.waiting(QueueManager.Mode.TEAM5),
                ChatColor.YELLOW + "Click to open"));

        inventory.setItem(14, ItemUtil.named(Material.GOLDEN_APPLE,
                ChatColor.AQUA + "Practice / FFA",
                ChatColor.GRAY + "Free for all warmup arena.",
                ChatColor.GRAY + "Nothing here affects your stats.",
                ChatColor.GRAY + "Leave any time with " + ChatColor.WHITE + "/pvp leave",
                ChatColor.YELLOW + "Click to join"));

        inventory.setItem(16, ItemUtil.named(Material.PAPER,
                ChatColor.AQUA + "Match Options",
                ChatColor.GRAY + "Best of: " + ChatColor.WHITE + queue.getBestOf(player),
                ChatColor.GRAY + "Map: " + ChatColor.WHITE + mapName(player),
                ChatColor.YELLOW + "Click to change"));

        if (queue.isQueued(player)) {
            inventory.setItem(22, ItemUtil.named(Material.BARRIER,
                    ChatColor.RED + "Cancel Queue",
                    ChatColor.GRAY + "Currently in: " + ChatColor.WHITE + queue.getMode(player).getLabel(),
                    ChatColor.YELLOW + "Click to cancel"));
        }

        player.openInventory(inventory);
        SoundUtil.menuOpen(player);
    }

    private String mapName(Player player) {
        String arena = plugin.getQueueManager().getPreferredArena(player);
        return arena == null ? "Random" : arena;
    }

    public void openPlayers(Player player, QueueManager.Mode mode) {
        viewing.put(player.getUniqueId(), mode);

        Inventory inventory = Bukkit.createInventory(null, 54, PLAYER_TITLE);

        inventory.setItem(4, ItemUtil.named(Material.NETHER_STAR,
                ChatColor.LIGHT_PURPLE + "Quick Match",
                ChatColor.GRAY + "Mode: " + ChatColor.WHITE + mode.getLabel(),
                ChatColor.GRAY + "Waits in a room until someone joins.",
                ChatColor.GRAY + "You can walk around while waiting.",
                ChatColor.YELLOW + "Click to queue"));

        int slot = 9;

        for (Player online : available(player)) {
            if (slot > 52) {
                break;
            }

            inventory.setItem(slot++, head(online));
        }

        if (slot == 9) {
            inventory.setItem(22, ItemUtil.named(Material.REDSTONE,
                    ChatColor.RED + "Nobody is free right now",
                    ChatColor.GRAY + "Use Quick Match and wait instead."));
        }

        inventory.setItem(49, ItemUtil.named(Material.ARROW,
                ChatColor.YELLOW + "Back",
                ChatColor.GRAY + "Return to the mode list."));

        player.openInventory(inventory);
        SoundUtil.menuOpen(player);
    }

    private List<Player> available(Player viewer) {
        List<Player> players = new ArrayList<Player>();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }

            if (plugin.getQueueManager().isBusy(online)) {
                continue;
            }

            players.add(online);
        }

        return players;
    }

    private ItemStack head(Player target) {
        ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        if (meta != null) {
            meta.setOwner(target.getName());
            meta.setDisplayName(ChatColor.AQUA + target.getName());
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + "Wins: " + ChatColor.WHITE
                    + plugin.getDataManager().get(target).getWins());
            lore.add(ChatColor.GRAY + "Losses: " + ChatColor.WHITE
                    + plugin.getDataManager().get(target).getLosses());
            lore.add(ChatColor.YELLOW + "Click to challenge");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    public void openOptions(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, OPTION_TITLE);
        int series = plugin.getQueueManager().getBestOf(player);

        inventory.setItem(11, ItemUtil.named(Material.WOOD_SWORD,
                ChatColor.AQUA + "Best of 1",
                series == 1 ? ChatColor.GREEN + "Selected" : ChatColor.GRAY + "Click to select"));

        inventory.setItem(13, ItemUtil.named(Material.IRON_SWORD,
                ChatColor.AQUA + "Best of 3",
                series == 3 ? ChatColor.GREEN + "Selected" : ChatColor.GRAY + "Click to select"));

        inventory.setItem(15, ItemUtil.named(Material.DIAMOND_SWORD,
                ChatColor.AQUA + "Best of 5",
                series == 5 ? ChatColor.GREEN + "Selected" : ChatColor.GRAY + "Click to select"));

        inventory.setItem(22, ItemUtil.named(Material.MAP,
                ChatColor.AQUA + "Map: " + mapName(player),
                ChatColor.GRAY + "Click to choose a map."));

        player.openInventory(inventory);
        SoundUtil.menuOpen(player);
    }

    public void openArenas(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, ARENA_TITLE);

        inventory.setItem(4, ItemUtil.named(Material.ENDER_PEARL,
                ChatColor.LIGHT_PURPLE + "Random Map",
                ChatColor.GRAY + "Let the plugin pick.",
                ChatColor.YELLOW + "Click to select"));

        int slot = 9;

        for (Arena arena : plugin.getArenaManager().all()) {
            if (slot > 53) {
                break;
            }

            inventory.setItem(slot++, ItemUtil.named(Material.MAP,
                    ChatColor.AQUA + arena.getName(),
                    ChatColor.GRAY + "Mode: " + ChatColor.WHITE + arena.getMode().name(),
                    ChatColor.GRAY + "Ready: " + ChatColor.WHITE + arena.isReady(),
                    ChatColor.YELLOW + "Click to select"));
        }

        player.openInventory(inventory);
        SoundUtil.menuOpen(player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory() == null || event.getCurrentItem() == null) {
            return;
        }

        String title = event.getInventory().getTitle();

        if (title == null) {
            return;
        }

        boolean mine = title.equals(MODE_TITLE) || title.equals(PLAYER_TITLE)
                || title.equals(OPTION_TITLE) || title.equals(ARENA_TITLE);

        if (!mine) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();

        if (item.getType() == Material.AIR || !item.hasItemMeta()) {
            return;
        }

        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());

        if (name == null) {
            return;
        }

        SoundUtil.click(player);

        if (title.equals(MODE_TITLE)) {
            handleModes(player, name);
        } else if (title.equals(PLAYER_TITLE)) {
            handlePlayers(player, name);
        } else if (title.equals(OPTION_TITLE)) {
            handleOptions(player, name);
        } else if (title.equals(ARENA_TITLE)) {
            handleArenas(player, name);
        }
    }

    private void handleModes(Player player, String name) {
        if (name.equals("Duels")) {
            openPlayers(player, QueueManager.Mode.DUEL);
        } else if (name.equals("2v2")) {
            openPlayers(player, QueueManager.Mode.TEAM2);
        } else if (name.equals("3v3")) {
            openPlayers(player, QueueManager.Mode.TEAM3);
        } else if (name.equals("5v5")) {
            openPlayers(player, QueueManager.Mode.TEAM5);
        } else if (name.equals("Practice / FFA")) {
            player.closeInventory();
            SoundUtil.menuClose(player);
            plugin.getQueueManager().join(player, QueueManager.Mode.PRACTICE);
        } else if (name.equals("Match Options")) {
            openOptions(player);
        } else if (name.equals("Cancel Queue")) {
            player.closeInventory();
            SoundUtil.menuClose(player);
            plugin.getQueueManager().cancel(player, false);
        }
    }

    private void handlePlayers(Player player, String name) {
        QueueManager.Mode mode = viewing.get(player.getUniqueId());

        if (mode == null) {
            openModes(player);
            return;
        }

        if (name.equals("Back")) {
            openModes(player);
            return;
        }

        if (name.equals("Quick Match")) {
            player.closeInventory();
            SoundUtil.menuClose(player);
            plugin.getQueueManager().join(player, mode);
            return;
        }

        Player target = Bukkit.getPlayerExact(name);

        if (target == null || plugin.getQueueManager().isBusy(target)) {
            player.sendMessage(plugin.color("&cThat player is no longer available."));
            SoundUtil.deny(player);
            openPlayers(player, mode);
            return;
        }

        player.closeInventory();
        SoundUtil.menuClose(player);
        SoundUtil.select(player);

        SetupGui.ChallengeType type = mode.isTeam()
                ? SetupGui.ChallengeType.TEAM
                : SetupGui.ChallengeType.DUEL;

        plugin.getSetupGui().open(player, target, type);
    }

    private void handleOptions(Player player, String name) {
        if (name.equals("Best of 1")) {
            plugin.getQueueManager().setBestOf(player, 1);
            openOptions(player);
        } else if (name.equals("Best of 3")) {
            plugin.getQueueManager().setBestOf(player, 3);
            openOptions(player);
        } else if (name.equals("Best of 5")) {
            plugin.getQueueManager().setBestOf(player, 5);
            openOptions(player);
        } else if (name.startsWith("Map: ")) {
            openArenas(player);
        }
    }

    private void handleArenas(Player player, String name) {
        if (name.equals("Random Map")) {
            plugin.getQueueManager().setPreferredArena(player, null);
        } else {
            plugin.getQueueManager().setPreferredArena(player, name);
        }

        SoundUtil.select(player);
        openOptions(player);
    }
}
