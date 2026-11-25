package com.cyberday1.riftfall.tool;

import net.minecraft.world.item.ItemStack;
import java.util.function.Supplier;

/** Scoped to one initial personal-share roll; never leaks into mob, boss or subsequent loot. */
public final class BarrelLootBonuses {
    private static final ThreadLocal<Double> RARITY = new ThreadLocal<>();
    private BarrelLootBonuses() {}
    public static boolean active() { return RARITY.get() != null; }
    public static double rarity() { return RARITY.get() == null ? 0 : RARITY.get(); }
    public static <T> T roll(ItemStack tool, Supplier<T> action) {
        var previous = RARITY.get();
        RARITY.set(ToolUpgrades.bonus(tool, ToolStat.RARITY));
        try { return action.get(); }
        finally { if (previous == null) RARITY.remove(); else RARITY.set(previous); }
    }
}
