package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.skill.BladeRainEntity;
import com.cyberday1.riftfall.skill.BladeRainPlan;
import com.cyberday1.riftfall.skill.BladeRains;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Blade Rain's blades, ported from NeoOrigins' {@code ProjectileRainRenderer}: the "Heroes Blade" mesh (Sylvamy,
 * CC BY 4.0, see ATTRIBUTION.md) drawn once per blade, each falling near-vertically, tip first with ease-in, from high
 * above its landing point, stabbing on the tick the server lands its hit, quivering, holding, then fading with the
 * storm. Diffuse-shaded translucent steel blue at full brightness, so the fuller, bevel and guard read.
 */
public final class BladeRainRenderer extends EntityRenderer<BladeRainEntity> {

    private static final String MESH = "/assets/riftfall/geo/spectral_sword.bakedmesh";
    /** The mesh is about 20.5 units long, tip on +Z; this makes it about 1.64 blocks. */
    private static final float MESH_SCALE = 0.08F;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("riftfall",
            "textures/entity/blade_rain.png");
    private static final RenderType LAYER = RenderType.entityTranslucent(TEXTURE);
    private static final int TINT_R = 175, TINT_G = 215, TINT_B = 255;
    /** Lifts the blade's centre so its tip sinks a little into the ground: a stab, not a touch. */
    private static final float PIVOT_LIFT = 0.62F;
    private static BladeMesh mesh;

    BladeRainRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BladeRains.BLADE_RAIN.get(), BladeRainRenderer::new);
    }

    private static BladeMesh mesh() {
        if (mesh == null) mesh = BladeMesh.load(MESH, MESH_SCALE);
        return mesh;
    }

    @Override
    public ResourceLocation getTextureLocation(BladeRainEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(BladeRainEntity storm, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int light) {
        float time = storm.tickCount + partialTick;
        float fadeFrom = BladeRainPlan.LIFETIME_TICKS - BladeRainPlan.FADE_TICKS;
        float fade = time <= fadeFrom ? 1.0F : Mth.clamp((BladeRainPlan.LIFETIME_TICKS - time) / BladeRainPlan.FADE_TICKS, 0.0F, 1.0F);
        if (fade <= 0.02F) return;
        VertexConsumer out = buffers.getBuffer(LAYER);
        Vec3 centre = storm.position();
        for (int i = 0; i < BladeRainPlan.COUNT; i++) {
            float landing = BladeRainPlan.landingTick(i);
            float start = landing - BladeRainPlan.FALL_TICKS;
            if (time < start) continue;
            float progress = Mth.clamp((time - start) / BladeRainPlan.FALL_TICKS, 0.0F, 1.0F);
            float remain = 1.0F - progress * progress;
            int alpha = (int) (255 * fade * (0.35F + 0.55F * progress));
            if (alpha < 6) continue;
            double tilt = Math.toRadians(BladeRainPlan.tiltDegrees(i));
            double azimuth = BladeRainPlan.azimuth(i);
            double skyX = Math.sin(tilt) * Math.cos(azimuth);
            double skyY = Math.cos(tilt);
            double skyZ = Math.sin(tilt) * Math.sin(azimuth);
            double height = storm.fallHeight(i) * remain;
            Vec3 ground = storm.ground(i).subtract(centre);
            float quiver = progress >= 1.0F
                    ? Mth.sin((time - landing) * 0.9F) * 1.5F * Math.max(0.0F, 1.0F - (time - landing) / 6.0F) : 0.0F;
            poses.pushPose();
            poses.translate(ground.x + skyX * height, ground.y + skyY * height + PIVOT_LIFT, ground.z + skyZ * height);
            poses.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(Math.atan2(-skyX, -skyZ))));
            // +Z is the tip: 90 degrees points it straight down, less leans it along its fall.
            poses.mulPose(Axis.XP.rotationDegrees(90.0F - (float) Math.toDegrees(tilt) + quiver));
            mesh().render(poses, out, TINT_R, TINT_G, TINT_B, alpha, 0xF000F0, OverlayTexture.NO_OVERLAY);
            poses.popPose();
        }
        super.render(storm, yaw, partialTick, poses, buffers, light);
    }
}
