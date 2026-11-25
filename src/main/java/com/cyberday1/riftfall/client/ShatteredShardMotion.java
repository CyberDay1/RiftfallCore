package com.cyberday1.riftfall.client;

import java.util.List;

/** Original fractured tablet rig: model pixels, degrees and game ticks (16-second loop). */
public final class ShatteredShardMotion {
    public static final int LOOP = 320;
    public static final List<String> PARTS = List.of("tablet_upper", "tablet_lower", "tablet_corner", "tablet_chip",
            "tablet_spark_0", "tablet_spark_1", "tablet_spark_2", "tablet_spark_3");
    public record Pose(double x, double y, double z, double rz) {}
    private ShatteredShardMotion() {}

    public static Pose pose(String name, double ticks) {
        double t = ((ticks % LOOP) + LOOP) % LOOP;
        double wave = 2 * Math.PI * t / 80;
        return switch (name) {
            case "tablet_upper" -> new Pose(0, 7.5 + .08 * Math.sin(wave), 0, 0);
            case "tablet_lower" -> new Pose(.35 + .15 * Math.sin(wave), 6.5 + .19 * Math.sin(wave + .6), .12, 3 * Math.sin(wave));
            case "tablet_corner" -> new Pose(-5.1 + .22 * Math.cos(wave), 4.5 + .35 * Math.sin(wave), .2, -22.5 + 5 * Math.sin(wave));
            case "tablet_chip" -> new Pose(4.9 + .20 * Math.cos(wave), 13 + .3 * Math.sin(wave), .1, 22.5 + 9 * Math.sin(wave));
            default -> {
                int index = PARTS.indexOf(name) - 4;
                if (index < 0) throw new IllegalArgumentException("Unknown shard part: " + name);
                double orbit = 2 * Math.PI * t / LOOP + index * Math.PI / 2;
                yield new Pose(5.7 * Math.cos(orbit), 7.5 + 3.5 * Math.sin(orbit) + .25 * Math.sin(wave),
                        1.7 * Math.sin(orbit), 45 + 360 * t / LOOP);
            }
        };
    }
}
