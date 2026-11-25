package com.cyberday1.riftfall.loot;

import com.cyberday1.riftfall.init.RiftfallLootEntries;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.tool.BarrelLootBonuses;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/** {@code riftfall:in_rift}: the roll happens in a rift (or a barrel cracked with the looting tool). */
public final class InRift implements LootItemCondition {

    public static final InRift INSTANCE = new InRift();
    public static final MapCodec<InRift> CODEC = MapCodec.unit(INSTANCE);

    private InRift() {
    }

    @Override
    public LootItemConditionType getType() {
        return RiftfallLootEntries.IN_RIFT.get();
    }

    @Override
    public boolean test(LootContext context) {
        return RiftLevels.isRiftWorld(context.getLevel()) || BarrelLootBonuses.active();
    }
}
