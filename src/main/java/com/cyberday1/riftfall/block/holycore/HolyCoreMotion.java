package com.cyberday1.riftfall.block.holycore;

import java.util.ArrayList;
import java.util.List;

/** The authored Blockbench motion, in model pixels/degrees; one loop is sixteen seconds. */
public final class HolyCoreMotion {
    public static final int LOOP = 320;
    public static final List<String> PARTS;
    static {
        List<String> names = new ArrayList<>();
        names.add("foundation");
        for (int i = 0; i < 4; i++) { names.add("rib_" + i); names.add("halo_" + i); names.add("glyph_" + i); }
        names.add("heart");
        for (int i = 0; i < 4; i++) names.add("shell_" + i);
        names.add("crown");
        for (int i = 0; i < 8; i++) names.add("spark_" + i);
        for (int i = 0; i < 4; i++) names.add("thread_" + i);
        PARTS = List.copyOf(names);
    }
    public record Pose(double x, double y, double z, double rx, double ry, double rz,
                       double sx, double sy, double sz) {}
    private HolyCoreMotion() {}
    private static Pose p(double x, double y, double z, double rx, double ry, double rz) {
        return p(x, y, z, rx, ry, rz, 1);
    }
    private static Pose p(double x, double y, double z, double rx, double ry, double rz, double scale) {
        return new Pose(x, y, z, rx, ry, rz, scale, scale, scale);
    }
    private static double v(double stage, double... values) {
        int lo = (int) stage, hi = Math.min(4, lo + 1);
        double k = stage - lo; k = k * k * (3 - 2 * k);
        return values[lo] + (values[hi] - values[lo]) * k;
    }
    public static Pose pose(String name, double stage, double ticks) {
        // Reference pose for authored stage previews. Runtime supplies its integrated halo angle.
        double orbit = 2 * Math.PI * (((ticks % LOOP) + LOOP) % LOOP) / LOOP;
        return pose(name, stage, ticks, orbit * Math.clamp(stage, 0, 1));
    }
    public static Pose pose(String name, double stage, double ticks, double haloOrbit) {
        double s = Math.clamp(stage, 0, 4);
        double t = ((ticks % LOOP) + LOOP) % LOOP;
        double wave = 2 * Math.PI * t / 80, orbit = 2 * Math.PI * t / LOOP, power = s / 4;
        if (name.equals("foundation")) return p(0, 0, 0, 0, 0, 0);
        if (name.equals("heart")) return p(0, v(s, 8.6, 11.7, 15, 18, 19.5) + .24 * Math.sin(wave), 0,
                0, 360 * t / LOOP, 0, v(s, .25, .48, .66, .82, 1));
        if (name.equals("crown")) return p(0, v(s, 7, 9, 16, 23, 28) + .25 * power * Math.sin(wave), 0,
                0, -360 * t / LOOP, 0, v(s, .05, .1, .35, .72, 1));
        int split = name.lastIndexOf('_');
        int i = Integer.parseInt(name.substring(split + 1));
        double a = i * Math.PI / 2;
        return switch (name.substring(0, split)) {
            case "rib" -> {
                double x = (i == 0 || i == 3) ? -5.8 : 5.8, z = i < 2 ? -5.8 : 5.8;
                double sign = x < 0 ? 1 : -1;
                yield p(v(s, Math.copySign(7.6, x), x * (1 + .6 * .13), x * (1 + .3 * .13), x, x),
                        v(s, 9.2 + (i % 2) * .4, 6.5, 7.8, 8.4, 8.4)
                        + .10 * Math.sin(wave + i),
                        v(s, Math.copySign(7.6, z), z * (1 + .6 * .13), z * (1 + .3 * .13), z, z),
                        v(s, Math.copySign(18, z), 0, 0, 0, 0), 0,
                        v(s, sign * 22.5, sign * 22.5, sign * 8, 0, 0) + v(s, 1, 1.7, 1, .5, .3) * Math.sin(wave));
            }
            case "halo" -> {
                double th = a + haloOrbit, r = v(s, 11.5, 9.5, 8.6, 7.6, 6.4);
                yield p(r * Math.sin(th), v(s, 5.2 + i * .28, 10.6, 17.8, 23.5, 26.8) + .25 * Math.sin(wave + a),
                        r * Math.cos(th), v(s, 45, 22.5, 12, 5, 0), Math.toDegrees(th),
                        v(s, 22.5, -8, 4, 0, 0) * Math.sin(wave + a));
            }
            case "glyph" -> {
                double th = a - orbit, r = v(s, 3, 9, 10, 10.8, 11);
                yield p(r * Math.sin(th), v(s, 6, 8, 12.5, 17, 20.3) + v(s, .15, .5, .5, .5, .5) * Math.sin(wave + a),
                        r * Math.cos(th), 8 * Math.sin(wave + a), Math.toDegrees(th), 5 * Math.cos(wave + a),
                        v(s, .16, .40, .62, .85, 1));
            }
            case "shell" -> {
                double r = v(s, 5.4, 6.3, 5.8, 5, 4.45) + .12 * Math.sin(wave + a);
                yield p(r * Math.sin(a), v(s, 10.3 + (i % 2) * .4, 10.5 + (i % 2), 14.5, 18, 19.5)
                        + .25 * Math.sin(wave + a), r * Math.cos(a), v(s, 45, 30, 15, 5, 0), Math.toDegrees(a),
                        v(s, 22.5, 12, 6, 2, 0) * Math.sin(wave + a));
            }
            case "spark" -> {
                double th = i * Math.PI / 4 + orbit * (i % 2 == 1 ? 1 : -1);
                double r = v(s, 2 + (i % 3) * .4, 5.3 + (i % 3) * 1.6, 5.3 + (i % 3) * 1.6,
                        5.3 + (i % 3) * 1.6, 5.3 + (i % 3) * 1.6);
                yield p(r * Math.sin(th), v(s, 6.9, 10, 14, 18, 21)
                                + v(s, .35, 3.8, 3.8, 3.8, 3.8) * Math.sin(wave + i * Math.PI / 4),
                        r * Math.cos(th), 0, 0, 45 + Math.toDegrees(orbit), .35 + .65 * power);
            }
            case "thread" -> {
                double th = a + orbit;
                yield p(2.7 * Math.sin(th), v(s, 5, 6, 8, 11, 13), 2.7 * Math.cos(th), 0, Math.toDegrees(th), 0,
                        v(s, .02, .18, .4, .66, 1));
            }
            default -> throw new IllegalArgumentException(name);
        };
    }
}
