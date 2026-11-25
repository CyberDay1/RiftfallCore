package com.cyberday1.riftfall.skill;

import org.jetbrains.annotations.Nullable;

/**
 * The Phantom's two signature ultimates as numbers: which spell opens which sight, how long it lasts, and the shape
 * of each moment. No Minecraft type, so the server entity, the client renderer and the tests share one plan.
 * Cosmetic only: the spells' own deliveries do every hit.
 */
public final class PhantomSignaturePlan {

    public enum Kind {
        /** A blood moon hangs over the struck ground while the carnage leaps run. */
        BLOOD_MOON("riftfall:phantom_blood_moon_carnage", 64),
        /** A rift tears open at the target and implodes into it. */
        RIFT_COLLAPSE("riftfall:phantom_rift_collapse", 36);

        public final String spell;
        public final int lifetime;

        Kind(String spell, int lifetime) {
            this.spell = spell;
            this.lifetime = lifetime;
        }
    }

    public static final float MOON_RADIUS = 2.6F;
    public static final float MOON_LOW = 3.0F;
    public static final float MOON_HIGH = 7.5F;
    public static final int MOON_RISE_TICKS = 14;
    public static final int FADE_IN_TICKS = 5;
    public static final int FADE_OUT_TICKS = 12;
    public static final int TEAR_TICKS = 8;
    public static final int COLLAPSE_END = 26;
    public static final float TEAR_HEIGHT = 4.5F;
    public static final int SHARDS = 12;

    private PhantomSignaturePlan() {
    }

    public static @Nullable Kind of(String spell) {
        for (Kind kind : Kind.values()) if (kind.spell.equals(spell)) return kind;
        return null;
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    /** Overall opacity at {@code age} ticks: a quick fade in, a hold, a fade out ending on the lifetime. */
    public static float fade(Kind kind, float age) {
        return clamp(age / FADE_IN_TICKS) * clamp((kind.lifetime - age) / FADE_OUT_TICKS);
    }

    /** The moon's height above the ground: eased out from low to high. */
    public static float moonHeight(float age) {
        float t = clamp(age / MOON_RISE_TICKS);
        return MOON_LOW + (MOON_HIGH - MOON_LOW) * (1.0F - (1.0F - t) * (1.0F - t));
    }

    /** The tear's opening, 0 to 1, over the first ticks. */
    public static float tearOpen(float age) {
        return clamp(age / TEAR_TICKS);
    }

    /** The void sphere's radius fraction: whole until the tear is open, then accelerating to nothing. */
    public static float collapse(float age) {
        float t = clamp((age - TEAR_TICKS) / (COLLAPSE_END - TEAR_TICKS));
        return 1.0F - t * t;
    }

    /** The closing flash's progress, 0 before the collapse ends, 1 at the lifetime. */
    public static float flash(Kind kind, float age) {
        return clamp((age - COLLAPSE_END) / (kind.lifetime - COLLAPSE_END));
    }
}
