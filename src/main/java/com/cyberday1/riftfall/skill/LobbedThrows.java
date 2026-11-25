package com.cyberday1.riftfall.skill;

/**
 * A lobbed skill projectile (gravity on) that runs out of flight bursts where it is. Spell Engine ends a flying
 * projectile at its spell's range without an impact; a throw from mid-jump starts higher and runs out just short
 * of the ground, so grenades vanished.
 */
public final class LobbedThrows {
    /** {@code -Driftfall.lob_burst=off} restores the silent end, the probe's before arm. */
    public static final boolean ENABLED = !"off".equals(System.getProperty("riftfall.lob_burst"));

    private LobbedThrows() {
    }

    public static boolean burstsAtRange(boolean ours, float gravity, boolean flying, float travelled, float range) {
        return ours && flying && gravity > 0.0F && travelled >= range;
    }
}
