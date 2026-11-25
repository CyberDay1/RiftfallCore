package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.block.barrel.BarrelTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.neoforged.neoforge.common.ItemAbilities;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Barrel-only exclusions. Tool ownership, crafting and existing saved loot are unchanged. */
public final class BarrelTools {
    public static final TagKey<Item> EXTRA_TOOLS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("riftfall", "barrel_tools"));

    private static final Set<ResourceLocation> TABLES = Stream.of(BarrelTier.ALL)
            .flatMap(tier -> Stream.of(tier.lootTable().location(), tier.shardTable().location()))
            .collect(Collectors.toUnmodifiableSet());

    private BarrelTools() {}

    public static boolean isTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // Explicit combat implements (including the Shifter's axe) retain their class loot slot.
        if (stack.is(com.cyberday1.riftfall.init.RiftfallTags.MELEE_WEAPONS)) return false;
        Item item = stack.getItem();
        // Axes extend DiggerItem even though GearPool files them as melee weapons.
        // Combat hammers/axes implemented as weapons remain eligible unless explicitly tagged.
        return stack.is(EXTRA_TOOLS) || item instanceof DiggerItem || item instanceof ShearsItem
                || item instanceof FishingRodItem || item instanceof FlintAndSteelItem
                || item instanceof BrushItem
                || stack.canPerformAction(ItemAbilities.PICKAXE_DIG)
                || stack.canPerformAction(ItemAbilities.AXE_DIG)
                || stack.canPerformAction(ItemAbilities.SHOVEL_DIG)
                || stack.canPerformAction(ItemAbilities.HOE_DIG)
                || stack.canPerformAction(ItemAbilities.SHEARS_DIG);
    }

    public static boolean allowed(Item item) {
        return !isTool(new ItemStack(item));
    }

    /** Called after loot modifiers, including for the separate once-per-share shard roll. */
    public static void strip(List<ItemStack> loot, @Nullable ResourceLocation table) {
        if (table != null && TABLES.contains(table)) loot.removeIf(BarrelTools::isTool);
    }
}
