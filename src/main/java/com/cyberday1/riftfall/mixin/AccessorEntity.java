package com.cyberday1.riftfall.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

/**
 * Reads an entity's NeoForge data without making it: {@code getPersistentData()} creates an empty
 * compound on first call, which the entity then saves for good.
 */
@Mixin(Entity.class)
public interface AccessorEntity {

    @Nullable
    @Accessor(value = "persistentData", remap = false)
    CompoundTag riftfall$persistentDataOrNull();
}
