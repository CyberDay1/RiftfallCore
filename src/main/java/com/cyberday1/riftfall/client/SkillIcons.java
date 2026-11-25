package com.cyberday1.riftfall.client;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/** Optional skill artwork from the installed pack. Unmapped or missing textures use the lever glyph. */
public final class SkillIcons implements ResourceManagerReloadListener {
    public static final SkillIcons INSTANCE = new SkillIcons();
    static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath("riftfall", "skill_icons.json");
    private static final Logger LOGGER = LogUtils.getLogger();
    private volatile Map<String, ResourceLocation> textures = Map.of();

    SkillIcons() {
    }

    public @Nullable ResourceLocation texture(String skill) {
        return textures.get(skill);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        Map<String, ResourceLocation> loaded = new HashMap<>();
        var resource = resources.getResource(LOCATION);
        if (resource.isPresent()) {
            try (Reader reader = resource.get().openAsReader()) {
                parse(reader).forEach((skill, texture) -> {
                    if (resources.getResource(texture).isPresent()) {
                        loaded.put(skill, texture);
                    }
                });
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Could not load skill icons; using lever glyphs", exception);
            }
        }
        // Replace, rather than merge: removing a resource pack must also remove its icons.
        textures = Map.copyOf(loaded);
    }

    static Map<String, ResourceLocation> parse(Reader reader) {
        Map<String, ResourceLocation> parsed = new HashMap<>();
        JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(entry -> {
            var value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                return;
            }
            ResourceLocation texture = ResourceLocation.tryParse(value.getAsString());
            if (texture != null && texture.getPath().startsWith("textures/") && texture.getPath().endsWith(".png")) {
                parsed.put(entry.getKey(), texture);
            }
        });
        return Map.copyOf(parsed);
    }
}
