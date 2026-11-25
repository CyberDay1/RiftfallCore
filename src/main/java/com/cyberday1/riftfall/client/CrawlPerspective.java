package com.cyberday1.riftfall.client;

import org.joml.Matrix4f;

/**
 * The credits plane tilted back about X, pivoting on the screen's bottom edge, seen from eyeDistance in front of
 * the screen. The pose rotation runs on the CPU; {@link #divide()} goes on the GPU model-view stack, because
 * VertexConsumer#addVertex(Matrix4f, ...) drops the w row a perspective needs.
 */
record CrawlPerspective(float centerX, float centerY, float anchorY, float tiltDegrees, float eyeDistance) {

    static CrawlPerspective flat(float centerX, float anchorY) {
        return new CrawlPerspective(centerX, anchorY, anchorY, 0, 1);
    }

    boolean tilted() {
        return tiltDegrees != 0;
    }

    /** Column-major: screen = point minus centre times z/d, all over 1 - z/d; depth flattened. */
    Matrix4f divide() {
        float d = eyeDistance;
        return new Matrix4f(1, 0, 0, 0, 0, 1, 0, 0, -centerX / d, -centerY / d, 0, -1 / d, 0, 0, 0, 1);
    }

    /** Where a line at this plane height lands, in GUI units. */
    float screenY(float planeY) {
        double t = Math.toRadians(tiltDegrees);
        double z = planeY * Math.sin(t);
        double y = anchorY + planeY * Math.cos(t);
        double w = 1 - z / eyeDistance;
        return (float) ((y - centerY * z / eyeDistance) / w);
    }

    /** How much wider than drawn a line at this plane height appears. */
    float scale(float planeY) {
        return (float) (1 / (1 - planeY * Math.sin(Math.toRadians(tiltDegrees)) / eyeDistance));
    }

    /** The plane height that lands on this screen height; screen height falls as the plane height does. */
    float planeYAt(float screenY) {
        float low = -100_000, high = 0;
        for (int i = 0; i < 64; i++) {
            float mid = (low + high) / 2;
            if (screenY(mid) < screenY) low = mid;
            else high = mid;
        }
        return (low + high) / 2;
    }
}
