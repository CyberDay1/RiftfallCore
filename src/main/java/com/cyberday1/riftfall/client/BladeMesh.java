package com.cyberday1.riftfall.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A pre-baked triangle mesh ({@code .bakedmesh}, "NBM1"), ported from NeoOrigins' {@code BakedMeshModel}: triangles
 * stored as degenerate quads, so the entity vertex path draws them as they are. Little-endian layout: "NBM1", a quad
 * count, a bounding radius, then 4 vertices a quad of x, y, z, u, v, nx, ny, nz.
 */
final class BladeMesh {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLOATS = 8;
    private static final int CORNERS = 4;

    private final float[] data;
    private final int quads;

    private BladeMesh(float[] data, int quads) {
        this.data = data;
        this.quads = quads;
    }

    /** Loads {@code path} from the classpath, every position times {@code scale}; a tiny quad when it cannot. */
    static BladeMesh load(String path, float scale) {
        try (InputStream in = BladeMesh.class.getResourceAsStream(path)) {
            if (in == null) {
                LOGGER.error("Baked mesh not found: {}", path);
                return fallback();
            }
            ByteBuffer bb = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            if (bb.remaining() < 12 || bb.get() != 'N' || bb.get() != 'B' || bb.get() != 'M' || bb.get() != '1') {
                LOGGER.error("{} is not an NBM1 baked mesh", path);
                return fallback();
            }
            int quads = bb.getInt();
            bb.getFloat();
            int floats = quads * CORNERS * FLOATS;
            if (quads <= 0 || bb.remaining() < floats * 4) {
                LOGGER.error("{} is truncated", path);
                return fallback();
            }
            float[] data = new float[floats];
            for (int v = 0; v < quads * CORNERS; v++) {
                int off = v * FLOATS;
                for (int k = 0; k < FLOATS; k++) {
                    data[off + k] = bb.getFloat() * (k < 3 ? scale : 1.0F);
                }
            }
            return new BladeMesh(data, quads);
        } catch (Exception failure) {
            LOGGER.error("Failed to load baked mesh {}: {}", path, failure.toString());
            return fallback();
        }
    }

    private static BladeMesh fallback() {
        float h = 0.125F;
        return new BladeMesh(new float[]{
                -h, -h, 0, .5F, .5F, 0, 0, 1,
                h, -h, 0, .5F, .5F, 0, 0, 1,
                h, h, 0, .5F, .5F, 0, 0, 1,
                -h, h, 0, .5F, .5F, 0, 0, 1}, 1);
    }

    void render(PoseStack poses, VertexConsumer out, int r, int g, int b, int a, int light, int overlay) {
        PoseStack.Pose pose = poses.last();
        for (int v = 0; v < quads * CORNERS; v++) {
            int off = v * FLOATS;
            out.addVertex(pose, data[off], data[off + 1], data[off + 2])
                    .setColor(r, g, b, a)
                    .setUv(data[off + 3], data[off + 4])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, data[off + 5], data[off + 6], data[off + 7]);
        }
    }
}
