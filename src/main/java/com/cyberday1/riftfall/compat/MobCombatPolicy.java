package com.cyberday1.riftfall.compat;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;

/** Authored boss timing and summon AI must not be replaced with generic weapon combos. */
public final class MobCombatPolicy {
    private MobCombatPolicy() {}

    public static boolean preserve(Mob mob) {
        return mob instanceof OwnableEntity || !mob.getPersistentData().getCompound("riftfall:boss").isEmpty();
    }
}
