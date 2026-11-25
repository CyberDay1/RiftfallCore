package com.cyberday1.riftfall.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Where the player put each HUD element, per client, in {@code config/riftfall/hud.json}: NeoOrigins'
 * hud.json v2 shape with {@code split_bars}. An entry without {@code xPct}/{@code yPct} keeps its element
 * docked on vanilla's columns and only carries its scale and orientation. {@code hidden} lists elements the player
 * turned off (only the cooldown slots read it so far).
 */
public final class HudPlacements {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable HudPlacements instance;

    /** Top-left as a share of the GUI size, or null for docked; scale 0.5 to 2. */
    public record Placement(@Nullable Float xPct, @Nullable Float yPct, float scale, boolean vertical) {

        public static final Placement DOCKED = new Placement(null, null, 1.0F, false);

        public boolean placed() {
            return xPct != null && yPct != null;
        }

        Placement at(float x, float y) {
            return new Placement(x, y, scale, vertical);
        }

        Placement scaled(float s) {
            return new Placement(xPct, yPct, HudLayout.clampScale(s), vertical);
        }

        Placement turned() {
            return new Placement(xPct, yPct, scale, !vertical);
        }
    }

    private final @Nullable Path file;
    private final Map<String, Placement> positions = new LinkedHashMap<>();
    private final Set<String> hidden = new TreeSet<>();
    private boolean split;

    public HudPlacements(@Nullable Path file) {
        this.file = file;
    }

    /** The client's file, read once. */
    public static HudPlacements instance() {
        if (instance == null) {
            instance = new HudPlacements(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve("riftfall").resolve("hud.json"));
            instance.load();
        }
        return instance;
    }

    public @Nullable Placement get(String id) {
        return positions.get(id);
    }

    /** The entry, or docked at 100% horizontal. */
    public Placement getOrDocked(String id) {
        return positions.getOrDefault(id, Placement.DOCKED);
    }

    public void set(String id, Placement placement) {
        positions.put(id, placement);
    }

    public void remove(String id) {
        positions.remove(id);
    }

    public boolean hidden(String id) {
        return hidden.contains(id);
    }

    public void hide(String id, boolean hide) {
        if (hide) {
            hidden.add(id);
        } else {
            hidden.remove(id);
        }
    }

    public boolean split() {
        return split;
    }

    public void split(boolean split) {
        this.split = split;
    }

    public void clear() {
        positions.clear();
        hidden.clear();
        split = false;
    }

    /** A detached copy for the editor to change; the HUD keeps reading this one until the editor closes. */
    public HudPlacements copy() {
        HudPlacements copy = new HudPlacements(file);
        copy.positions.putAll(positions);
        copy.hidden.addAll(hidden);
        copy.split = split;
        return copy;
    }

    public void replaceWith(HudPlacements other) {
        positions.clear();
        positions.putAll(other.positions);
        hidden.clear();
        hidden.addAll(other.hidden);
        split = other.split;
    }

    public void load() {
        clear();
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            read(JsonParser.parseReader(in));
        } catch (Exception e) {
            clear();
            LOGGER.warn("Riftfall HUD layout {} is unreadable, using the docked defaults: {}", file, e.getMessage());
        }
    }

    public void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(write(), out);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not save the Riftfall HUD layout to {}: {}", file, e.getMessage());
        }
    }

    void read(JsonElement root) {
        clear();
        if (root == null || !root.isJsonObject()) {
            return;
        }
        JsonObject obj = root.getAsJsonObject();
        split = obj.has("split_bars") && obj.get("split_bars").getAsBoolean();
        if (obj.has("hidden") && obj.get("hidden").isJsonArray()) {
            for (JsonElement id : obj.getAsJsonArray("hidden")) {
                if (id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) {
                    hidden.add(id.getAsString());
                }
            }
        }
        if (!obj.has("positions") || !obj.get("positions").isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("positions").entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject p = e.getValue().getAsJsonObject();
            Float x = share(p, "xPct");
            Float y = share(p, "yPct");
            boolean both = x != null && y != null;
            float scale = p.has("scale") ? HudLayout.clampScale(p.get("scale").getAsFloat()) : 1.0F;
            boolean vertical = p.has("vertical") && p.get("vertical").getAsBoolean();
            positions.put(e.getKey(), new Placement(both ? x : null, both ? y : null, scale, vertical));
        }
    }

    JsonObject write() {
        JsonObject root = new JsonObject();
        root.addProperty("split_bars", split);
        JsonObject out = new JsonObject();
        for (Map.Entry<String, Placement> e : new TreeMap<>(positions).entrySet()) {
            Placement p = e.getValue();
            JsonObject entry = new JsonObject();
            if (p.placed()) {
                entry.addProperty("xPct", round(p.xPct()));
                entry.addProperty("yPct", round(p.yPct()));
            }
            entry.addProperty("scale", p.scale());
            entry.addProperty("vertical", p.vertical());
            out.add(e.getKey(), entry);
        }
        root.add("positions", out);
        // Only when something is off, so an untouched file keeps NeoOrigins' shape.
        if (!hidden.isEmpty()) {
            com.google.gson.JsonArray off = new com.google.gson.JsonArray();
            hidden.forEach(off::add);
            root.add("hidden", off);
        }
        return root;
    }

    private static @Nullable Float share(JsonObject p, String key) {
        if (!p.has(key) || !p.get(key).isJsonPrimitive()) {
            return null;
        }
        float v = p.get(key).getAsFloat();
        return Float.isFinite(v) ? Math.max(0.0F, Math.min(1.0F, v)) : null;
    }

    private static float round(float v) {
        return Math.round(v * 10_000.0F) / 10_000.0F;
    }
}
