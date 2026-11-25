package com.cyberday1.riftfall.home;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fixed geography for every home: layout_v2.json. */
public final class ShatteredHomeLayout {
    public static final int VERSION = 2;
    public record Tile(String id, ResourceLocation template, ShatteredHomeTiles.GridPos position,
                       int rank, boolean ready, Map<String, Integer> ports) {}
    private static final Map<Integer,List<Tile>> LAYOUTS = Map.of(VERSION,load(VERSION));

    private ShatteredHomeLayout() {}

    private static List<Tile> load(int version) {
        try (var stream = ShatteredHomeLayout.class.getResourceAsStream("/data/riftfall/home_layout/layout_v"+version+".json")) {
            if (stream == null) throw new IllegalStateException("Missing home layout");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("layout_version").getAsInt() != version) throw new IllegalStateException("Unsupported home layout");
            var tiles = new ArrayList<Tile>();
            for (var value : root.getAsJsonArray("tiles")) {
                var t = value.getAsJsonObject();
                var grid = t.getAsJsonArray("grid_xz");
                var ports = new HashMap<String, Integer>();
                t.getAsJsonObject("ports_and_clear_widths").entrySet().forEach(e -> ports.put(e.getKey(), e.getValue().getAsInt()));
                tiles.add(new Tile(t.get("id").getAsString(), ResourceLocation.parse(t.get("template").getAsString()),
                    new ShatteredHomeTiles.GridPos(grid.get(0).getAsInt(), grid.get(1).getAsInt()),
                    t.get("proposed_unlock_order").getAsInt(), t.get("ready").getAsBoolean(), Map.copyOf(ports)));
            }
            tiles.sort(Comparator.comparingInt(Tile::rank));
            if (tiles.size() != 25 || tiles.stream().map(Tile::position).distinct().count() != 25
                    || tiles.stream().map(Tile::id).distinct().count() != 25)
                throw new IllegalStateException("Home layout must have 25 unique positions and IDs");
            for (int i = 0; i < tiles.size(); i++) {
                var tile = tiles.get(i);
                if (tile.rank() != i + 1 || Math.abs(tile.position().x()) > 2 || Math.abs(tile.position().z()) > 2)
                    throw new IllegalStateException("Home layout must fill the 5x5 with ranks 1 through 25");
            }
            if (!tiles.getFirst().position().equals(new ShatteredHomeTiles.GridPos(0, 0)))
                throw new IllegalStateException("The sanctuary must be first");
            return List.copyOf(tiles);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load home layout", e);
        }
    }

    public static boolean supports(int version) { return LAYOUTS.containsKey(version); }
    public static List<Tile> tiles() { return tiles(VERSION); }
    public static List<Tile> tiles(int version) {
        var tiles=LAYOUTS.get(version);
        if (tiles==null) throw new IllegalArgumentException("Unsupported home layout: "+version);
        return tiles;
    }
    public static Tile starter() { return starter(VERSION); }
    public static Tile starter(int version) { return at(version,new ShatteredHomeTiles.GridPos(0, 0)).orElseThrow(); }
    public static Optional<Tile> at(ShatteredHomeTiles.GridPos position) {
        return at(VERSION,position);
    }
    public static Optional<Tile> at(int version,ShatteredHomeTiles.GridPos position) {
        return tiles(version).stream().filter(t -> t.position().equals(position)).findFirst();
    }
    public static boolean roadConnected(Tile a, Tile b) {
        int dx = b.position().x() - a.position().x(), dz = b.position().z() - a.position().z();
        String face, opposite;
        if (dx == 1 && dz == 0) { face = "E"; opposite = "W"; }
        else if (dx == -1 && dz == 0) { face = "W"; opposite = "E"; }
        else if (dx == 0 && dz == 1) { face = "S"; opposite = "N"; }
        else if (dx == 0 && dz == -1) { face = "N"; opposite = "S"; }
        else return false;
        return a.ports().containsKey(face) && Objects.equals(a.ports().get(face), b.ports().get(opposite));
    }
    /** Restore the first missing rank; never skip ahead or place an isolated island. */
    public static Optional<Tile> next(List<ShatteredHomeTiles.GridPos> placed) {
        return next(VERSION,placed);
    }
    public static Optional<Tile> next(int version,List<ShatteredHomeTiles.GridPos> placed) {
        if (placed.isEmpty()) return starter(version).ready() ? Optional.of(starter(version)) : Optional.empty();
        return tiles(version).stream().filter(t -> !placed.contains(t.position())).findFirst().filter(Tile::ready)
            .filter(t -> placed.stream().map(p -> at(version,p)).flatMap(Optional::stream)
                .anyMatch(neighbor -> roadConnected(t, neighbor)));
    }
}
