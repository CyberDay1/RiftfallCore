package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Local, smoothly fading red glow on the personal dimension's border. */
@Mixin(LevelRenderer.class)
public abstract class MixinWorldBorderAlpha {
    @Unique private static final double RADIUS = 12.0;
    @Unique private static final double STEP = 1.0;

    @Inject(method = "renderWorldBorder", at = @At("HEAD"), cancellable = true)
    private void riftfall$replaceBorderForShatteredHome(net.minecraft.client.Camera camera, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !ShatteredHomeLevels.isShatteredHome(level.dimension())) return;
        ci.cancel();
        WorldBorder border = level.getWorldBorder();
        Vec3 pos = camera.getPosition();


        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (var edge : com.cyberday1.riftfall.home.HomeBoundary.edges(level)) {
            double normal = edge.coordinate() - (edge.xWall() ? pos.x : pos.z);
            double along = edge.xWall() ? pos.z : pos.x;
            riftfall$wall(buf, edge.xWall(), normal, edge.start() - along, edge.end() - along);
        }
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    @Unique
    private static void riftfall$wall(BufferBuilder buf, boolean xWall, double normal, double min, double max) {
        if (Math.abs(normal) >= RADIUS) return;
        double start = Math.max(min, -RADIUS);
        double end = Math.min(max, RADIUS);
        for (double u = start; u < end; u += STEP) {
            double next = Math.min(end, u + STEP);
            for (double y = -RADIUS; y < RADIUS; y += STEP) {
                riftfall$vertex(buf, xWall, normal, u, y);
                riftfall$vertex(buf, xWall, normal, next, y);
                riftfall$vertex(buf, xWall, normal, next, y + STEP);
                riftfall$vertex(buf, xWall, normal, u, y + STEP);
            }
        }
    }

    @Unique
    private static void riftfall$vertex(BufferBuilder buf, boolean xWall, double normal, double u, double y) {
        float alpha = com.cyberday1.riftfall.client.HomeBorderGlow.alpha(normal, u, y);
        buf.addVertex((float)(xWall ? normal : u), (float)y, (float)(xWall ? u : normal))
                .setColor(1.0F, 0.08F, 0.05F, alpha);
    }
}
