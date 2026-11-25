package com.cyberday1.riftfall.client;

/** Smooth spatial falloff, shared by all four walls to avoid corner brightness jumps. */
public final class HomeBorderGlow {
    private HomeBorderGlow() {}

    public static float alpha(double normal, double horizontal, double vertical) {
        double distance = Math.sqrt(normal * normal + horizontal * horizontal + vertical * vertical);
        double t = Math.clamp(1.0 - distance / 12.0, 0.0, 1.0);
        return (float) (0.45 * t * t * (3.0 - 2.0 * t));
    }
}
