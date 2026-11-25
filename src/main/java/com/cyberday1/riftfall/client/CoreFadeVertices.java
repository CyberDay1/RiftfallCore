package com.cyberday1.riftfall.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Keeps baked lighting, UVs and normals while fading a repair-stage mesh. */
final class CoreFadeVertices implements VertexConsumer {
    private final VertexConsumer into;
    private final double opacity;
    CoreFadeVertices(VertexConsumer into, double opacity) {
        this.into = into;
        this.opacity = Math.clamp(opacity, 0, 1);
    }
    @Override public VertexConsumer addVertex(float x, float y, float z) { into.addVertex(x, y, z); return this; }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) {
        into.setColor(r, g, b, (int) Math.round(a * opacity)); return this;
    }
    @Override public VertexConsumer setUv(float u, float v) { into.setUv(u, v); return this; }
    @Override public VertexConsumer setUv1(int u, int v) { into.setUv1(u, v); return this; }
    @Override public VertexConsumer setUv2(int u, int v) { into.setUv2(u, v); return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { into.setNormal(x, y, z); return this; }
}
