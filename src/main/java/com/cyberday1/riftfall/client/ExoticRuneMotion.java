package com.cyberday1.riftfall.client;

import java.util.List;

/** Subtle eight-second suspension loop. Positions are model pixels, angles degrees. */
public final class ExoticRuneMotion {
    public static final int LOOP = 160;
    public static final List<String> PARTS = List.of("core", "frame", "tip_0", "tip_1", "tip_2", "tip_3");
    public record Pose(double x, double y, double z, double rz, double scale) {}
    private ExoticRuneMotion() {}

    public static Pose pose(String part, double ticks) {
        double t = ((ticks % LOOP) + LOOP) % LOOP;
        double wave = 2 * Math.PI * t / LOOP;
        double bob = .08 * Math.sin(wave);
        if (part.equals("core")) return new Pose(0, bob, 0, 0, 1);
        if (part.equals("frame")) return new Pose(0, bob, 0, 1.2 * Math.sin(wave), 1 + .012 * Math.sin(wave));
        int index = PARTS.indexOf(part) - 2;
        if (index < 0) throw new IllegalArgumentException("Unknown rune part: " + part);
        double phase = wave + index * Math.PI / 2;
        double radial = .12 * Math.sin(phase);
        double angle = index * Math.PI / 2;
        return new Pose(radial * Math.sin(angle), bob + radial * Math.cos(angle),
                .08 * Math.sin(phase), 0, 1);
    }
}
