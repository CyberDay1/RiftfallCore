package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.skill.SkillBook;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.ToIntFunction;

/** What one cooldown slot shows: the share still to run, the time left and the key. Nothing here draws. */
public final class CooldownFace {

    private CooldownFace() {
    }

    /** The share of the timer still to run, 0 to 1. A timer longer than its stated length reads full. */
    public static float fraction(double remainingTicks, int durationTicks) {
        if (!(remainingTicks > 0.0)) {
            return 0.0F;
        }
        if (durationTicks <= 0 || remainingTicks >= durationTicks) {
            return 1.0F;
        }
        return (float) (remainingTicks / durationTicks);
    }

    /** "2m" from a minute up, whole seconds from 10, tenths under that, all rounded up; "" when ready. */
    public static String seconds(double remainingTicks) {
        if (!(remainingTicks > 0.0)) {
            return "";
        }
        double seconds = remainingTicks / 20.0;
        long whole = (long) Math.ceil(seconds - 1e-9);
        if (whole >= 60L) {
            return (long) Math.ceil(seconds / 60.0 - 1e-9) + "m";
        }
        if (whole >= 10L) {
            return Long.toString(whole);
        }
        return String.format(Locale.ROOT, "%.1f", Math.ceil(seconds * 10.0 - 1e-9) / 10.0);
    }

    /** The slot's own name: 1 to 8, or {@code ultimate} for the ultimate slot. */
    public static String slotNumber(int slot, String ultimate) {
        return slot == SkillBook.ULTIMATE_SLOT ? ultimate : Integer.toString(slot + 1);
    }

    /** The bound key's name when it fits {@code maxWidth}; else, or unbound, the slot's number. */
    public static String keyLabel(@Nullable String bound, int slot, String ultimate, ToIntFunction<String> width,
                                  int maxWidth) {
        if (bound == null || bound.isBlank() || width.applyAsInt(bound) > maxWidth) {
            return slotNumber(slot, ultimate);
        }
        return bound;
    }
}
