package com.cyberday1.riftfall.compat.viewer;

import com.cyberday1.riftfall.gear.RiftLootRarity;
import com.cyberday1.riftfall.gear.FortuneGrant;
import com.cyberday1.riftfall.home.npc.smith.SmithRules;
import com.cyberday1.riftfall.home.npc.smith.ToolWorkService;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.init.RiftfallTags;
import com.cyberday1.riftfall.tool.LootingToolItem;
import com.cyberday1.riftfall.tool.ToolMode;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

/**
 * The example stacks the Fitter's pages show: stand-in gear stamped with a tier (and a rarity on salvage), the looting
 * pick at a level or with a mode, and tools at a Fortune level. Each slot also says "any rift gear", since the real
 * input is whatever the rifts dropped.
 */
public final class FitterExamples {
    public static final List<Item> WEAPONS = List.of(Items.IRON_SWORD, Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.SHIELD);
    public static final List<Item> ARMOUR = List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
    /** Trinkets are other mods' items; members of these Curios tags stand in, when there are any. */
    public static final List<TagKey<Item>> TRINKET_TAGS = List.of(curios("necklace"), curios("ring"));
    public static final int MAX_TRINKETS = 8;
    public static final List<Item> FORTUNE_TOOLS = List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_AXE);

    private FitterExamples() {}

    private static TagKey<Item> curios(String slot) { return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("curios", slot)); }

    /** Stand-ins for one kind, skipping anything the Fitter refuses by tag. */
    public static List<Item> items(SmithRules.Kind kind) {
        return switch (kind) {
            case WEAPON -> WEAPONS;
            case ARMOUR -> ARMOUR;
            case TRINKET -> {
                var out = new ArrayList<Item>();
                for (var tag : TRINKET_TAGS)
                    BuiltInRegistries.ITEM.getTag(tag).ifPresent(set -> set.forEach(h -> {
                        var stack = new ItemStack(h);
                        if (out.size() < MAX_TRINKETS && !out.contains(h.value()) && !stack.is(RiftfallTags.UNIQUE_WEAPONS)
                                && !stack.is(RiftfallTags.NOT_GEAR) && !stack.is(RiftfallTags.GEAR_DENIED)) out.add(h.value());
                    }));
                yield List.copyOf(out);
            }
        };
    }

    public static List<Item> allGear() {
        var out = new ArrayList<Item>();
        for (var kind : SmithRules.Kind.values()) out.addAll(items(kind));
        return List.copyOf(out);
    }

    public static ItemStack tiered(Item item, int tier) {
        var stack = new ItemStack(item);
        stack.set(RiftfallDataComponents.RIFT_TIER.get(), tier);
        return stack;
    }

    /** Salvage's input: tier and rarity label, no affixes. */
    public static List<ItemStack> salvage(int rarity, int tier) {
        return allGear().stream().map(i -> RiftLootRarity.labelled(tiered(i, tier), rarity)).toList();
    }

    public static List<ItemStack> retemper(SmithRules.Kind kind, int tier) {
        return items(kind).stream().map(i -> tiered(i, tier)).toList();
    }

    public static ItemStack stock(int count) { return new ItemStack(RiftfallItems.TEMPERING_STOCK.get(), count); }

    /** The rarity's material stacks at {@code count}: every member of {@code #riftfall:rarity_material/<r>}. */
    public static List<ItemStack> material(int rarity, int count) {
        var out = new ArrayList<ItemStack>();
        BuiltInRegistries.ITEM.getTag(ToolWorkService.rarityTag(rarity)).ifPresent(set -> set.forEach(h -> out.add(new ItemStack(h, count))));
        return List.copyOf(out);
    }

    public static List<ItemStack> tagged(TagKey<Item> tag, int count) {
        var out = new ArrayList<ItemStack>();
        BuiltInRegistries.ITEM.getTag(tag).ifPresent(set -> set.forEach(h -> out.add(new ItemStack(h, count))));
        return List.copyOf(out);
    }

    /** A looting pick at {@code level}, as {@code ToolUpgrades.level} reads it. */
    public static ItemStack pick(int level) {
        var stack = new ItemStack(RiftfallItems.LOOTING_PICK.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(LootingToolItem.LEVEL_KEY, level));
        return stack;
    }

    public static ItemStack pick(ToolMode mode) {
        var stack = new ItemStack(RiftfallItems.LOOTING_PICK.get());
        ToolWorkService.learn(stack, mode);
        return stack;
    }

    /** Fortune tools and the pick at granted Fortune {@code level}; plain when the holder is unknown or the level 0. */
    public static List<ItemStack> fortune(int level, Holder<Enchantment> fortune) {
        var out = new ArrayList<ItemStack>();
        for (var item : FORTUNE_TOOLS) out.add(new ItemStack(item));
        out.add(new ItemStack(RiftfallItems.LOOTING_PICK.get()));
        if (fortune != null && level > 0) out.forEach(s -> FortuneGrant.set(s, fortune, level));
        return List.copyOf(out);
    }
}
