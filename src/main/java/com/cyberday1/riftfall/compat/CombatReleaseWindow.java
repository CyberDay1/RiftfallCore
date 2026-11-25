package com.cyberday1.riftfall.compat;

/** Convert a finite release gesture to ticks without allowing bad assets to lock attacks forever. */
public final class CombatReleaseWindow {
    private CombatReleaseWindow() {}

    public static int ticks(int endTick, float speed, boolean looping) {
        if (looping || endTick <= 0 || !Float.isFinite(speed) || speed <= 0) return 0;
        return Math.min(100, (int) Math.ceil(endTick / (double) speed));
    }
}
