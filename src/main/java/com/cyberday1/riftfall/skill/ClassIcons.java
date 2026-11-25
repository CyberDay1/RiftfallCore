package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What stands for each class on the class screen. {@code assets/riftfall/class_icons.json} maps a class id
 * to item ids, best first (the first one registered wins, a vanilla item last), or to an object
 * {@code {"texture": "<ns>:textures/...png", "items": [...]}}: a flat 16x16 sprite drawn instead of the
 * item when the resource exists, with the items as its fallback.
 */
public final class ClassIcons {

    /** Under {@code assets/riftfall/}, so a resource pack can replace it. */
    public static final String FILE = "class_icons.json";
    public static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath(RiftfallCore.MODID, FILE);
    private static final String CLASSPATH = "/assets/" + RiftfallCore.MODID + "/" + FILE;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Table shipped;

    /** Per class: the item ids in order, and the sprite if one is named. */
    public record Table(Map<String, List<String>> items, Map<String, ResourceLocation> textures) {
        public static final Table EMPTY = new Table(Map.of(), Map.of());
    }

    private ClassIcons() {
    }

    /** The item lists in this jar. */
    public static Map<String, List<String>> shipped() {
        return shippedTable().items();
    }

    /** The whole table in this jar, sprites included. */
    public static synchronized Table shippedTable() {
        if (shipped == null) {
            try (InputStream in = ClassIcons.class.getResourceAsStream(CLASSPATH)) {
                shipped = in == null ? Table.EMPTY : parseTable(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Riftfall: could not read {}: {}", CLASSPATH, e.toString());
                shipped = Table.EMPTY;
            }
        }
        return shipped;
    }

    /** Class id to item ids, in file order. A malformed entry is skipped, not fatal. */
    public static Map<String, List<String>> parse(Reader reader) {
        return parseTable(reader).items();
    }

    public static Table parseTable(Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        Map<String, List<String>> items = new LinkedHashMap<>();
        Map<String, ResourceLocation> textures = new LinkedHashMap<>();
        if (!root.isJsonObject()) {
            return new Table(items, textures);
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                JsonElement texture = object.get("texture");
                ResourceLocation location = texture != null && texture.isJsonPrimitive()
                        ? ResourceLocation.tryParse(texture.getAsString()) : null;
                if (location != null) {
                    textures.put(entry.getKey(), location);
                }
                value = object.has("items") ? object.get("items") : new JsonObject();
            }
            items.put(entry.getKey(), ids(value));
        }
        return new Table(items, textures);
    }

    private static List<String> ids(JsonElement value) {
        List<String> ids = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement id : value.getAsJsonArray()) {
                if (id.isJsonPrimitive()) {
                    ids.add(id.getAsString());
                }
            }
        } else if (value.isJsonPrimitive()) {
            ids.add(value.getAsString());
        }
        return List.copyOf(ids);
    }

    /** Whether {@code texture} is in this jar: what the probe can check without a client. */
    public static boolean shippedTexture(ResourceLocation texture) {
        try (InputStream in = ClassIcons.class.getResourceAsStream(
                "/assets/" + texture.getNamespace() + "/" + texture.getPath())) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** The first id that parses and is registered. */
    public static Optional<ResourceLocation> pick(List<String> ids, Predicate<ResourceLocation> registered) {
        for (String id : ids) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location != null && registered.test(location)) {
                return Optional.of(location);
            }
        }
        return Optional.empty();
    }

    /** The class's item in this game, from {@code table}. */
    public static Optional<ResourceLocation> pick(Map<String, List<String>> table, String classId) {
        return pick(table.getOrDefault(classId, List.of()), BuiltInRegistries.ITEM::containsKey);
    }
}
