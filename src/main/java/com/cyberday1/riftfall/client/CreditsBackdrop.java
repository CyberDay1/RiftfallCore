package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * The Shattered Home sky behind the credits, reduced on a worker thread to screen-sized copies. Goes through
 * SkyImageFiles, so a Riftfall Lite (or PNG override) pack's images are the ones read.
 */
final class CreditsBackdrop implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation STARS = RiftfallCore.id("credits/stars");
    private static final ResourceLocation NEBULA = RiftfallCore.id("credits/nebula");
    /** A 16:9 window onto the equirectangular star map, through the Milky Way. */
    private static final float U0 = 0.59F, U1 = 0.89F, V0 = 0.38F;
    private static final float STAR_LEVEL = 0.7F, NEBULA_LEVEL = 0.12F;

    private CompletableFuture<NativeImage[]> decoding;
    private int starsWidth, starsHeight, nebulaWidth, nebulaHeight;
    private boolean uploaded;
    private long readyAt;

    CreditsBackdrop() {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        decoding = CompletableFuture.supplyAsync(() -> decode(resources), Util.backgroundExecutor());
    }

    /** Black until both images are up, then a short fade in. */
    void draw(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xFF000000);
        if (!upload()) return;
        float in = Math.min(1, (Util.getMillis() - readyAt) / 600F);
        float stars = STAR_LEVEL * in;
        graphics.setColor(stars, stars, stars, 1);
        cover(graphics, STARS, starsWidth, starsHeight, width, height);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        float nebula = NEBULA_LEVEL * in;
        graphics.setColor(nebula, nebula, nebula, 1);
        cover(graphics, NEBULA, nebulaWidth, nebulaHeight, width, height);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        graphics.setColor(1, 1, 1, 1);
    }

    @Override
    public void close() {
        if (decoding != null) {
            decoding.thenAccept(CreditsBackdrop::free);
            decoding = null;
        }
        if (uploaded) {
            var textures = Minecraft.getInstance().getTextureManager();
            textures.release(STARS);
            textures.release(NEBULA);
            uploaded = false;
        }
    }

    private boolean upload() {
        if (uploaded) return true;
        if (decoding == null || !decoding.isDone()) return false;
        NativeImage[] images = decoding.join();
        decoding = null;
        if (images == null) return false;
        starsWidth = images[0].getWidth();
        starsHeight = images[0].getHeight();
        nebulaWidth = images[1].getWidth();
        nebulaHeight = images[1].getHeight();
        var textures = Minecraft.getInstance().getTextureManager();
        register(textures, STARS, images[0]);
        register(textures, NEBULA, images[1]);
        uploaded = true;
        readyAt = Util.getMillis();
        return true;
    }

    private static void register(net.minecraft.client.renderer.texture.TextureManager textures, ResourceLocation id, NativeImage image) {
        var texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        textures.register(id, texture);
    }

    /** Fills the screen, cropping whichever way the aspect overhangs. */
    private static void cover(GuiGraphics graphics, ResourceLocation id, int imageWidth, int imageHeight, int width, int height) {
        float scale = Math.max(width / (float) imageWidth, height / (float) imageHeight);
        int w = Math.round(imageWidth * scale), h = Math.round(imageHeight * scale);
        graphics.blit(id, (width - w) / 2, (height - h) / 2, w, h, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    /** One full-size image in memory at a time; null (logged) if either fails. */
    private static NativeImage[] decode(ResourceManager resources) {
        NativeImage stars = null;
        try {
            NativeImage map = SkyImageFiles.load(resources, SkyTextures.STARS).pixels();
            int x = Math.round(map.getWidth() * U0), w = Math.round(map.getWidth() * (U1 - U0));
            int h = Math.min(Math.round(w * 9 / 16F), map.getHeight() - Math.round(map.getHeight() * V0));
            stars = reduce(map, x, Math.round(map.getHeight() * V0), w, h, 1920);
            NativeImage nebula = SkyImageFiles.load(resources, SkyTextures.NEBULA).pixels();
            return new NativeImage[] {stars, reduce(nebula, 0, 0, nebula.getWidth(), nebula.getHeight(), 1024)};
        } catch (Exception e) {
            LOGGER.warn("Credits backdrop not drawn: {}", e.toString());
            if (stars != null) stars.close();
            return null;
        }
    }

    /** A copy of the rectangle at most maxWidth wide; closes the source. */
    private static NativeImage reduce(NativeImage source, int x, int y, int w, int h, int maxWidth) {
        try {
            float scale = Math.min(1, maxWidth / (float) w);
            var out = new NativeImage(NativeImage.Format.RGBA, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), false);
            source.resizeSubRectTo(x, y, w, h, out);
            return out;
        } finally {
            source.close();
        }
    }

    private static void free(NativeImage[] images) {
        if (images != null) for (var image : images) image.close();
    }
}
