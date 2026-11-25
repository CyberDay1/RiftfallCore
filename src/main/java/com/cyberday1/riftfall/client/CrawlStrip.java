package com.cyberday1.riftfall.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The tilted roll's visible lines drawn flat into an offscreen texture (no depth buffer) on a fixed texel grid, then
 * drawn as one quad through the tilt with the depth test off. Glyphs never move between texels; the sub-texel scroll
 * and the fade ride the quad (linear filtering, mipmaps, per-band vertex alpha) instead of the font.
 */
final class CrawlStrip implements AutoCloseable {
    /** Bands the fade is sampled over; the fade is linear in screen height, not plane height. */
    private static final int BANDS = 48;
    private static final int MAX_SIZE = 8192;

    private TextureTarget target;
    private float left, right, top, bottom, texelsPerUnit;

    /** Sizes the strip for a plane region; false if it cannot be drawn at all. */
    boolean prepare(float halfWidth, float planeTop, float planeBottom, float texelsPerUnit) {
        int limit = Math.min(MAX_SIZE, RenderSystem.maxSupportedTextureSize());
        float tpu = texelsPerUnit;
        while (tpu > 0.5F && (2 * Math.ceil(halfWidth * tpu) > limit || Math.ceil((planeBottom - planeTop) * tpu) > limit)) tpu /= 2;
        this.texelsPerUnit = tpu;
        // Even width and a top on the grid, so every line snaps to the same texels each frame.
        int w = 2 * (int) Math.ceil(halfWidth * tpu);
        top = (float) Math.floor(planeTop * tpu) / tpu;
        int h = (int) Math.ceil((planeBottom - top) * tpu);
        if (w <= 0 || h <= 0 || w > limit || h > limit) return false;
        left = -w / (2 * tpu);
        right = w / (2 * tpu);
        bottom = top + h / tpu;
        if (target == null) target = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        else if (target.width != w || target.height != h) target.resize(w, h, Minecraft.ON_OSX);
        return true;
    }

    float texelsPerUnit() {
        return texelsPerUnit;
    }

    /** Runs draw with the strip bound and a plane-unit projection, then hands the screen back. */
    void render(Runnable draw) {
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(left, right, bottom, top, 1000,
                net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane()), VertexSorting.ORTHOGRAPHIC_Z);
        try {
            draw.run();
        } finally {
            RenderSystem.restoreProjectionMatrix();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
        // Mipmapped: the strip is drawn at twice the GUI scale and shrinks further as it recedes.
        GlStateManager._bindTexture(target.getColorTextureId());
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._bindTexture(0);
    }

    /**
     * Draws the strip in the current pose, offset by offsetY plane units, premultiplied (the strip was cleared to
     * transparent black and the text blended over it). alphaAt maps a plane height (offset included) to its alpha.
     */
    void draw(PoseStack.Pose pose, float offsetY, java.util.function.DoubleUnaryOperator alphaAt) {
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        Matrix4f matrix = pose.pose();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < BANDS; i++) {
            float y0 = top + (bottom - top) * i / BANDS, y1 = top + (bottom - top) * (i + 1) / BANDS;
            float a0 = (float) alphaAt.applyAsDouble(y0 + offsetY), a1 = (float) alphaAt.applyAsDouble(y1 + offsetY);
            float v0 = 1 - (float) i / BANDS, v1 = 1 - (float) (i + 1) / BANDS;
            buffer.addVertex(matrix, left, y0 + offsetY, 0).setUv(0, v0).setColor(a0, a0, a0, a0);
            buffer.addVertex(matrix, left, y1 + offsetY, 0).setUv(0, v1).setColor(a1, a1, a1, a1);
            buffer.addVertex(matrix, right, y1 + offsetY, 0).setUv(1, v1).setColor(a1, a1, a1, a1);
            buffer.addVertex(matrix, right, y0 + offsetY, 0).setUv(1, v0).setColor(a0, a0, a0, a0);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    @Override
    public void close() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }

    /** A plane value on the strip's texel grid, so a glyph lands on the same texels every frame. */
    static float snap(float value, float texelsPerUnit) {
        return Math.round(value * texelsPerUnit) / texelsPerUnit;
    }
}
