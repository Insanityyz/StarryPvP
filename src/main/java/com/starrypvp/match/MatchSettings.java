package com.starrypvp.match;

public final class MatchSettings implements Cloneable {
    public enum ArmorTier {
        LEATHER,
        CHAIN,
        GOLD,
        IRON,
        DIAMOND;

        public ArmorTier next() {
            ArmorTier[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum WeaponMode {
        SWORDS,
        AXES,
        BOTH;

        public WeaponMode next() {
            WeaponMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public boolean hasSword() {
            return this == SWORDS || this == BOTH;
        }

        public boolean hasAxe() {
            return this == AXES || this == BOTH;
        }
    }

    public enum HealingMode {
        NONE,
        GAPPLE,
        POTIONS;

        public HealingMode next() {
            HealingMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private boolean bow;
    private boolean shears;
    private boolean opponentPicksKit;
    private int bestOf = 1;
    private String arenaName;

    public boolean isBow() {
        return bow;
    }

    public void setBow(boolean bow) {
        this.bow = bow;
    }

    public boolean isShears() {
        return shears;
    }

    public void setShears(boolean shears) {
        this.shears = shears;
    }

    public boolean isOpponentPicksKit() {
        return opponentPicksKit;
    }

    public void setOpponentPicksKit(boolean opponentPicksKit) {
        this.opponentPicksKit = opponentPicksKit;
    }

    public int getBestOf() {
        return bestOf;
    }

    public void setBestOf(int bestOf) {
        if (bestOf == 1 || bestOf == 3 || bestOf == 5) {
            this.bestOf = bestOf;
        }
    }

    public String getArenaName() {
        return arenaName;
    }

    public void setArenaName(String arenaName) {
        this.arenaName = arenaName;
    }

    private ArmorTier armorTier = ArmorTier.IRON;
    private WeaponMode weaponMode = WeaponMode.BOTH;
    private HealingMode healingMode = HealingMode.GAPPLE;
    private boolean building = true;
    private boolean legacyCombat = true;
    private boolean customKnockback = true;
    private int swordSharpness;
    private int axeSharpness;
    private int swordUnbreaking;
    private int axeUnbreaking;

    public ArmorTier getArmorTier() {
        return armorTier;
    }

    public void setArmorTier(ArmorTier armorTier) {
        this.armorTier = armorTier;
    }

    public WeaponMode getWeaponMode() {
        return weaponMode;
    }

    public void setWeaponMode(WeaponMode weaponMode) {
        this.weaponMode = weaponMode;

        if (!weaponMode.hasSword()) {
            swordSharpness = 0;
            swordUnbreaking = 0;
        }

        if (!weaponMode.hasAxe()) {
            axeSharpness = 0;
            axeUnbreaking = 0;
        }
    }

    public HealingMode getHealingMode() {
        return healingMode;
    }

    public void setHealingMode(HealingMode healingMode) {
        this.healingMode = healingMode;
    }

    public boolean isBuilding() {
        return building;
    }

    public void setBuilding(boolean building) {
        this.building = building;
    }

    public boolean isLegacyCombat() {
        return legacyCombat;
    }

    public void setLegacyCombat(boolean legacyCombat) {
        this.legacyCombat = legacyCombat;
    }

    public boolean isCustomKnockback() {
        return customKnockback;
    }

    public void setCustomKnockback(boolean customKnockback) {
        this.customKnockback = customKnockback;
    }

    public int getSwordSharpness() {
        return swordSharpness;
    }

    public int getAxeSharpness() {
        return axeSharpness;
    }

    public int getSwordUnbreaking() {
        return swordUnbreaking;
    }

    public int getAxeUnbreaking() {
        return axeUnbreaking;
    }

    public void cycleSwordSharpness() {
        if (!weaponMode.hasSword()) {
            return;
        }

        int next = (swordSharpness + 1) % 4;
        swordSharpness = next + axeSharpness <= 3 ? next : 0;
    }

    public void cycleAxeSharpness() {
        if (!weaponMode.hasAxe()) {
            return;
        }

        int next = (axeSharpness + 1) % 4;
        axeSharpness = next + swordSharpness <= 3 ? next : 0;
    }

    public void cycleSwordUnbreaking() {
        if (!weaponMode.hasSword()) {
            return;
        }

        int next = (swordUnbreaking + 1) % 4;
        swordUnbreaking = next + axeUnbreaking <= 3 ? next : 0;
    }

    public void cycleAxeUnbreaking() {
        if (!weaponMode.hasAxe()) {
            return;
        }

        int next = (axeUnbreaking + 1) % 4;
        axeUnbreaking = next + swordUnbreaking <= 3 ? next : 0;
    }

    public String summary() {
        return display(armorTier.name()) + " Kit | " +
                display(weaponMode.name()) + " Weapons | " +
                healingText();
    }

    private String healingText() {
        if (healingMode == HealingMode.GAPPLE) {
            return "1x Gapple";
        }

        if (healingMode == HealingMode.POTIONS) {
            return "3x Health II";
        }

        return "No Healing";
    }

    private String display(String value) {
        String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public MatchSettings clone() {
        try {
            return (MatchSettings) super.clone();
        } catch (CloneNotSupportedException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
