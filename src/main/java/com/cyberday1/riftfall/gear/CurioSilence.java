package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.init.RiftfallTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A curio in {@code riftfall:curio_silenced} keeps Riftfall's affixes and nothing of its own: no tick, no own
 * attributes, no worn perks, no abilities. The compat mixins at the Curios bridge and in Relics ask this.
 */
public final class CurioSilence {

    private static final AtomicLong TICKS_REFUSED = new AtomicLong();
    private static final AtomicLong MODIFIERS_WITHHELD = new AtomicLong();
    private static final AtomicLong LOOKUPS_HIDDEN = new AtomicLong();

    private CurioSilence() {
    }

    public static boolean silenced(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(RiftfallTags.CURIO_SILENCED);
    }

    @SuppressWarnings("deprecation")
    public static boolean silenced(Item item) {
        return item != null && item.builtInRegistryHolder().is(RiftfallTags.CURIO_SILENCED);
    }

    /** The bridge refused a tick. Counted for the probe. */
    public static void refusedTick() {
        TICKS_REFUSED.incrementAndGet();
    }

    public static void withheldModifiers() {
        MODIFIERS_WITHHELD.incrementAndGet();
    }

    /** A Relics "is it equipped" lookup was answered "no". */
    public static void hidLookup() {
        LOOKUPS_HIDDEN.incrementAndGet();
    }

    public static long ticksRefused() {
        return TICKS_REFUSED.get();
    }

    public static long modifiersWithheld() {
        return MODIFIERS_WITHHELD.get();
    }

    public static long lookupsHidden() {
        return LOOKUPS_HIDDEN.get();
    }
}
