package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.block.barrel.BarrelTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Only the barrel's weighted gear entry may award accessories; external chest injections cannot add extras. */
public final class BarrelAccessoryLoot {
    private static final Set<ResourceLocation> TABLES = Stream.of(BarrelTier.ALL)
            .flatMap(t -> Stream.of(t.lootTable().location(), t.shardTable().location()))
            .collect(Collectors.toUnmodifiableSet());
    private static final ThreadLocal<Map<Item, Integer>> AUTHORED = new ThreadLocal<>();

    private BarrelAccessoryLoot() {}

    /** A nested table has its own allowance; exceptions must never leak permission into another roll. */
    public static <T extends List<ItemStack>> T roll(ResourceLocation table, Supplier<T> draw) {
        Map<Item, Integer> previous = AUTHORED.get();
        Map<Item, Integer> allowed = table != null && TABLES.contains(table) ? new HashMap<>() : null;
        if (allowed == null) AUTHORED.remove(); else AUTHORED.set(allowed);
        try {
            T loot = draw.get();
            if (allowed != null) {
                loot.removeIf(stack -> {
                    if (stack.isEmpty() || GearPool.classify(stack.getItem()) != GearPool.Slot.CURIO) return false;
                    int keep = Math.min(stack.getCount(), allowed.getOrDefault(stack.getItem(), 0));
                    if (keep == 0) return true;
                    allowed.computeIfPresent(stack.getItem(), (item, count) -> count - keep);
                    if (keep < stack.getCount()) stack.setCount(keep);
                    return false;
                });
            }
            return loot;
        } finally {
            if (previous == null) AUTHORED.remove(); else AUTHORED.set(previous);
        }
    }

    /** Called at the source of our gear draw, before affix processing can copy or rebuild the stack. */
    public static void authored(ItemStack stack) {
        var allowed = AUTHORED.get();
        if (allowed != null && !stack.isEmpty() && GearPool.classify(stack.getItem()) == GearPool.Slot.CURIO)
            allowed.merge(stack.getItem(), stack.getCount(), Integer::sum);
    }
}
