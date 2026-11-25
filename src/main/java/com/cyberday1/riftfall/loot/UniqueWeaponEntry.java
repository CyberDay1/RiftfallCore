package com.cyberday1.riftfall.loot;

import com.cyberday1.riftfall.gear.UniqueWeapons;
import com.cyberday1.riftfall.init.RiftfallLootEntries;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@code riftfall:unique_weapon}: one weapon from {@code riftfall:unique_weapons}, only in a rift of at least
 * {@code min_tier} (default {@link UniqueWeapons#MIN_TIER}). The boss table gates its chance with a pool condition.
 */
public class UniqueWeaponEntry extends LootPoolSingletonContainer {

    public static final MapCodec<UniqueWeaponEntry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.INT.optionalFieldOf("min_tier", UniqueWeapons.MIN_TIER).forGetter(entry -> entry.minTier))
            .and(singletonFields(instance))
            .apply(instance, UniqueWeaponEntry::new));

    private final int minTier;

    private UniqueWeaponEntry(int minTier, int weight, int quality, List<LootItemCondition> conditions,
            List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.minTier = minTier;
    }

    @Override
    public LootPoolEntryType getType() {
        return RiftfallLootEntries.UNIQUE_WEAPON.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> consumer, LootContext context) {
        ServerLevel level = context.getLevel();
        if (!RiftLevels.isRiftWorld(level) || UniqueWeapons.tier(RiftLevels.tier(level)) < minTier) {
            return;
        }
        ItemStack unique = UniqueWeapons.draw(context.getRandom());
        if (!unique.isEmpty()) {
            consumer.accept(unique);
        }
    }
}
