package com.cyberday1.riftfall.client;

/**
 * The boss finder's numbers: where the arrow points relative to the player's facing, how far the target is
 * across the ground, and whether its height is worth showing. Pure, so it is unit-tested.
 */
public final class BossFinderBearing {

    /** A height difference under this many blocks is not shown. */
    public static final int RISE_SHOWN = 8;

    private BossFinderBearing() {
    }

    /**
     * Degrees to turn the arrow clockwise from straight up: 0 dead ahead, 90 to the right, 180 behind,
     * in (-180, 180]. {@code yaw} is Minecraft's yRot (0 faces +z, 90 faces -x).
     */
    public static float relative(double dx, double dz, float yaw) {
        if (dx == 0.0 && dz == 0.0) {
            return 0.0F;
        }
        double heading = Math.toDegrees(Math.atan2(-dx, dz));
        return wrap((float) (heading - yaw));
    }

    static float wrap(float degrees) {
        float d = degrees % 360.0F;
        if (d <= -180.0F) {
            d += 360.0F;
        } else if (d > 180.0F) {
            d -= 360.0F;
        }
        return d;
    }

    /** Metres across the ground, rounded. */
    public static int metres(double dx, double dz) {
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    /** The rise to show: blocks up (positive) or down, or 0 when it is under {@link #RISE_SHOWN}. */
    public static int rise(double dy) {
        int r = (int) Math.round(dy);
        return Math.abs(r) >= RISE_SHOWN ? r : 0;
    }
}
