package com.cyberday1.riftfall.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * The rip's aperture, as geometry rather than as pixels.
 *
 * <p>
 * Exported by {@code generate_tear.py} beside the artwork it is measured off, because the crack
 * behind the rip has to start exactly where the rip's edge is. A smooth stand-in would drift from
 * the jagged silhouette, and the rim glow is drawn on the same curve.
 *
 * <p>
 * Read off the classpath rather than through the resource manager, deliberately: this is baked to
 * match a baked strip, so nothing may swap one without the other, and a plain static load is a pure
 * function a test can exercise with no game running.
 */
public final class RiftTearOutline {

    public static final String RESOURCE = "/assets/riftfall/textures/block/rift_tear_outline.json";

    private static RiftTearOutline mouth;

    private final float[] x;
    private final float[] y;
    private final int rows;
    private final float centreX;
    private final float centreY;

    private RiftTearOutline(float[] x, float[] y, int rows) {
        this.x = x;
        this.y = y;
        this.rows = rows;
        float sumX = 0.0F;
        float sumY = 0.0F;
        for (int at = 0; at < x.length; at++) {
            sumX += x[at];
            sumY += y[at];
        }
        this.centreX = sumX / x.length;
        this.centreY = sumY / y.length;
    }

    /** The shipped aperture, read once. */
    public static synchronized RiftTearOutline mouth() {
        if (mouth == null) {
            try (InputStream in = RiftTearOutline.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("missing " + RESOURCE);
                }
                mouth = read(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException failed) {
                throw new IllegalStateException("could not read " + RESOURCE, failed);
            }
        }
        return mouth;
    }

    /** Parses the exported ring. Pure, so a test can hand it any string it likes. */
    public static RiftTearOutline read(Reader json) {
        JsonObject root = JsonParser.parseReader(json).getAsJsonObject();
        int rows = root.get("rows").getAsInt();
        JsonArray points = root.getAsJsonArray("points");
        if (points.size() != 2 * rows || rows < 2) {
            throw new IllegalStateException(
                    "the ring is " + points.size() + " points over " + rows + " rows; it must be two");
        }

        float[] x = new float[points.size()];
        float[] y = new float[points.size()];
        for (int at = 0; at < points.size(); at++) {
            JsonArray point = points.get(at).getAsJsonArray();
            x[at] = point.get(0).getAsFloat();
            y[at] = point.get(1).getAsFloat();
        }
        return new RiftTearOutline(x, y, rows);
    }

    /** Half the opening at row {@code row}, in normalised x. Zero where the rip is pinched shut. */
    public float halfWidth(int row) {
        return (x[row] - x[x.length - 1 - row]) * 0.5F;
    }

    /** The middle of row {@code row}, in normalised x. The rip does not run straight down. */
    public float centreOf(int row) {
        return (x[row] + x[x.length - 1 - row]) * 0.5F;
    }

    /** Points round the ring: the right edge top to bottom, then the left edge back up. */
    public int points() {
        return x.length;
    }

    /** Rows the ring was sampled at. Point {@code k} and point {@code 2*rows-1-k} share row {@code k}. */
    public int rows() {
        return rows;
    }

    /** Rightward across the picture, in [0, 1]. */
    public float x(int at) {
        return x[at];
    }

    /** Downward from the top of the picture, in [0, 1]. So it reads straight across to a texture V. */
    public float y(int at) {
        return y[at];
    }

    public float centreX() {
        return centreX;
    }

    public float centreY() {
        return centreY;
    }

    /**
     * Is this point inside the aperture? Even-odd, which is right here because the ring cannot
     * self-intersect: it is one point per side per row, taken as that row's extremes.
     */
    public boolean contains(float px, float py) {
        boolean in = false;
        for (int at = 0, prior = x.length - 1; at < x.length; prior = at++) {
            boolean straddles = (y[at] > py) != (y[prior] > py);
            if (straddles && px < x[at] + (py - y[at]) * (x[prior] - x[at]) / (y[prior] - y[at])) {
                in = !in;
            }
        }
        return in;
    }
}
