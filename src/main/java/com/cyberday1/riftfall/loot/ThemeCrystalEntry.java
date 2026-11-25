package com.cyberday1.riftfall.loot;

import com.cyberday1.riftfall.init.RiftfallLootEntries;
import com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@code riftfall:theme_crystal} — whichever crystal belongs to the rift this draw happened in.
 *
 * <h2>Named by the generator, not by the dimension</h2>
 * The same rule {@code RiftSpawns} keeps: the shipped {@code riftfall:rift} dimension is a rift and
 * its id does not say so. Outside a rift the entry yields nothing, so a barrel that somehow stands
 * in the overworld gives only its tier's contents.
 *
 * <p>
 * The theme's own namespace is kept, so a datapack that adds a theme brings its crystal table with
 * it instead of having to overwrite one of ours.
 *
 * <h2>Why an entry rather than a fall-through</h2>
 * The rate used to be a tier table's {@code minecraft:empty} weight, read after the table returned
 * empty. Any third-party loot modifier that adds a stack — Apotheosis' {@code affix_loot} matches
 * {@code chests.*} in every namespace — made the table non-empty and ate the crystal draw. A rate
 * expressed as an absence is a rate another mod can spend.
 */
public class ThemeCrystalEntry extends LootPoolSingletonContainer {

    public static final MapCodec<ThemeCrystalEntry> CODEC = RecordCodecBuilder.mapCodec(
            instance -> singletonFields(instance).apply(instance, ThemeCrystalEntry::new));

    private ThemeCrystalEntry(
            int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
    }

    @Override
    public LootPoolEntryType getType() {
        return RiftfallLootEntries.THEME_CRYSTAL.get();
    }

    /**
     * Resolves and delegates exactly the way {@code NestedLootTable} does. {@code getRandomItemsRaw}
     * is the deliberate call — the non-raw one would fire the global loot modifiers a second time,
     * on a table the caller never named — and it carries vanilla's own recursion guard, which is
     * the only guard available here: the table is picked at draw time, so {@code validate} has
     * nothing to look at.
     */
    @Override
    @SuppressWarnings("deprecation")
    protected void createItemStack(Consumer<ItemStack> consumer, LootContext context) {
        ServerLevel level = context.getLevel();
        if (!(level.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator)) {
            return;
        }
        ResourceLocation theme = generator.theme(level).location();
        ResourceKey<LootTable> table = ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(theme.getNamespace(), "chests/rift_theme/" + theme.getPath()));
        context.getResolver()
                .get(Registries.LOOT_TABLE, table)
                .map(Holder::value)
                .orElse(LootTable.EMPTY)
                .getRandomItemsRaw(context, consumer);
    }
}
