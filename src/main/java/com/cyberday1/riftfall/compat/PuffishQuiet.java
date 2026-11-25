package com.cyberday1.riftfall.compat;

/**
 * Riftfall lists no Puffish skill trees, and Puffish toasts "No skill trees provided" to an operator or the host on
 * every join and reload when its config is valid but empty. That toast is skipped; an invalid config still toasts.
 */
public final class PuffishQuiet {
    private static int skipped;

    private PuffishQuiet() {
    }

    /** Puffish's own test before its toast: a valid config with no categories. */
    public static boolean skip(boolean configValid, boolean noCategories) {
        if (!configValid || !noCategories) return false;
        skipped++;
        return true;
    }

    public static int skipped() {
        return skipped;
    }
}
