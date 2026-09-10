package com.starrypvp.util;

import org.bukkit.ChatColor;
import org.bukkit.Color;

/**
 * The ten FFA event colours: red, orange, yellow, lime, forest, cyan,
 * teal, blue, pink, purple. Greys, white, black, dark red and dark blue
 * are deliberately excluded so no two players ever look alike.
 */
public enum EventColor {
    RED(ChatColor.RED, Color.fromRGB(255, 85, 85), (short) 14, "Red"),
    ORANGE(ChatColor.GOLD, Color.fromRGB(255, 170, 0), (short) 1, "Orange"),
    YELLOW(ChatColor.YELLOW, Color.fromRGB(255, 255, 85), (short) 4, "Yellow"),
    LIME(ChatColor.GREEN, Color.fromRGB(85, 255, 85), (short) 5, "Lime"),
    FOREST(ChatColor.DARK_GREEN, Color.fromRGB(0, 170, 0), (short) 13, "Forest"),
    CYAN(ChatColor.AQUA, Color.fromRGB(85, 255, 255), (short) 3, "Cyan"),
    TEAL(ChatColor.DARK_AQUA, Color.fromRGB(0, 170, 170), (short) 9, "Teal"),
    BLUE(ChatColor.BLUE, Color.fromRGB(85, 85, 255), (short) 11, "Blue"),
    PINK(ChatColor.LIGHT_PURPLE, Color.fromRGB(255, 85, 255), (short) 6, "Pink"),
    PURPLE(ChatColor.DARK_PURPLE, Color.fromRGB(170, 0, 170), (short) 10, "Purple");

    private final ChatColor chat;
    private final Color dye;
    private final short wool;
    private final String label;

    EventColor(ChatColor chat, Color dye, short wool, String label) {
        this.chat = chat;
        this.dye = dye;
        this.wool = wool;
        this.label = label;
    }

    public ChatColor getChat() {
        return chat;
    }

    public Color getDye() {
        return dye;
    }

    public short getWool() {
        return wool;
    }

    public String getLabel() {
        return label;
    }

    public String coloured() {
        return chat + label;
    }

    /**
     * Picks {@code count} colours evenly spaced around the palette, so a
     * three player event gets red / lime / blue rather than three reds.
     */
    public static EventColor[] spread(int count) {
        EventColor[] all = values();

        if (count <= 0) {
            return new EventColor[0];
        }

        if (count >= all.length) {
            return all.clone();
        }

        EventColor[] chosen = new EventColor[count];

        for (int index = 0; index < count; index++) {
            int position = (int) Math.round((double) index * all.length / (double) count);
            chosen[index] = all[position % all.length];
        }

        return chosen;
    }
}
