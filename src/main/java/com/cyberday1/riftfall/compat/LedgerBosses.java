package com.cyberday1.riftfall.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Bosses whose health is a damage ledger that {@code setHealth} cannot move: legendary_monsters'
 * {@code IAnimatedBoss} subtracts from its own total on {@code LivingIncomingDamageEvent}, so a
 * multiplier on {@code LivingDamageEvent.Pre} never reaches it. Found by class name, no compile
 * dependency; if the class moves, nothing here is a ledger boss and the old path runs.
 */
public final class LedgerBosses {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final String MOD = "legendary_monsters";
    static final String BOSS_CLASS =
            "net.miauczel.legendary_monsters.entity.AnimatedMonster.OriginClasses.IAnimatedBoss";

    /** Bisection steps; 40 halvings of a hit is far below a float's resolution. */
    static final int STEPS = 40;

    private static final @Nullable Class<?> TYPE = resolve();
    private static final @Nullable Method CAP = TYPE == null ? null : cap(TYPE);

    private LedgerBosses() {
    }

    public static boolean isLedger(Entity entity) {
        return TYPE != null && TYPE.isInstance(entity);
    }

    /**
     * The incoming amount that makes a ledger boss lose {@code multiplier} of what {@code amount}
     * would have cost it: its own cap first, then the one non-linear step, its armour, solved for.
     */
    public static float scaledIncoming(LivingEntity boss, DamageSource source, float amount, float multiplier) {
        float capped = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? amount : Math.min(amount, cap(boss));
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return capped * multiplier;
        }
        float armour = boss.getArmorValue();
        float toughness = (float) boss.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        return solve(damage -> CombatRules.getDamageAfterAbsorb(boss, damage, source, armour, toughness),
                capped, multiplier);
    }

    /** The {@code x} in {@code [0, capped]} with {@code absorb(x) = multiplier * absorb(capped)}. */
    static float solve(Absorb absorb, float capped, float multiplier) {
        if (capped <= 0.0F || multiplier >= 1.0F) {
            return capped;
        }
        float target = multiplier * absorb.after(capped);
        float low = 0.0F;
        float high = capped;
        for (int step = 0; step < STEPS; step++) {
            float middle = (low + high) * 0.5F;
            if (absorb.after(middle) < target) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return high;
    }

    /** What a hit of this size leaves after the boss's armour; strictly increasing in damage. */
    @FunctionalInterface
    interface Absorb {
        float after(float damage);
    }

    private static float cap(LivingEntity boss) {
        if (CAP == null) {
            return Float.MAX_VALUE;
        }
        try {
            return (float) (double) CAP.invoke(boss);
        } catch (ReflectiveOperationException | RuntimeException failed) {
            return Float.MAX_VALUE;
        }
    }

    private static @Nullable Class<?> resolve() {
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded(MOD)) {
            return null;
        }
        try {
            return Class.forName(BOSS_CLASS, false, LedgerBosses.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError missing) {
            LOGGER.warn("{} is loaded but {} is not there; its bosses keep the Pre-event multiplier", MOD, BOSS_CLASS);
            return null;
        }
    }

    private static @Nullable Method cap(Class<?> type) {
        try {
            return type.getMethod("damageCap");
        } catch (NoSuchMethodException | SecurityException missing) {
            LOGGER.warn("{} has no damageCap(); ledger bosses are scaled uncapped", BOSS_CLASS);
            return null;
        }
    }
}
