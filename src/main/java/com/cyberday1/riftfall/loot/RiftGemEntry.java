package com.cyberday1.riftfall.loot;

import com.cyberday1.riftfall.init.RiftfallLootEntries;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.fml.ModList;
import java.util.List;
import java.util.function.Consumer;

/** A socketable gem is an authored reward; the table owns its chance, not a global injector. */
public class RiftGemEntry extends LootPoolSingletonContainer {
    public static final MapCodec<RiftGemEntry> CODEC = RecordCodecBuilder.mapCodec(
            instance -> singletonFields(instance).apply(instance, RiftGemEntry::new));

    private RiftGemEntry(int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
    }

    @Override public LootPoolEntryType getType() { return RiftfallLootEntries.GEM.get(); }

    @Override protected void createItemStack(Consumer<ItemStack> consumer, LootContext context) {
        if (ModList.get().isLoaded("apotheosis")) Apotheosis.draw(consumer, context);
    }

    /** Keep optional types behind the mod check, as with RiftRarity. */
    private static final class Apotheosis {
        static void draw(Consumer<ItemStack> consumer, LootContext context) {
            var gen = dev.shadowsoffire.apotheosis.tiers.GenContext.forLoot(context);
            if (gen == null) return;
            var purity = dev.shadowsoffire.apotheosis.socket.gem.Purity.random(gen);
            var gem = dev.shadowsoffire.apotheosis.socket.gem.GemRegistry.INSTANCE.getRandomItem(gen);
            if (gem != null) consumer.accept(gem.toStack(purity));
        }
    }
}
