package com.cyberday1.riftfall.block.breaker;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the World Breaker's moving parts are at a given tick: the key going in and turning, and the
 * ring of broken shards. Pure arithmetic, in model pixels about the block's corner, so the renderer
 * only poses models and the numbers are checked in a unit test.
 *
 * <p>
 * Ported from the approved prototype ({@code render_all.py}: {@code charge_groups}, {@code ring_items},
 * {@code seated}, {@code ber}), which is what the approved GIFs show.
 */
public final class WorldBreakerMotion {

    /** One lit loop, in ticks: the textures' 16 frames at frametime 5, and one quarter turn of the ring. */
    public static final int LOOP = 80;
    /** A shard needs four texture loops to return to its own corner. */
    public static final int ORBIT = 4 * LOOP;

    /** How long the shards take to lift off onto the ring, and to fly home again. */
    public static final int EASE = 20;

    /** Crystal windup, then a thin incision, then the existing tear pulled fully open. */
    public static final int RIP_START = 24;
    public static final int RIP_WIDEN = 40;

    public record Opening(float width, float height, float depth) { }

    public static Opening opening(double tick) {
        double incision = smooth((tick - RIP_START) / (RIP_WIDEN - RIP_START));
        double spread = smooth((tick - RIP_WIDEN) / (WorldBreakerPhase.CHARGING.ticks() - RIP_WIDEN));
        return new Opening((float) (0.035 * incision + 0.965 * spread),
                (float) (0.55 * incision + 0.45 * spread), (float) (0.08 * incision + 0.92 * spread));
    }

    /** The crystals themselves gain emissive light before the first visible cut. */
    public static float crystalGlow(double tick) {
        return (float) smooth((tick - 4.0) / (RIP_WIDEN - 4.0));
    }

    private static double smooth(double value) {
        double u = clamp(value);
        return u * u * (3.0 - 2.0 * u);
    }

    /** The key's pose in the socket: its shaft tip 2 px down inside it, at 0.6 of its item size. */
    public static final float KEY_TIP_Y = 3.0F;
    public static final float KEY_SCALE = 0.6F;
    /** The key's shaft tip, in its own item model's pixels: the pivot it is flipped and turned about. */
    public static final float KEY_PIVOT_Y = 15.0F;

    /** Where the broken tips leave from: the stump's break, and the tip's own half-length above it. */
    static final float STUMP = 9.0F;
    static final float TIP_HALF = 3.0F;
    /** cooldown_3's baked tips hover this far over their stumps; the fly-home lands them there. */
    static final float HOME_HOVER = 2.5F;
    /** Distance from the block's centre to a corner shard's axis (the corner cluster stands at 2, 2). */
    static final float CORNER = 8.485F;

    private WorldBreakerMotion() {
    }

    /** The key's height offset and its turn, in degrees, {@code tick} ticks into the charge. */
    public static float[] key(double tick) {
        double drop = Math.max(0.0, 1.0 - tick / 4.0) * 2.0;
        double p = clamp((tick - 4.0) / (RIP_START - 4.0));
        double turn = 90.0 * (0.5 - 0.5 * Math.cos(Math.PI * p));
        return new float[] {(float) drop, (float) turn};
    }

    /** How lit the charge glow is, 0..1, {@code tick} ticks into the charge. */
    public static float glow(double tick) {
        return (float) clamp((tick - 2.0) / (WorldBreakerPhase.CHARGING.ticks() - 2.0));
    }

    /** One floating piece: which model, where (px, about the block's centre), and how it is turned. */
    public record Piece(Kind kind, double x, double y, double z, float tilt, float yaw, float roll,
                        boolean visible, double scale) {
    }

    public enum Kind {
        TIP,
        CHIP_LARGE,
        CHIP_SMALL
    }

    /**
     * The twelve pieces on the ring at {@code tick}, with no ease: four broken tips (r 13, centre
     * 8 px) and eight chips, two per quadrant. The ring turns a quarter turn per {@link #LOOP}; every
     * other term follows that piece continuously. The complete pose repeats after four texture
     * loops, when the same piece has made a full orbit.
     */
    public static List<Piece> ring(double tick) {
        return orbit(tick - Math.floor(tick / ORBIT) * ORBIT);
    }

    private static List<Piece> orbit(double t) {
        List<Piece> out = new ArrayList<>(12);
        double turn = (Math.PI / 2.0) * t / LOOP;
        for (int j = 0; j < 4; j++) {
            double q = Math.PI / 4.0 + j * Math.PI / 2.0;
            double th = q + turn;
            double y = 8.0 + 0.8 * Math.sin(2.0 * Math.PI * t / 40.0 + 2.0 * th);
            out.add(piece(Kind.TIP, 13.0, th, y,
                    22.0 * Math.sin(2.0 * Math.PI * t / LOOP + th),
                    -Math.toDegrees(th),
                    15.0 * Math.cos(2.0 * Math.PI * t / LOOP + 2.0 * th), true));
            for (int k = 0; k < 2; k++) {
                double dq = Math.toRadians(k == 0 ? 32.0 : -38.0);
                double r = k == 0 ? 14.5 : 9.5;
                double y0 = k == 0 ? 5.5 : 11.0;
                double th2 = q + dq + turn;
                double yy = y0 + 0.8 * Math.sin(2.0 * Math.PI * t / 40.0 + 3.0 * th2);
                // Gentle tumbling, without the old full-revolution oscillation and reversals.
                out.add(piece(k == 0 ? Kind.CHIP_LARGE : Kind.CHIP_SMALL, r, th2, yy,
                        30.0 * Math.sin(2.0 * Math.PI * t / LOOP + th2),
                        -Math.toDegrees(th2),
                        35.0 * Math.sin(2.0 * Math.PI * t / LOOP + 2.0 * th2), true));
            }
        }
        return out;
    }

    /**
     * The ring with the lift-off ({@code intro}) or the fly-home ({@code outro}) applied, each 0..1,
     * or neither. Lift-off is a smoothstep from the stumps; the fly-home a smoothstep to 2.5 px
     * over them, where the baked cooldown tips take over. Chips fold into the stumps.
     */
    public static List<Piece> ring(double tick, double intro, double outro) {
        double k;
        double hover;
        double elapsed;
        if (intro >= 0.0) {
            if (intro >= 1.0) return ring(tick);
            double u = clamp(intro);
            k = u * u * (3.0 - 2.0 * u);
            hover = 0.0;
            elapsed = u * EASE;
        } else if (outro >= 0.0) {
            double u = clamp(outro);
            k = 1.0 - u * u * (3.0 - 2.0 * u);
            hover = HOME_HOVER;
            elapsed = u * EASE;
        } else {
            return ring(tick);
        }
        // Pick angular branches once at the transition's start. Re-selecting the shortest
        // arc each frame makes a partly blended piece jump when an angle crosses 180 degrees.
        double start = tick - elapsed;
        start -= Math.floor(start / ORBIT) * ORBIT;
        double turnStart = (Math.PI / 2.0) * start / LOOP;
        double turn = (Math.PI / 2.0) * (start + elapsed) / LOOP;
        List<Piece> eased = new ArrayList<>(12);
        List<Piece> ring = orbit(start + elapsed);
        for (int i = 0; i < ring.size(); i++) {
            Piece p = ring.get(i);
            int j = i / 3;
            double q = Math.PI / 4.0 + j * Math.PI / 2.0;
            double offset = i % 3 == 0 ? 0 : Math.toRadians(i % 3 == 1 ? 32.0 : -38.0);
            double th = q + offset + turn;
            double r = Math.hypot(p.x(), p.z());
            double th0 = q + Math.round(turnStart / (Math.PI / 2.0)) * Math.PI / 2.0;
            boolean tip = p.kind() == Kind.TIP;
            double y0 = tip ? STUMP + TIP_HALF + hover : STUMP + 0.5;
            double yaw0 = -Math.toDegrees(th0) + 45.0;
            yaw0 += 90.0 * Math.round((-Math.toDegrees(q + offset + turnStart) - yaw0) / 90.0);
            double rr = lerp(CORNER, r, k);
            double tt = lerp(th0, th, k);
            eased.add(new Piece(p.kind(), rr * Math.cos(tt), lerp(y0, p.y(), k), rr * Math.sin(tt),
                    (float) (p.tilt() * k), (float) lerp(yaw0, p.yaw(), k), (float) (p.roll() * k),
                    tip || k > 0.0, tip ? 1.0 : k));
        }
        return eased;
    }

    private static Piece piece(Kind kind, double r, double th, double y, double tilt, double yaw, double roll,
                               boolean visible) {
        return new Piece(kind, r * Math.cos(th), y, r * Math.sin(th), (float) tilt, (float) yaw, (float) roll,
                visible, 1.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
