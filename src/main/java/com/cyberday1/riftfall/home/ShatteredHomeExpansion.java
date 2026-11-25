package com.cyberday1.riftfall.home;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Ordered crafted core followed by repeatable random wilderness outside its 5x5 footprint. */
public final class ShatteredHomeExpansion {
    public record Placement(ShatteredHomeTiles.GridPos position, ResourceLocation template) {}
    private static final List<ResourceLocation> WILDERNESS = loadWilderness();

    private ShatteredHomeExpansion() {}

    private static List<ResourceLocation> loadWilderness() {
        try (var stream = ShatteredHomeExpansion.class.getResourceAsStream("/data/riftfall/home_layout/wilderness_v1.json")) {
            if (stream == null) throw new IllegalStateException("Missing home wilderness manifest");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1 || root.get("tile_size").getAsInt() != ShatteredHomeTiles.TILE_SIZE)
                throw new IllegalStateException("Unsupported home wilderness manifest");
            var templates = new ArrayList<ResourceLocation>();
            for (var value : root.getAsJsonArray("tiles")) {
                var tile = value.getAsJsonObject();
                var size = tile.getAsJsonArray("size");
                var template = ResourceLocation.parse(tile.get("template").getAsString());
                if (size.get(0).getAsInt() != 100 || size.get(2).getAsInt() != 100 || tile.get("roads").getAsBoolean()
                        || !template.getNamespace().equals("riftfall") || !template.getPath().startsWith("home/wilderness_v1/"))
                    throw new IllegalStateException("Invalid wilderness tile: " + template);
                templates.add(template);
            }
            if (templates.size() != 6 || new HashSet<>(templates).size() != templates.size())
                throw new IllegalStateException("Expected six unique wilderness templates");
            return List.copyOf(templates);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load home wilderness", e);
        }
    }

    public static List<ResourceLocation> wildernessTemplates() { return WILDERNESS; }

    /** The dimension contains the owner's UUID, giving each home its own stable random sequence. */
    public static long homeSeed(ResourceLocation dimension) {
        long seed = 0xcbf29ce484222325L;
        for (char c : dimension.toString().toCharArray()) seed = (seed ^ c) * 0x100000001b3L;
        return seed;
    }

    public static Optional<Placement> next(List<ShatteredHomeTiles.GridPos> placed, long homeSeed) {
        return next(ShatteredHomeLayout.VERSION,placed,homeSeed);
    }

    public static Optional<Placement> next(int version,List<ShatteredHomeTiles.GridPos> placed, long homeSeed) {
        var occupied = new HashSet<>(placed);
        if (!ShatteredHomeLayout.tiles(version).stream().allMatch(t -> occupied.contains(t.position()))) {
            return ShatteredHomeLayout.next(version,placed).map(t -> new Placement(t.position(), t.template()));
        }
        // The first 25 spiral cells cover exactly the crafted square, despite its different
        // internal unlock order. The first empty cell thereafter touches restored terrain.
        var position = ShatteredHomeTiles.spiralOrder(occupied.size() + 1).stream().skip(25)
            .filter(p -> !occupied.contains(p)).findFirst().orElseThrow();
        long seed = homeSeed ^ ((long) position.x() * 0x9E3779B97F4A7C15L)
            ^ ((long) position.z() * 0xC2B2AE3D27D4EB4FL);
        var template = WILDERNESS.get(new Random(seed).nextInt(WILDERNESS.size()));
        return Optional.of(new Placement(position, template));
    }
}
