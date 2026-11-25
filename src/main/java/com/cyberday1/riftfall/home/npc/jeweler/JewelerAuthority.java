package com.cyberday1.riftfall.home.npc.jeweler;

import java.util.function.Supplier;

/** Set only while the Jeweler's own code asks Apotheosis to socket; every other socketing path is refused. */
public final class JewelerAuthority {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private JewelerAuthority() {}

    public static boolean active() { return DEPTH.get() > 0; }

    public static <T> T call(Supplier<T> action) {
        DEPTH.set(DEPTH.get() + 1);
        try { return action.get(); }
        finally { DEPTH.set(DEPTH.get() - 1); }
    }
}
