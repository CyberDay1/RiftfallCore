package com.cyberday1.riftfall.skill;

import java.util.function.BooleanSupplier;

/**
 * A skill's damage is its own number: a spell impact's hit, a DoT tick, a reflected share. Simply Swords' weapon
 * implicits read the attacker's main hand on every hit, so a class weapon in hand added 20-40% (a dagger's backstab)
 * or a 5-15% double (a katana) to every Phantom and Sword Immortal skill. {@code MixinWeaponImplicitDamage} leaves a
 * hit alone while it is a skill's; weapon swings keep their implicits but the second-crit ones SingleCrit closes.
 */
public final class SkillDamage {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private SkillDamage() {}

    /** Runs {@code hit} as a skill's damage. */
    public static boolean dealing(BooleanSupplier hit) {
        int[] depth = DEPTH.get();
        depth[0]++;
        try {
            return hit.getAsBoolean();
        } finally {
            depth[0]--;
        }
    }

    public static boolean active() {
        return DEPTH.get()[0] > 0;
    }
}
