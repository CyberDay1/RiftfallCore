package com.cyberday1.riftfall.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Summon-only art preview. No AI, combat, terrain changes or automatic spawning. */
public final class UnseatedPreviewEntity extends Entity {
    public UnseatedPreviewEntity(EntityType<? extends UnseatedPreviewEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    // Use world time so players joining later see the same animation phase.
    public double animationTicks(float partialTick) {
        return (level().getGameTime() % 240L) + partialTick;
    }
}
