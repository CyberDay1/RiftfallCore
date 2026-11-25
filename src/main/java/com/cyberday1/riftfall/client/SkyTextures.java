package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The two sky images, about 385 MB of VRAM (96 MB with Riftfall Lite): decoded on a worker thread, registered once
 * both are ready, and released whenever the client is not in the home dimension. Until they are ready the sky draws
 * nothing rather than letting a bind decode them on the render thread. A resource reload decodes them again the
 * same way, the old pair drawing until the new one is ready. A pair that fails to decode is logged once and the sky
 * stays empty until the client next enters the home.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class SkyTextures {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Texture ids, and the image paths without an extension (SkyImageFiles picks .png or .jpg). */
    static final ResourceLocation NEBULA = RiftfallCore.id("textures/sky/carina_nebula");
    static final ResourceLocation STARS = RiftfallCore.id("textures/sky/nasa_stars_8k");

    /** Render thread only. */
    private static Image[] decoding;
    private static boolean registered, stale, failed;

    private SkyTextures() {}

    /** True once both images are bound to their ids; starts the decode otherwise. */
    static boolean ready() {
        if (decoding == null && !failed && (!registered || stale)) {
            decoding = new Image[] {new Image(STARS), new Image(NEBULA)};
            stale = false;
        }
        if (decoding != null && decoding[0].decoded() && decoding[1].decoded()) {
            String failure = null;
            for (var image : decoding) if (failure == null) failure = image.failure();
            if (failure != null) {
                LOGGER.warn("Shattered Home sky not drawn: {}", failure);
                for (var image : decoding) image.discard();
                decoding = null;
                failed = true;
                return registered;
            }
            var textures = Minecraft.getInstance().getTextureManager();
            // Replaces and closes an older pair after a reload.
            for (var image : decoding) textures.register(image.location(), image);
            decoding = null;
            registered = true;
        }
        return registered;
    }

    static void release() {
        if (decoding != null) {
            for (var image : decoding) image.discard();
            decoding = null;
        }
        if (registered) {
            var textures = Minecraft.getInstance().getTextureManager();
            textures.release(STARS);
            textures.release(NEBULA);
            registered = false;
        }
        stale = false;
        failed = false;
    }

    /** Starts the decode as the home loads, before its first frame asks; lets go of both anywhere else. */
    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level != null && ShatteredHomeLevels.isShatteredHome(level.dimension())) ready();
        else if (registered || decoding != null || failed) release();
    }

    /** A {@link SimpleTexture} whose image was decoded off the render thread. */
    private static final class Image extends SimpleTexture {
        private CompletableFuture<TextureImage> image;

        Image(ResourceLocation id) {
            super(id);
            var resources = Minecraft.getInstance().getResourceManager();
            image = CompletableFuture.supplyAsync(() -> load(resources, id), Util.backgroundExecutor());
        }

        ResourceLocation location() { return location; }

        boolean decoded() { return image == null || image.isDone(); }

        /** Why the decode failed, or null; only once {@link #decoded()}. */
        String failure() {
            if (image == null) return null;
            try {
                image.join().throwIfError();
                return null;
            } catch (IOException | RuntimeException e) {
                return location + ": " + e.getMessage();
            }
        }

        /** Left before it was registered: free the decoded pixels, now or when the worker finishes. */
        void discard() {
            if (image != null) image.thenAccept(TextureImage::close);
            image = null;
        }

        @Override
        protected TextureImage getTextureImage(ResourceManager resources) {
            if (image == null) return load(resources, location);
            var done = image.join();
            image = null;
            return done;
        }

        /** A resource reload: keep drawing this image and decode the new one off the render thread. */
        @Override
        public void reset(TextureManager textures, ResourceManager resources, ResourceLocation path, Executor executor) {
            stale = true;
        }

        private static TextureImage load(ResourceManager resources, ResourceLocation id) {
            try {
                var decoded = SkyImageFiles.load(resources, id);
                return new TextureImage(decoded.metadata(), decoded.pixels());
            } catch (IOException e) {
                return new TextureImage(e);
            } catch (RuntimeException e) {
                return new TextureImage(new IOException(e));
            }
        }
    }
}
