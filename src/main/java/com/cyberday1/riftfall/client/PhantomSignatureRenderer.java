package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.skill.PhantomSignatureEntity;
import com.cyberday1.riftfall.skill.PhantomSignaturePlan;
import com.cyberday1.riftfall.skill.PhantomSignatures;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The Phantom's signature sights, drawn from {@link PhantomSignaturePlan} by age. Two untextured layers: a translucent
 * one for the dark bodies (the moon's face, the void), additive for light (rims, halo, rings, shards).
 */
public final class PhantomSignatureRenderer extends EntityRenderer<PhantomSignatureEntity> {

    private static final int SEGMENTS = 48;
    private static final int BLOOD_BLACK = 0x2A0509, BLOOD_RED = 0x9E1028, BLOOD_GLOW = 0xE0263F, MOON_FACE = 0x5C0A14;
    private static final int VOID_BLACK = 0x0E0830, VOID_INDIGO = 0x3D1FD6, VOID_MAGENTA = 0xD13BFF;

    PhantomSignatureRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PhantomSignatures.SIGNATURE.get(), PhantomSignatureRenderer::new);
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomSignatureEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public void render(PhantomSignatureEntity sight, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        float age = sight.tickCount + partial;
        float fade = PhantomSignaturePlan.fade(sight.kind(), age);
        if (fade > .01F) {
            if (sight.kind() == PhantomSignaturePlan.Kind.BLOOD_MOON) bloodMoon(sight, age, fade, pose, buffers);
            else riftCollapse(sight, age, fade, pose, buffers);
        }
        super.render(sight, yaw, partial, pose, buffers, light);
    }

    private void bloodMoon(PhantomSignatureEntity sight, float age, float fade, PoseStack pose, MultiBufferSource buffers) {
        float r = PhantomSignaturePlan.MOON_RADIUS, height = PhantomSignaturePlan.moonHeight(age);
        float pulse = .85F + .15F * Mth.sin(age * .35F);
        pose.pushPose();
        pose.translate(0, height, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        VertexConsumer shade = buffers.getBuffer(Layers.SHADE);
        disc(pose, shade, 0, 0, r, MOON_FACE, a(235, fade), BLOOD_RED, a(245, fade));
        // Darker maria so the face reads as a moon, not a red coin.
        disc(pose, shade, -.7F, .5F, .8F, BLOOD_BLACK, a(150, fade), MOON_FACE, a(0, fade));
        disc(pose, shade, .6F, -.4F, 1.0F, BLOOD_BLACK, a(120, fade), MOON_FACE, a(0, fade));
        disc(pose, shade, .2F, .9F, .45F, BLOOD_BLACK, a(130, fade), MOON_FACE, a(0, fade));
        VertexConsumer glow = buffers.getBuffer(Layers.GLOW);
        ring(pose, glow, r * .97F, r * 1.08F, BLOOD_GLOW, a(220, fade), BLOOD_GLOW, a(120, fade));
        ring(pose, glow, r * 1.08F, r * 1.9F, BLOOD_RED, a(150 * pulse, fade), BLOOD_RED, 0);
        pose.popPose();
        // The moon's light on the ground it hangs over, and a faint shaft between them.
        float radius = sight.radius();
        pose.pushPose();
        pose.translate(0, .06, 0);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        ring(pose, glow, radius - .25F, radius + .15F, BLOOD_GLOW, a(200 * pulse, fade), BLOOD_GLOW, a(60, fade));
        disc(pose, glow, 0, 0, radius, BLOOD_RED, a(70, fade), BLOOD_RED, a(10, fade));
        pose.popPose();
        for (int side = 0; side < 2; side++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(side * 90 + age * 1.5F));
            quad(pose, glow, -r * .45F, 0, r * .45F, height, BLOOD_RED, a(10, fade), a(60, fade));
            pose.popPose();
        }
    }

    private void riftCollapse(PhantomSignatureEntity sight, float age, float fade, PoseStack pose, MultiBufferSource buffers) {
        float radius = sight.radius();
        float open = PhantomSignaturePlan.tearOpen(age), collapse = PhantomSignaturePlan.collapse(age);
        float flash = PhantomSignaturePlan.flash(sight.kind(), age);
        float centre = 1.2F;
        VertexConsumer shade = buffers.getBuffer(Layers.SHADE);
        // The void body: a dark orb facing the camera, shrinking into the tear.
        float orb = radius * .55F * collapse * open;
        pose.pushPose();
        pose.translate(0, centre, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        if (orb > .05F) disc(pose, shade, 0, 0, orb, VOID_BLACK, a(200, fade), VOID_INDIGO, a(110, fade));
        pose.popPose();
        // The tear: a tall lens turned to face the camera about the vertical only.
        float width = .7F * open * (flash > 0 ? 1 - flash : 1);
        pose.pushPose();
        pose.translate(0, centre, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-entityRenderDispatcher.camera.getYRot()));
        if (width > .02F) lens(pose, shade, width, PhantomSignaturePlan.TEAR_HEIGHT * open * .5F, VOID_BLACK, a(240, fade));
        VertexConsumer glow = buffers.getBuffer(Layers.GLOW);
        if (width > .02F) lensRim(pose, glow, width, PhantomSignaturePlan.TEAR_HEIGHT * open * .5F, VOID_MAGENTA, a(230, fade));
        pose.popPose();
        pose.pushPose();
        pose.translate(0, centre, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        if (orb > .05F) ring(pose, glow, orb * .92F, orb * 1.12F, VOID_MAGENTA, a(200, fade), VOID_INDIGO, a(40, fade));
        // Shards spiral in with the collapse.
        if (age >= PhantomSignaturePlan.TEAR_TICKS && collapse > 0) {
            for (int i = 0; i < PhantomSignaturePlan.SHARDS; i++) {
                float angle = i * Mth.TWO_PI / PhantomSignaturePlan.SHARDS + age * .18F;
                float d = radius * .9F * collapse * (.7F + .3F * ((i * 37) % 10) / 10F);
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotation(angle));
                quad(pose, glow, -.06F, d, .06F, d + .9F * collapse + .2F, i % 2 == 0 ? VOID_MAGENTA : VOID_INDIGO,
                        a(220, fade), a(40, fade));
                pose.popPose();
            }
        }
        pose.popPose();
        if (flash > 0) {
            pose.pushPose();
            pose.translate(0, .06, 0);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            float edge = radius * (.2F + flash * 1.1F);
            ring(pose, glow, edge * .82F, edge, VOID_MAGENTA, a(230 * (1 - flash), fade), VOID_INDIGO, 0);
            pose.popPose();
        }
    }

    private static int a(float alpha, float fade) {
        return Mth.clamp(Math.round(alpha * fade), 0, 255);
    }

    private static void vertex(PoseStack pose, VertexConsumer out, float x, float y, int rgb, int alpha) {
        out.addVertex(pose.last(), x, y, 0).setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha);
    }

    /** A filled disc in the pose's XY plane, centre colour fading to the edge colour. */
    private static void disc(PoseStack pose, VertexConsumer out, float cx, float cy, float r,
                             int inner, int innerAlpha, int outer, int outerAlpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
            vertex(pose, out, cx, cy, inner, innerAlpha);
            vertex(pose, out, cx, cy, inner, innerAlpha);
            vertex(pose, out, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, outer, outerAlpha);
            vertex(pose, out, cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, outer, outerAlpha);
        }
    }

    /** An annulus in the pose's XY plane. */
    private static void ring(PoseStack pose, VertexConsumer out, float r0, float r1,
                             int inner, int innerAlpha, int outer, int outerAlpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
            vertex(pose, out, Mth.cos(a0) * r0, Mth.sin(a0) * r0, inner, innerAlpha);
            vertex(pose, out, Mth.cos(a0) * r1, Mth.sin(a0) * r1, outer, outerAlpha);
            vertex(pose, out, Mth.cos(a1) * r1, Mth.sin(a1) * r1, outer, outerAlpha);
            vertex(pose, out, Mth.cos(a1) * r0, Mth.sin(a1) * r0, inner, innerAlpha);
        }
    }

    /** An upright rectangle from (x0, y0) to (x1, y1), its alpha running from bottom to top. */
    private static void quad(PoseStack pose, VertexConsumer out, float x0, float y0, float x1, float y1,
                             int rgb, int bottomAlpha, int topAlpha) {
        vertex(pose, out, x0, y0, rgb, bottomAlpha);
        vertex(pose, out, x1, y0, rgb, bottomAlpha);
        vertex(pose, out, x1, y1, rgb, topAlpha);
        vertex(pose, out, x0, y1, rgb, topAlpha);
    }

    private static float lensX(float half, float halfHeight, float y) {
        float t = y / halfHeight;
        return half * (1 - t * t);
    }

    /** A vertical lens, pointed at both ends. */
    private static void lens(PoseStack pose, VertexConsumer out, float half, float halfHeight, int rgb, int alpha) {
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            float y0 = -halfHeight + 2 * halfHeight * i / steps, y1 = -halfHeight + 2 * halfHeight * (i + 1) / steps;
            float w0 = lensX(half, halfHeight, y0), w1 = lensX(half, halfHeight, y1);
            vertex(pose, out, -w0, y0, rgb, alpha);
            vertex(pose, out, w0, y0, rgb, alpha);
            vertex(pose, out, w1, y1, rgb, alpha);
            vertex(pose, out, -w1, y1, rgb, alpha);
        }
    }

    private static void lensRim(PoseStack pose, VertexConsumer out, float half, float halfHeight, int rgb, int alpha) {
        int steps = 16;
        float edge = .12F;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < steps; i++) {
                float y0 = -halfHeight + 2 * halfHeight * i / steps, y1 = -halfHeight + 2 * halfHeight * (i + 1) / steps;
                float w0 = side * lensX(half, halfHeight, y0), w1 = side * lensX(half, halfHeight, y1);
                vertex(pose, out, w0, y0, rgb, alpha);
                vertex(pose, out, w0 + side * edge, y0, rgb, 0);
                vertex(pose, out, w1 + side * edge, y1, rgb, 0);
                vertex(pose, out, w1, y1, rgb, alpha);
            }
        }
    }

    /** The two untextured layers, built where RenderType's shards are visible. */
    private static final class Layers extends RenderType {
        static final RenderType SHADE = create("riftfall_signature_shade", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 16384, false, true, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
        static final RenderType GLOW = create("riftfall_signature_glow", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 16384, false, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));

        private Layers() {
            super("riftfall_signature", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                    () -> { }, () -> { });
        }
    }
}
