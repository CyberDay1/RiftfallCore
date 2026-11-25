package com.cyberday1.riftfall.home;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Replaces original paving in loaded chunks without repasting tiles or loading neighbors. */
public final class HomeRoadRepairs {
    private record Cell(BlockPos position,ResourceLocation before,ResourceLocation after) {}
    private record Group(ShatteredHomeTiles.GridPos tile,String key,List<Cell> cells) {}
    private static final Map<Integer,Map<Long,List<Group>>> INDEX=load();
    private HomeRoadRepairs() {}

    private static Map<Integer,Map<Long,List<Group>>> load() {
        try (var stream=HomeRoadRepairs.class.getResourceAsStream("/data/riftfall/home_repairs/roads_v1.json")) {
            if (stream==null) throw new IllegalStateException("Missing shared road repairs");
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("revision").getAsInt()!=1) throw new IllegalStateException("Unsupported road repair");
            Map<String,List<Cell>> templates=new HashMap<>();
            for (var entry:root.getAsJsonObject("templates").entrySet()) {
                var template=entry.getValue().getAsJsonObject();List<ResourceLocation> palette=new ArrayList<>();
                for (var value:template.getAsJsonArray("palette")) {
                    var state=value.getAsJsonObject();
                    if (!state.getAsJsonObject("Properties").isEmpty()) throw new IllegalStateException("Road repair must use full paving blocks");
                    palette.add(ResourceLocation.parse(state.get("Name").getAsString()));
                }
                List<Cell> cells=new ArrayList<>();
                for (var value:template.getAsJsonArray("cells")) {
                    var row=value.getAsJsonArray();var p=new BlockPos(row.get(0).getAsInt(),row.get(1).getAsInt(),row.get(2).getAsInt());
                    var after=palette.get(row.get(4).getAsInt());
                    if (p.getX()<0 || p.getX()>=100 || p.getZ()<0 || p.getZ()>=100 || p.getY()<10 || p.getY()>=72
                            || !(after.toString().equals("chipped:flat_cobblestone_tiles") || after.toString().equals("chipped:cracked_flat_cobblestone_tiles")))
                        throw new IllegalStateException("Invalid shared road repair: "+p);
                    cells.add(new Cell(p,palette.get(row.get(3).getAsInt()),after));
                }
                templates.put(entry.getKey(),List.copyOf(cells));
            }
            Map<Integer,Map<Long,List<Group>>> versions=new HashMap<>();
            for (int version:List.of(ShatteredHomeLayout.VERSION)) {
                Map<Long,List<Group>> chunks=new HashMap<>();
                for (var tile:ShatteredHomeLayout.tiles(version)) {
                    var cells=templates.get(tile.template().toString());
                    if (cells==null) throw new IllegalStateException("Missing roads for "+tile.template());
                    Map<Long,List<Cell>> grouped=new HashMap<>();
                    for (var cell:cells) {
                        var p=tile.position().blockOrigin().offset(cell.position());
                        grouped.computeIfAbsent(new ChunkPos(p).toLong(),k -> new ArrayList<>()).add(new Cell(p,cell.before(),cell.after()));
                    }
                    grouped.forEach((chunk,part) -> chunks.computeIfAbsent(chunk,k -> new ArrayList<>()).add(
                            new Group(tile.position(),tile.template()+"@"+chunk,List.copyOf(part))));
                }
                chunks.replaceAll((k,v) -> List.copyOf(v));versions.put(version,Map.copyOf(chunks));
            }
            return Map.copyOf(versions);
        } catch (java.io.IOException e) {throw new IllegalStateException("Cannot load road repairs",e);}
    }

    /** Progress keys of one tile's road groups, for the rig probe. */
    public static List<String> keys(int version,ShatteredHomeTiles.GridPos tile) {
        return INDEX.get(version).values().stream().flatMap(List::stream).filter(g -> g.tile().equals(tile)).map(Group::key).sorted().toList();
    }

    public static void repairChunk(ServerLevel level,LevelChunk chunk,ShatteredHomeProgress progress) {
        repairChunk(level,chunk,progress,BuiltInRegistries.BLOCK::getOptional);
    }

    static void repairChunk(ServerLevel level,LevelChunk chunk,ShatteredHomeProgress progress,
                            Function<ResourceLocation,Optional<Block>> blocks) {
        if (!ShatteredHomeLevels.isShatteredHome(level.dimension()) || !ShatteredHomeLayout.supports(progress.layoutVersion())) return;
        for (var group:INDEX.get(progress.layoutVersion()).getOrDefault(chunk.getPos().toLong(),List.of())) {
            if (!progress.unlockedTiles().contains(group.tile()) || progress.roadChunkRepaired(group.key())) continue;
            // Missing optional blocks must not erase paths or mark an incomplete repair done.
            if (group.cells().stream().anyMatch(c -> blocks.apply(c.before()).isEmpty() || blocks.apply(c.after()).isEmpty())) continue;
            for (var cell:group.cells()) {
                var before=blocks.apply(cell.before()).orElseThrow().defaultBlockState();
                var after=blocks.apply(cell.after()).orElseThrow().defaultBlockState();
                if (chunk.getBlockState(cell.position()).equals(before))
                    level.setBlock(cell.position(),after,Block.UPDATE_CLIENTS);
            }
            // A 100-block tile can share a chunk with a still-locked neighbor.
            // Track each tile's portion separately, so later unlocks are not skipped.
            progress.markRoadChunkRepaired(group.key());
        }
    }
}
