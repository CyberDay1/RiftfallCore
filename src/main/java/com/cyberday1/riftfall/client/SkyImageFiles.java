package com.cyberday1.riftfall.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Finds and decodes a sky image. It ships as JPEG; a PNG at the same path (a resource pack's) wins, so a pack can
 * override with either. NativeImage.read takes PNG only, so a JPEG goes through stb_image directly.
 */
final class SkyImageFiles {
    private SkyImageFiles() {}

    /** The file for a sky image id (its path without an extension). */
    record Found(ResourceLocation path, Resource resource) {
        boolean png() { return path.getPath().endsWith(".png"); }
    }

    static Optional<Found> find(ResourceManager resources, ResourceLocation id) {
        for (var extension : new String[] {".png", ".jpg"}) {
            var path = id.withSuffix(extension);
            var resource = resources.getResource(path);
            if (resource.isPresent()) return Optional.of(new Found(path, resource.get()));
        }
        return Optional.empty();
    }

    /** Decoded pixels (the caller closes them) and the file's texture metadata. */
    record Decoded(NativeImage pixels, TextureMetadataSection metadata) {}

    /** Blur and clamp unless the file's .mcmeta says otherwise. */
    static Decoded load(ResourceManager resources, ResourceLocation id) throws IOException {
        var found = find(resources, id).orElseThrow(() -> new FileNotFoundException("no .png or .jpg"));
        var pixels = decode(found);
        try {
            var metadata = found.resource().metadata().getSection(TextureMetadataSection.SERIALIZER);
            return new Decoded(pixels, metadata.orElseGet(() -> new TextureMetadataSection(true, true)));
        } catch (IOException | RuntimeException e) {
            pixels.close();
            throw new IOException(found.path() + ": unreadable .mcmeta", e);
        }
    }

    /** RGBA, as NativeImage.read gives a PNG; the caller closes it. */
    static NativeImage decode(Found found) throws IOException {
        try (InputStream in = found.resource().open()) {
            if (found.png()) return NativeImage.read(in);
            ByteBuffer encoded = TextureUtil.readResource(in);
            try {
                encoded.rewind();
                return decodeJpeg(encoded);
            } finally {
                MemoryUtil.memFree(encoded);
            }
        }
    }

    /** Decodes with stb_image into an RGBA NativeImage that owns, and frees, stb's buffer. */
    static NativeImage decodeJpeg(ByteBuffer encoded) throws IOException {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var width = stack.mallocInt(1);
            var height = stack.mallocInt(1);
            var channels = stack.mallocInt(1);
            // Forced to 4 channels: an RGB upload would set GL_UNPACK_ALIGNMENT to 3, which GL rejects.
            ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
            if (pixels == null) throw new IOException("Could not decode image: " + STBImage.stbi_failure_reason());
            try {
                return new NativeImage(NativeImage.Format.RGBA, width.get(0), height.get(0), true, MemoryUtil.memAddress(pixels));
            } catch (RuntimeException e) {
                STBImage.stbi_image_free(pixels);
                throw e;
            }
        }
    }
}
