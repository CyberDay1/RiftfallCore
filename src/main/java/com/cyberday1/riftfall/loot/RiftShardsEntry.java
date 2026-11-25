package com.cyberday1.riftfall.loot;

import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.init.RiftfallLootEntries;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@code riftfall:rift_shards}: rift shards for one {@link RiftShards.Grade} at the rift's tier. Yields outside a rift
 * too; the {@code rift_only} strip is what removes them there.
 */
public class RiftShardsEntry extends LootPoolSingletonContainer {

    public static final MapCodec<RiftShardsEntry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("grade").forGetter(entry -> entry.grade.key()))
            .and(singletonFields(instance))
            .apply(instance, RiftShardsEntry::new));

    private final RiftShards.Grade grade;

    private RiftShardsEntry(String grade, int weight, int quality, List<LootItemCondition> conditions,
            List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        RiftShards.Grade parsed = RiftShards.Grade.of(grade);
        if (parsed == null) {
            throw new IllegalArgumentException("riftfall:rift_shards has no grade " + grade);
        }
        this.grade = parsed;
    }

    @Override
    public LootPoolEntryType getType() {
        return RiftfallLootEntries.RIFT_SHARDS.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> consumer, LootContext context) {
        RandomSource random = context.getRandom();
        if (random.nextInt(100) >= grade.chancePercent()) {
            return;
        }
        int tier = RiftShards.tier(RiftLevels.tier(context.getLevel()));
        int count = grade.min(tier) + random.nextInt(grade.max(tier) - grade.min(tier) + 1);
        int stack = RiftfallItems.RIFT_SHARD.get().getDefaultMaxStackSize();
        while (count > 0) {
            int part = Math.min(count, stack);
            consumer.accept(new ItemStack(RiftfallItems.RIFT_SHARD.get(), part));
            count -= part;
        }
    }
}
