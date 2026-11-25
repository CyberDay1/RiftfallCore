package com.cyberday1.riftfall.gear;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The Infinity Ham's own ruling (2026-11-24): it is food everywhere, rifts included; Marinade is off everywhere;
 * the Meat Bat is off inside a rift. Relics' ability hooks ask this, per ability.
 */
public final class InfinityHam {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("relics", "infinity_ham");
    static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, ID);

    public static final String REGENERATION = "regeneration";
    public static final String MARINADE = "marinade";
    public static final String MEAT_BAT = "meat_bat";

    private static final AtomicLong MARINADE_REFUSED = new AtomicLong();
    private static final AtomicLong MARINADE_STRIPPED = new AtomicLong();

    private InfinityHam() {
    }

    public static boolean is(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItemHolder().is(KEY);
    }

    /** Relics' "is this ability there at all": its screens drop a disabled one, and every use check fails. */
    public static boolean disabled(String ability) {
        return MARINADE.equals(ability);
    }

    /** The ruling for one ability: false refuses it, null leaves Relics' own answer. */
    public static Boolean answer(String ability, boolean inRift) {
        if (disabled(ability)) return Boolean.FALSE;
        if (inRift && MEAT_BAT.equals(ability)) return Boolean.FALSE;
        return null;
    }

    /** Drops a marinade poured before the ruling, so the tooltip and glint stop promising it. True if one was. */
    public static boolean strip(ItemStack stack) {
        if (stack == null || !stack.has(DataComponents.POTION_CONTENTS) || !is(stack)) return false;
        stack.remove(DataComponents.POTION_CONTENTS);
        MARINADE_STRIPPED.incrementAndGet();
        return true;
    }

    public static void refusedMarinade() {
        MARINADE_REFUSED.incrementAndGet();
    }

    public static long marinadeRefused() {
        return MARINADE_REFUSED.get();
    }

    public static long marinadeStripped() {
        return MARINADE_STRIPPED.get();
    }
}
