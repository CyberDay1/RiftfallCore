package com.cyberday1.riftfall.lore;

import com.cyberday1.riftfall.home.ShatteredHomeLayout;
import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import com.cyberday1.riftfall.home.ShatteredHomeProgress;
import com.cyberday1.riftfall.lore.NoteMarkerBlock.Carrier;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * One-off pass that puts the story notes into homes built before the tiles carried note markers. Each note is
 * tried once, when its chunk is loaded, and placed only where the tile's original block (and, for a hanging
 * carrier, the block it hangs on) is still there. Completion is saved per note in ShatteredHomeProgress.
 */
public final class NoteBackfill {
    public record Entry(LoreNotes.Note note, BlockPos pos, Carrier carrier, Direction facing, ResourceLocation before,
                        Optional<BlockPos> support, Optional<ResourceLocation> supportBlock) {}

    private static final Map<ResourceLocation, List<Entry>> ENTRIES = load();

    private NoteBackfill() {}

    public static Map<ResourceLocation, List<Entry>> entries() { return ENTRIES; }

    private static Map<ResourceLocation, List<Entry>> load() {
        try (var stream = NoteBackfill.class.getResourceAsStream("/data/riftfall/home_repairs/notes_v1.json")) {
            if (stream == null) throw new IllegalStateException("Missing note back-fill");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("revision").getAsInt() != 1) throw new IllegalStateException("Unsupported note back-fill");
            var out = new HashMap<ResourceLocation, List<Entry>>();
            for (var template : root.getAsJsonObject("templates").entrySet()) {
                var id = ResourceLocation.parse(template.getKey());
                var tile = NotePlacement.tileOf(id);
                var list = new ArrayList<Entry>();
                for (var value : template.getValue().getAsJsonArray()) {
                    var e = value.getAsJsonObject();
                    var note = LoreNotes.note(e.get("note").getAsString()).orElseThrow(
                            () -> new IllegalStateException("Back-fill names an unknown note: " + e.get("note")));
                    if (!note.tile().equals(tile)) throw new IllegalStateException(note.id() + " is not on " + tile);
                    var pos = pos(e.getAsJsonArray("pos"));
                    if (pos.getX() < 0 || pos.getX() >= 100 || pos.getZ() < 0 || pos.getZ() >= 100 || pos.getY() < 1 || pos.getY() >= 76)
                        throw new IllegalStateException("Back-fill escaped its tile: " + note.id() + " " + pos);
                    var carrier = Carrier.valueOf(e.get("carrier").getAsString().toUpperCase(Locale.ROOT));
                    var facing = Direction.byName(e.get("facing").getAsString());
                    if (facing == null) throw new IllegalStateException("Bad facing for " + note.id());
                    var support = e.has("support") ? Optional.of(pos(e.getAsJsonArray("support"))) : Optional.<BlockPos>empty();
                    if (carrier.hangs() != support.isPresent()) throw new IllegalStateException("Support mismatch for " + note.id());
                    list.add(new Entry(note, pos, carrier, facing, ResourceLocation.parse(e.get("before").getAsString()), support,
                            support.map(s -> ResourceLocation.parse(e.get("support_block").getAsString()))));
                }
                out.put(id, List.copyOf(list));
            }
            return Map.copyOf(out);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load note back-fill", e);
        }
    }

    private static BlockPos pos(com.google.gson.JsonArray a) {
        return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
    }

    /** False when a frame's chunk has no entities loaded yet, so the chunk is queued again. */
    public static boolean repairChunk(ServerLevel level, LevelChunk chunk, ShatteredHomeProgress progress) {
        if (!ShatteredHomeLevels.isShatteredHome(level.dimension()) || !ShatteredHomeLayout.supports(progress.layoutVersion())) return true;
        boolean done = true;
        for (var tile : ShatteredHomeLayout.tiles(progress.layoutVersion())) {
            if (!progress.unlockedTiles().contains(tile.position())) continue;
            var origin = tile.position().blockOrigin();
            for (var e : ENTRIES.getOrDefault(tile.template(), List.of())) {
                var key = NotePlacement.key(tile.position(), e.note());
                var pos = origin.offset(e.pos());
                if (!new ChunkPos(pos).equals(chunk.getPos())) continue;
                if (NotePlacement.retired(e.note())) {
                    NotePlacement.removeRetiredSign(level, pos, e.note());
                    progress.markNotePlaced(key);
                    continue;
                }
                if (progress.notePlaced(key)) continue;
                if (e.carrier() == Carrier.FRAME && !level.areEntitiesLoaded(chunk.getPos().toLong())) { done = false; continue; }
                if (matches(level, origin, e)) NotePlacement.place(level, pos, e.carrier(), e.facing(), e.note());
                progress.markNotePlaced(key);
            }
        }
        return done;
    }

    /** The cell still holds the tile's original block, and a hanging carrier's wall is still the original wall. */
    static boolean matches(ServerLevel level, BlockPos origin, Entry e) {
        var pos = origin.offset(e.pos());
        if (!BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).equals(e.before())) return false;
        if (e.support().isPresent()) {
            var wall = level.getBlockState(origin.offset(e.support().get()));
            if (!BuiltInRegistries.BLOCK.getKey(wall.getBlock()).equals(e.supportBlock().orElseThrow())) return false;
        }
        return e.carrier() != Carrier.FRAME || level.getEntitiesOfClass(HangingEntity.class, new AABB(pos)).isEmpty();
    }
}
