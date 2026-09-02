package com.starrypvp.util;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class SoundUtil {
    private static final Map<String, Sound> CACHE = new HashMap<String, Sound>();

    private SoundUtil() {
    }

    private static Sound resolve(String key, String[] names) {
        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }

        Sound found = null;

        for (String name : names) {
            try {
                found = Sound.valueOf(name);
                break;
            } catch (IllegalArgumentException ignored) {
                found = null;
            }
        }

        CACHE.put(key, found);
        return found;
    }

    private static void play(Player player, String key, String[] names, float volume, float pitch) {
        if (player == null) {
            return;
        }

        Sound sound = resolve(key, names);

        if (sound == null) {
            return;
        }

        Location location = player.getLocation();
        player.playSound(location, sound, volume, pitch);
    }

    public static void menuOpen(Player player) {
        play(player, "open", new String[]{"BLOCK_CHEST_OPEN", "CHEST_OPEN"}, 0.5F, 1.4F);
    }

    public static void menuClose(Player player) {
        play(player, "close", new String[]{"BLOCK_CHEST_CLOSE", "CHEST_CLOSE"}, 0.4F, 1.4F);
    }

    public static void click(Player player) {
        play(player, "click", new String[]{"UI_BUTTON_CLICK", "CLICK"}, 0.5F, 1.7F);
    }

    public static void hover(Player player) {
        play(player, "pling", new String[]{"BLOCK_NOTE_PLING", "NOTE_PIANO"}, 0.4F, 1.8F);
    }

    public static void select(Player player) {
        play(player, "harp", new String[]{"BLOCK_NOTE_HARP", "NOTE_PIANO"}, 0.6F, 1.2F);
    }

    public static void deny(Player player) {
        play(player, "deny", new String[]{"BLOCK_NOTE_BASS", "NOTE_BASS"}, 0.6F, 0.7F);
    }

    public static void queueJoin(Player player) {
        play(player, "orb", new String[]{"ENTITY_EXPERIENCE_ORB_PICKUP", "ORB_PICKUP"}, 0.7F, 1.5F);
    }

    public static void queueLeave(Player player) {
        play(player, "fizz", new String[]{"BLOCK_FIRE_EXTINGUISH", "FIZZ"}, 0.5F, 1.4F);
    }

    public static void matchFound(Player player) {
        play(player, "launch", new String[]{"ENTITY_FIREWORK_LAUNCH", "FIREWORK_LAUNCH"}, 0.8F, 1.2F);
    }

    public static void countdownTick(Player player) {
        play(player, "orb", new String[]{"ENTITY_EXPERIENCE_ORB_PICKUP", "ORB_PICKUP"}, 1.0F, 1.6F);
    }

    public static void countdownGo(Player player) {
        play(player, "levelup", new String[]{"ENTITY_PLAYER_LEVELUP", "LEVEL_UP"}, 1.0F, 1.0F);
    }

    public static void victory(Player player) {
        play(player, "levelup", new String[]{"ENTITY_PLAYER_LEVELUP", "LEVEL_UP"}, 1.0F, 1.3F);
    }

    public static void defeat(Player player) {
        play(player, "bassattack", new String[]{"ENTITY_WITHER_SPAWN", "WITHER_SPAWN"}, 0.3F, 1.6F);
    }
}
