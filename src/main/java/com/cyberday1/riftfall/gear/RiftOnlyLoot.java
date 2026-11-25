package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.init.RiftfallTags;
import com.cyberday1.riftfall.rift.RiftLevels;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Loot rolled outside a rift loses every {@code riftfall:rift_only} item. The recipe ban closes the bench; this closes
 * the other mods' loot, TaCZ's own bonus-chest pistol included, so the rifts stay the only source.
 */
public final class RiftOnlyLoot {

    private static final AtomicLong STRIPPED = new AtomicLong();

    private RiftOnlyLoot() {
    }

    public static void strip(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (RiftLevels.isRiftWorld(context.getLevel())) {
            return;
        }
        loot.removeIf(stack -> {
            if (!stack.is(RiftfallTags.RIFT_ONLY)) {
                return false;
            }
            STRIPPED.addAndGet(stack.getCount());
            return true;
        });
    }

    /** Stacks' worth of items removed since the server started. Read by the probe. */
    public static long stripped() {
        return STRIPPED.get();
    }
}
