package com.cyberday1.riftfall.loot;

import com.cyberday1.riftfall.init.RiftfallLootEntries;
import com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator;
import com.cyberday1.riftfall.worldgen.ore.RiftOres;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/**
 * {@code riftfall:rift_tier_chance}: passes with a chance running from {@code base} at tier 1 to
 * {@code top} at {@link RiftOres#TOP_TIER}, by the rift's tier. Never passes outside a rift.
 */
public record RiftTierChance(float base, float top) implements LootItemCondition {

    public static final MapCodec<RiftTierChance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.floatRange(0F, 1F).fieldOf("base").forGetter(RiftTierChance::base),
            Codec.floatRange(0F, 1F).fieldOf("top").forGetter(RiftTierChance::top)
    ).apply(instance, RiftTierChance::new));

    @Override
    public LootItemConditionType getType() {
        return RiftfallLootEntries.RIFT_TIER_CHANCE.get();
    }

    public double chance(int tier) {
        return base + (top - base) * RiftOres.tierFactor(tier);
    }

    @Override
    public boolean test(LootContext context) {
        if (!(context.getLevel().getChunkSource().getGenerator() instanceof RiftChunkGenerator rift)) return false;
        return context.getRandom().nextFloat() < chance(rift.tier());
    }
}
