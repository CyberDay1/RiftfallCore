package com.cyberday1.riftfall.tool;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Extra tool kinds the looting pick can learn at the Blacksmith. Stored as {@code mode_<key>} in the pick's upgrade
 * compound; each one adds its mineable tag at netherite tier and its vanilla right-click abilities.
 */
public enum ToolMode {
    SHOVEL(BlockTags.MINEABLE_WITH_SHOVEL, ItemAbilities.DEFAULT_SHOVEL_ACTIONS),
    AXE(BlockTags.MINEABLE_WITH_AXE, ItemAbilities.DEFAULT_AXE_ACTIONS),
    HOE(BlockTags.MINEABLE_WITH_HOE, ItemAbilities.DEFAULT_HOE_ACTIONS);

    public final TagKey<Block> mineable;
    public final Set<ItemAbility> abilities;
    public final String key;

    ToolMode(TagKey<Block> mineable, Set<ItemAbility> abilities) {
        this.mineable = mineable;
        this.abilities = abilities;
        this.key = "mode_" + name().toLowerCase(Locale.ROOT);
    }

    private static final Tool[] TOOLS = new Tool[1 << values().length];

    public static boolean has(ItemStack stack, ToolMode mode) {
        return LootingToolItem.isTool(stack) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getCompound(LootingToolItem.UPGRADES_KEY).getBoolean(mode.key);
    }

    public static EnumSet<ToolMode> of(ItemStack stack) {
        var set = EnumSet.noneOf(ToolMode.class);
        if (!LootingToolItem.isTool(stack)) return set;
        var upgrades = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(LootingToolItem.UPGRADES_KEY);
        for (var mode : values()) if (upgrades.getBoolean(mode.key)) set.add(mode);
        return set;
    }

    /** The pick's mining rules with these modes: pickaxe always, plus each mode's tag, all at netherite tier. */
    public static Tool tool(Set<ToolMode> modes) {
        int mask = 0;
        for (var mode : modes) mask |= 1 << mode.ordinal();
        var cached = TOOLS[mask];
        if (cached != null) return cached;
        var rules = new ArrayList<Tool.Rule>();
        rules.add(Tool.Rule.deniesDrops(Tiers.NETHERITE.getIncorrectBlocksForDrops()));
        rules.add(Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, Tiers.NETHERITE.getSpeed()));
        for (var mode : values()) if (modes.contains(mode)) rules.add(Tool.Rule.minesAndDrops(mode.mineable, Tiers.NETHERITE.getSpeed()));
        var built = new Tool(List.copyOf(rules), 1.0F, 1);
        TOOLS[mask] = built;
        return built;
    }
}
