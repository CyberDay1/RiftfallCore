package com.cyberday1.riftfall.worldgen.space;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.jetbrains.annotations.Nullable;

/** Reading a template id back out of a pool element. */
public final class PoolElements {

    private PoolElements() {
    }

    /**
     * The template a pool element names, or null when it does not name one.
     *
     * <p>
     * {@code SinglePoolElement} keeps its location behind a protected field with no getter, so it is
     * read back through the element's own codec &mdash; the same one that read it out of the pool
     * JSON in the first place. Going through the codec rather than {@code toString} means the answer
     * cannot drift from the file format, and an element that is not a single template (a list, a
     * feature, a runtime element with no location at all) declines to encode and is skipped.
     */
    @Nullable
    public static ResourceLocation locationOf(StructurePoolElement element) {
        if (!(element instanceof SinglePoolElement single)) {
            return null;
        }
        JsonElement encoded = SinglePoolElement.CODEC.codec()
                .encodeStart(JsonOps.INSTANCE, single).result().orElse(null);
        if (!(encoded instanceof JsonObject object)) {
            return null;
        }
        JsonElement location = object.get("location");
        return location == null || !location.isJsonPrimitive() ? null
                : ResourceLocation.tryParse(location.getAsString());
    }
}
