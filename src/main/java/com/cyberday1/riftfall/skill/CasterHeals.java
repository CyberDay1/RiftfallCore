package com.cyberday1.riftfall.skill;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A Riftfall drain heals its caster only off a hit that landed. The engine runs a caster heal for
 * every target the cast reached, whether or not the hit on it did anything, and never asks whose
 * side that target is on. {@code MixinSpellImpactsDrain} reports the hit here and asks before the heal.
 */
public final class CasterHeals {

    static final String NAMESPACE = "riftfall";

    private static final ThreadLocal<boolean[]> LANDED = ThreadLocal.withInitial(() -> new boolean[1]);
    private static final AtomicLong WITHHELD = new AtomicLong();

    private CasterHeals() {
    }

    /** Whether to drop a heal: it goes on the caster, a hit on this target missed, and the ability is ours. */
    public static boolean withholds(@Nullable ResourceLocation ability, boolean onCaster, boolean hitMissed) {
        return hitMissed && onCaster && ability != null && NAMESPACE.equals(ability.getNamespace());
    }

    /** Before a DAMAGE impact runs. */
    public static void hitStarting() {
        LANDED.get()[0] = false;
    }

    /** What the target's {@code hurt} answered. */
    public static void hurtAnswered(boolean landed) {
        if (landed) {
            LANDED.get()[0] = true;
        }
    }

    /** After a DAMAGE impact: whether any hurt in it landed. False when it never reached one. */
    public static boolean hitLanded() {
        return LANDED.get()[0];
    }

    public static void withheld() {
        WITHHELD.incrementAndGet();
    }

    /** How many caster heals were dropped. A probe's read. */
    public static long withheldCount() {
        return WITHHELD.get();
    }
}
