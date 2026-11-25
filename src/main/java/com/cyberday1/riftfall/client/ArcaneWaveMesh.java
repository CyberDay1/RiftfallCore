package com.cyberday1.riftfall.client;

/** Continuous, feathered energy ribbons. Local -Z points forward; the damaging crest is 2.5 blocks wide. */
public final class ArcaneWaveMesh {
    public static final int SEGMENTS = 48;
    private static final int BANDS = 8;
    private ArcaneWaveMesh() { }

    public static float yaw(double x, double z) { return (float) Math.toDegrees(Math.atan2(x, z)) + 180; }
    public static float pitch(double y) { return (float) Math.toDegrees(Math.asin(Math.clamp(y, -1, 1))); }

    @FunctionalInterface public interface Vertex {
        void emit(float x, float y, float z, int red, int green, int blue, int alpha);
    }

    public static void emit(float age, float opacity, Vertex vertex) {
        opacity = Math.clamp(opacity, 0, 1);
        // A bright leading crest and two separated, quieter echoes. No opaque faces or block textures.
        ribbon(age, opacity, 1, 0, .18F, false, vertex);
        ribbon(age - 2, opacity * .34F, .91F, .40F, .11F, false, vertex);
        ribbon(age - 4, opacity * .14F, .79F, .78F, .07F, false, vertex);
        // A shallow wake catches elevated viewing angles without turning the crescent into an orb.
        ribbon(age, opacity * .25F, 1, .08F, .34F, true, vertex);
    }

    private static void ribbon(float age, float opacity, float width, float behind, float thickness,
                               boolean horizontal, Vertex out) {
        for (int i = 0; i < SEGMENTS; i++) {
            float u0 = -1 + 2F * i / SEGMENTS, u1 = -1 + 2F * (i + 1) / SEGMENTS;
            for (int band = 0; band < BANDS; band++) {
                float v0 = -1 + 2F * band / BANDS, v1 = -1 + 2F * (band + 1) / BANDS;
                point(u0, v0, age, opacity, width, behind, thickness, horizontal, out);
                point(u1, v0, age, opacity, width, behind, thickness, horizontal, out);
                point(u1, v1, age, opacity, width, behind, thickness, horizontal, out);
                point(u0, v1, age, opacity, width, behind, thickness, horizontal, out);
            }
        }
    }

    private static void point(float u, float v, float age, float opacity, float width, float behind,
                              float thickness, boolean horizontal, Vertex out) {
        float taper = (float) Math.sqrt(Math.max(0, 1 - u * u));
        float feather = 1 - v * v;
        float ripple = (float) Math.sin(u * 9 - age * .33F) * .022F * taper;
        float x = u * 1.25F * width;
        float y = ripple + (horizontal ? 0 : v * thickness * taper);
        float z = -.48F + .66F * u * u + behind + (horizontal ? v * thickness * taper : 0);
        float hot = (float) Math.pow(feather, 3);
        float pulse = .94F + .06F * (float) Math.sin(age * .4F + u * 5);
        out.emit(x, y, z, (int) (139 + 102 * hot), (int) (68 + 151 * hot), 255,
                Math.round(215 * opacity * feather * taper * pulse));
    }
}
