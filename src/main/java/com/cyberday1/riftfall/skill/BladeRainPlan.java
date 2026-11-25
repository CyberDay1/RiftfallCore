package com.cyberday1.riftfall.skill;

/**
 * Blade Rain's storm as numbers: where each blade lands, on which tick, and how many reach a target. No Minecraft and
 * no Spell Engine type, so the server entity, the client renderer, the skill guide and the tests share one plan.
 * {@code tools/skill_rain.py} mirrors it; the generator splits the catalogue total over {@link #centreHits}.
 *
 * <p>Layout: a jittered sunflower over the disk (equal area per blade, so no ring and no bunching at the centre),
 * turned by the caster's yaw. Timing: a telegraph, then each blade lands on its own hashed tick inside the window.
 * Both come from a 32-bit integer hash, so Java and Python agree bit for bit.
 */
public final class BladeRainPlan {

    /** The Spell Engine custom delivery, under riftfall, that opens a storm. */
    public static final String HANDLER = "rain/blade";
    public static final int COUNT = 20;
    /** Blocks around a blade's landing point that its stab reaches. */
    public static final double IMPACT_RADIUS = 2.0;
    public static final int TELEGRAPH_TICKS = 12;
    public static final int WINDOW_TICKS = 24;
    /** The whole storm: the last landing (at most {@code TELEGRAPH + WINDOW}), a short hold, then the fade. */
    public static final int LIFETIME_TICKS = 46;
    public static final int FADE_TICKS = 8;
    /** Ticks a blade spends falling before it stabs; the renderer's, so a blade is in the air before it lands. */
    public static final int FALL_TICKS = 10;
    /** Upward push a blade's hit adds, before knockback resistance. Bosses take none. */
    public static final double KNOCKUP = 0.25;

    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private BladeRainPlan() {
    }

    /** A 32-bit integer hash of {@code i} under {@code salt}. */
    static int mix(int i, int salt) {
        int x = i * 0x9E3779B9 + salt * 0x85EBCA6B;
        x ^= x >>> 16;
        x *= 0x7FEB352D;
        x ^= x >>> 15;
        x *= 0x846CA68B;
        x ^= x >>> 16;
        return x;
    }

    /** A fraction in [0, 1) with 24 bits, exact in a double. */
    static double unit(int i, int salt) {
        return (mix(i, salt) >>> 8) / (double) (1 << 24);
    }

    /** Blade {@code i}'s share of the disk's area, measured from the centre. Its distance is radius x sqrt(share). */
    static double areaShare(int i) {
        return (i + 0.5 + 0.8 * (unit(i, 1) - 0.5)) / COUNT;
    }

    /** Blade {@code i}'s landing offset {x, z} from the centre, the layout turned by {@code yawDegrees}. */
    public static double[] offset(int i, double radius, float yawDegrees) {
        double angle = i * GOLDEN_ANGLE + 0.7 * (unit(i, 2) - 0.5) + Math.toRadians(yawDegrees);
        double r = radius * Math.sqrt(areaShare(i));
        return new double[]{Math.cos(angle) * r, Math.sin(angle) * r};
    }

    /** The storm tick on which blade {@code i} stabs, after the telegraph and inside the window. */
    public static int landingTick(int i) {
        return TELEGRAPH_TICKS + 1 + (int) (unit(i, 3) * WINDOW_TICKS);
    }

    /** Degrees off vertical the blade falls at: 3 to 15. */
    public static double tiltDegrees(int i) {
        return 3.0 + 12.0 * unit(i, 4);
    }

    /** Compass bearing, in radians, the blade's tilt leans toward. */
    public static double azimuth(int i) {
        return unit(i, 5) * Math.PI * 2.0;
    }

    /** Blades that reach a target standing at the centre. Compared on area share, so no square root decides it. */
    public static int centreHits(double radius) {
        double reach = (IMPACT_RADIUS / radius) * (IMPACT_RADIUS / radius);
        int hits = 0;
        for (int i = 0; i < COUNT; i++) {
            if (areaShare(i) <= reach) hits++;
        }
        return hits;
    }

    /** Blades that reach a target at {@code (x, z)} from the centre; none outside the area. */
    public static int hitsAt(double radius, double x, double z, float yawDegrees) {
        if (x * x + z * z > radius * radius) return 0;
        int hits = 0;
        for (int i = 0; i < COUNT; i++) {
            double[] at = offset(i, radius, yawDegrees);
            double dx = at[0] - x;
            double dz = at[1] - z;
            if (dx * dx + dz * dz <= IMPACT_RADIUS * IMPACT_RADIUS) hits++;
        }
        return hits;
    }

    /** One blade's share of a cast's total coefficient: a target at the centre takes the whole total. */
    public static double perBlade(double total, double radius) {
        return total / centreHits(radius);
    }

    /** The last tick any blade lands. */
    public static int lastLanding() {
        int last = 0;
        for (int i = 0; i < COUNT; i++) last = Math.max(last, landingTick(i));
        return last;
    }
}
