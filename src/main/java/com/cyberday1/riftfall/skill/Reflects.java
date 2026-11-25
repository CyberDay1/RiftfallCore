package com.cyberday1.riftfall.skill;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The reflect window: an active opens it on its caster, and for its length a share of every
 * hit a living attacker lands on the caster goes back to that attacker; with {@code projectiles}, a vanilla projectile
 * that would hit the caster is turned around instead. Names no engine type; {@link RiftReflect} binds it.
 *
 * <p>The reflect runs on the counter window's guards: it is a reaction's delivery, so nothing it
 * does reflects or counters again, and a window reflects at most once per {@link #ICD_TICKS}.
 */
public final class Reflects {

    /** The least gap between two reflects of one window, in ticks: an area tick or a multi-hit pays once. */
    public static final int ICD_TICKS = 10;
    /** How far an attacker may stand and still take the reflect, in blocks (a bow's reach, not a sword's). */
    public static final double REACH = 24.0;
    /**
     * A node's {@code reflect} terms: the share of each hit sent back, how long the window stays open, and whether
     * vanilla projectiles are turned around.
     */
    public record Spec(double share, double seconds, boolean projectiles) {
        public Spec {
            if (!(share > 0.0 && share <= 1.0)) {
                throw new IllegalArgumentException("a reflect sends back a share in (0, 1], was " + share);
            }
            if (!(seconds > 0.0) || Double.isInfinite(seconds)) {
                throw new IllegalArgumentException("a reflect window lasts a positive time, was " + seconds);
            }
        }

        public int ticks() {
            return (int) Math.round(seconds * 20.0);
        }
    }

    /** One open window: whose node, at what rank, its share, and when it ends. */
    public record Window(String node, int rank, Spec spec, long endsAt) {
    }

    private static final Map<UUID, Window> OPEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private Reflects() {
    }

    /** The damage a hit of {@code amount} sends back at {@code share}. */
    public static double reflected(double amount, double share) {
        if (!(amount > 0.0) || Double.isInfinite(amount)) {
            return 0.0;
        }
        return amount * share;
    }

    public static void open(UUID player, Window window) {
        OPEN.put(player, window);
        LAST.remove(player);
    }

    /** The player's window if it is still open at {@code now}; a lapsed one is closed and null comes back. */
    public static @Nullable Window window(UUID player, long now) {
        Window window = OPEN.get(player);
        if (window == null) {
            return null;
        }
        if (now >= window.endsAt()) {
            OPEN.remove(player, window);
            return null;
        }
        return window;
    }

    /** Whether the window may reflect at {@code now}: the first time, or {@link #ICD_TICKS} after the last. */
    public static boolean ready(UUID player, long now) {
        Long last = LAST.get(player);
        return last == null || now - last >= ICD_TICKS;
    }

    public static void spent(UUID player, long now) {
        LAST.put(player, now);
    }

    public static void close(UUID player) {
        OPEN.remove(player);
        LAST.remove(player);
    }
}
