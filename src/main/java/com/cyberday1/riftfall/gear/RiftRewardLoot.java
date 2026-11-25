package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.block.barrel.BarrelTier;
import com.cyberday1.riftfall.loot.RiftBossLoot;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Modifiers may enhance authored rewards, but may not add items or multiply their quantities. */
public final class RiftRewardLoot {
    private static final Set<ResourceLocation> TABLES = Stream.concat(
            Stream.of(BarrelTier.ALL).flatMap(t -> Stream.of(t.lootTable().location(), t.shardTable().location())),
            Stream.of(RiftBossLoot.ARMS.location())).collect(Collectors.toUnmodifiableSet());

    private RiftRewardLoot() {}

    public static boolean protects(ResourceLocation table) {
        return table != null && (TABLES.contains(table) || (table.getNamespace().equals("riftfall")
                && table.getPath().startsWith("chests/rift_theme/")));
    }

    /** Snapshot counts before modifiers mutate the input; affix conversions may return copied stacks. */
    public static ObjectArrayList<ItemStack> modify(ResourceLocation table, ObjectArrayList<ItemStack> rolled,
            Function<ObjectArrayList<ItemStack>, ObjectArrayList<ItemStack>> modifiers) {
        if (!protects(table)) return modifiers.apply(rolled);
        Map<Item, Integer> budget = new HashMap<>();
        for (var stack : rolled) if (!stack.isEmpty()) budget.merge(stack.getItem(), stack.getCount(), Integer::sum);
        var result = modifiers.apply(rolled);
        result.removeIf(stack -> {
            if (stack.isEmpty()) return true;
            int keep = Math.min(stack.getCount(), budget.getOrDefault(stack.getItem(), 0));
            if (keep == 0) return true;
            budget.put(stack.getItem(), budget.get(stack.getItem()) - keep);
            if (keep < stack.getCount()) stack.setCount(keep);
            return false;
        });
        return result;
    }
}
