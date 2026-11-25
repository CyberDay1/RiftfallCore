package com.cyberday1.riftfall.compat;

import net.minecraft.world.item.ItemStack;

/**
 * Simply Swords' throwable spears (iron, gold, diamond, netherite, sword on a stick) always come back, at Loyalty III
 * or the stack's own higher Loyalty. Read by MixinSimplySwordsSpearLoyalty; nothing links a Simply Swords class.
 */
public final class SpearLoyalty {
    public static final int LEVEL = 3;
    public static final String TOOLTIP = "tooltip.riftfall.spear_returns";
    static final String THROWABLE = "net.sweenus.simplyswords.item.SimplySwordsThrowableItem";

    private SpearLoyalty() {}

    public static boolean applies(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (Class<?> type = stack.getItem().getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(THROWABLE)) return true;
        }
        return false;
    }

    /** The return strength a throw gets: the stack's own Loyalty when higher. */
    public static int level(int own) {
        return Math.max(own, LEVEL);
    }
}
