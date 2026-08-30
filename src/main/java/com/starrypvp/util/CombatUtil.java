package com.starrypvp.util;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatUtil {
    private static final Map<UUID, Double> ORIGINAL_ATTACK_SPEED = new ConcurrentHashMap<UUID, Double>();

    private CombatUtil() {
    }

    public static void enableLegacyAttackSpeed(Player player) {
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Object attackSpeed = Enum.valueOf((Class<Enum>) attributeClass.asSubclass(Enum.class), "GENERIC_ATTACK_SPEED");
            Method getAttribute = player.getClass().getMethod("getAttribute", attributeClass);
            Object instance = getAttribute.invoke(player, attackSpeed);

            if (instance == null) {
                return;
            }

            Method getBaseValue = instance.getClass().getMethod("getBaseValue");
            Method setBaseValue = instance.getClass().getMethod("setBaseValue", double.class);
            double original = ((Number) getBaseValue.invoke(instance)).doubleValue();

            if (!ORIGINAL_ATTACK_SPEED.containsKey(player.getUniqueId())) {
                ORIGINAL_ATTACK_SPEED.put(player.getUniqueId(), original);
            }

            setBaseValue.invoke(instance, 1024.0D);
        } catch (Throwable ignored) {
        }
    }

    public static void restoreAttackSpeed(Player player) {
        Double original = ORIGINAL_ATTACK_SPEED.remove(player.getUniqueId());
        if (original == null) {
            return;
        }

        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Object attackSpeed = Enum.valueOf((Class<Enum>) attributeClass.asSubclass(Enum.class), "GENERIC_ATTACK_SPEED");
            Method getAttribute = player.getClass().getMethod("getAttribute", attributeClass);
            Object instance = getAttribute.invoke(player, attackSpeed);

            if (instance != null) {
                Method setBaseValue = instance.getClass().getMethod("setBaseValue", double.class);
                setBaseValue.invoke(instance, original.doubleValue());
            }
        } catch (Throwable ignored) {
        }
    }
}
