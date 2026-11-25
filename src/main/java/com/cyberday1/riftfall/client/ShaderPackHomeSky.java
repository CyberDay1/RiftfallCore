package com.cyberday1.riftfall.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

/** Replaces only far-depth background after Iris composites, preserving shaded world geometry. */
final class ShaderPackHomeSky {
    private ShaderPackHomeSky() {}

    static void draw(RenderLevelStageEvent event) {
        var previousShader = RenderSystem.getShader();
        int texture0 = RenderSystem.getShaderTexture(0), texture1 = RenderSystem.getShaderTexture(1);
        float[] color = RenderSystem.getShaderColor().clone();
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), writeDepth = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            // shattered_sky.vsh emits p.xyww: depth is exactly 1. Foreground, the
            // Core and the altar fail this depth test and retain the pack's shading.
            ShatteredSkyRenderer.drawAfterShaderPack(event.getModelViewMatrix(), event.getProjectionMatrix(),
                    (event.getRenderTick() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20.0);
        } finally {
            RenderSystem.setShaderTexture(0, texture0);
            RenderSystem.setShaderTexture(1, texture1);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.depthMask(writeDepth);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }
}
