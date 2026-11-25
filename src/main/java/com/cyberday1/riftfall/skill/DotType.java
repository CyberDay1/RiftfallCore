package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

import java.util.Locale;

/**
 * H6's damage-over-time types (ruled 08-23): each ticks as its own {@code riftfall:<key>} damage type, never in
 * {@code #bypasses_effects}, and each has a {@code riftfall:<key>_damage} attribute on the caster that scales it.
 */
public enum DotType {
    BLEED, BURN, VENOM;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ResourceKey<DamageType> damageType() {
        return ResourceKey.create(Registries.DAMAGE_TYPE, RiftfallCore.id(key()));
    }
}
