package com.cyberday1.riftfall.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** A narrow, six-sided hemp rope with a little slack and alternating strand shading. */
final class GrappleRope {
    private GrappleRope() { }

    static void render(VertexConsumer vertices, PoseStack.Pose pose, Vec3 start, Vec3 end, int light) {
        Vec3 span = end.subtract(start);
        double length = span.length();
        if (length < .01) return;
        Vec3 axis = span.normalize();
        Vec3 across = axis.cross(Math.abs(axis.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 up = axis.cross(across).normalize();
        int segments = Math.max(8, (int) Math.ceil(length * 8));
        Vec3 last = null;
        for (int face = 0; face < 6; face++) {
            double a = face * Math.PI / 3, b = (face + 1) * Math.PI / 3;
            Vec3 edgeA = across.scale(Math.cos(a) * .022).add(up.scale(Math.sin(a) * .022));
            Vec3 edgeB = across.scale(Math.cos(b) * .022).add(up.scale(Math.sin(b) * .022));
            Vec3 first = start.add(edgeA);
            // Degenerate vertices separate strips without connecting different faces or ropes.
            if (last != null) vertex(vertices, pose, last, light, 1);
            vertex(vertices, pose, first, light, 1);
            for (int i = 0; i <= segments; i++) {
                double t = (double) i / segments;
                Vec3 center = start.add(span.scale(t)).add(0, -Math.sin(Math.PI * t) * Math.min(.14, length * .012), 0);
                float shade = (float) ((.78 + .22 * Math.cos(a - .6)) * ((i + face) % 3 == 0 ? .82 : 1));
                vertex(vertices, pose, center.add(edgeA), light, shade);
                last = center.add(edgeB);
                vertex(vertices, pose, last, light, shade);
            }
            vertex(vertices, pose, last, light, 1);
        }
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 point, int light, float shade) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor((int) (164 * shade), (int) (125 * shade), (int) (78 * shade), 255).setLight(light);
    }
}
