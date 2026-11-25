package com.cyberday1.riftfall.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Additive full-bright energy, depth tested but not depth writing; solid walls still occlude it. */
public final class SkillProjectileRenderer extends RenderType {
    private static final RenderType ENERGY = create("riftfall_skill_energy", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 131072, false, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));

    private SkillProjectileRenderer() {
        super("riftfall_skill_energy", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                131072, false, false, () -> { }, () -> { });
    }

    public static void render(SkillProjectileMesh.Style style, Entity entity, Vec3 previousVelocity, float scale, float partial,
                              PoseStack pose, MultiBufferSource buffers) {
        Vec3 velocity = previousVelocity == null ? entity.getDeltaMovement()
                : previousVelocity.lerp(entity.getDeltaMovement(), partial);
        Vec3 direction = velocity.lengthSqr() > .00001 ? velocity.normalize() : entity.getLookAngle();
        float age = entity.tickCount + partial;
        pose.pushPose();
        // Same forward-axis convention as Spell Engine's TOWARDS_MOTION models.
        pose.mulPose(Axis.YP.rotationDegrees(ArcaneWaveMesh.yaw(direction.x, direction.z)));
        pose.mulPose(Axis.XP.rotationDegrees(ArcaneWaveMesh.pitch(direction.y)));
        pose.scale(scale, scale, scale);
        var vertices = buffers.getBuffer(ENERGY);
        var transform = pose.last();
        SkillProjectileMesh.emit(style, age, Math.min(1, age / 1.5F), (x, y, z, r, g, b, a) ->
                vertices.addVertex(transform, x, y, z).setColor(r, g, b, a));
        pose.popPose();
    }
}
