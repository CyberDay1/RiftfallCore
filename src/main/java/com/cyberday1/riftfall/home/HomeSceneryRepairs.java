package com.cyberday1.riftfall.home;

import com.google.gson.JsonObject;
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

/** Exact-state scenery corrections, applied through the existing bounded chunk queue. */
public final class HomeSceneryRepairs {
    record Cell(BlockPos pos, HomeRoadDetails.State before, HomeRoadDetails.State after,
                BlockPos anchor, HomeRoadDetails.State guard) {}
    record Group(ShatteredHomeTiles.GridPos tile,String key,List<Cell> cells) {}
    private static final Map<Integer,Map<Long,List<Group>>> INDEX=load();
    private HomeSceneryRepairs() {}

    private static BlockPos pos(JsonObject row) {
        var a=row.getAsJsonArray("position");
        var p=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
        if (p.getX()<5 || p.getX()>94 || p.getZ()<5 || p.getZ()>94 || p.getY()<10 || p.getY()>39)
            throw new IllegalStateException("Scenery repair outside authored bounds: "+p);
        return p;
    }
    private static HomeRoadDetails.State state(JsonObject row) {
        var properties=new HashMap<String,String>();
        row.getAsJsonObject("Properties").entrySet().forEach(e -> properties.put(e.getKey(),e.getValue().getAsString()));
        return new HomeRoadDetails.State(ResourceLocation.parse(row.get("Name").getAsString()),Map.copyOf(properties));
    }
    private static Map<Integer,Map<Long,List<Group>>> load() {
        try (var in=HomeSceneryRepairs.class.getResourceAsStream("/data/riftfall/home_repairs/scenery_v1.json")) {
            if (in==null) throw new IllegalStateException("Missing scenery repairs");
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("revision").getAsInt()!=1) throw new IllegalStateException("Unknown scenery revision");
            var templates=new HashMap<String,List<Cell>>();
            for (var entry:root.getAsJsonObject("templates").entrySet()) {
                var cells=new ArrayList<Cell>();
                for (var value:entry.getValue().getAsJsonArray()) {
                    var row=value.getAsJsonObject();var guard=row.getAsJsonObject("guard");
                    cells.add(new Cell(pos(row),state(row.getAsJsonObject("before")),state(row.getAsJsonObject("after")),
                            pos(guard),state(guard.getAsJsonObject("state"))));
                }
                templates.put(entry.getKey(),List.copyOf(cells));
            }
            var versions=new HashMap<Integer,Map<Long,List<Group>>>();
            for (int version:List.of(ShatteredHomeLayout.VERSION)) {
                var chunks=new HashMap<Long,List<Group>>();
                for (var tile:ShatteredHomeLayout.tiles(version)) {
                    var local=templates.get(tile.template().toString());
                    if (local==null) continue;
                    var portions=new HashMap<Long,List<Cell>>();
                    for (var cell:local) {
                        var origin=tile.position().blockOrigin();var p=origin.offset(cell.pos());
                        portions.computeIfAbsent(new ChunkPos(p).toLong(),k -> new ArrayList<>()).add(
                                new Cell(p,cell.before(),cell.after(),origin.offset(cell.anchor()),cell.guard()));
                    }
                    portions.forEach((chunk,cells) -> chunks.computeIfAbsent(chunk,k -> new ArrayList<>()).add(
                            new Group(tile.position(),"scenery-v1:"+tile.template()+"@"+chunk,List.copyOf(cells))));
                }
                versions.put(version,Map.copyOf(chunks));
            }
            return Map.copyOf(versions);
        } catch (java.io.IOException e) {throw new IllegalStateException("Cannot read scenery repairs",e);}
    }

    public static boolean repairChunk(ServerLevel level,LevelChunk chunk,ShatteredHomeProgress progress) {
        return repairChunk(level,chunk,progress,BuiltInRegistries.BLOCK::getOptional);
    }
    static boolean repairChunk(ServerLevel level,LevelChunk chunk,ShatteredHomeProgress progress,
                               Function<ResourceLocation,Optional<Block>> blocks) {
        if (!ShatteredHomeLevels.isShatteredHome(level.dimension()) || !ShatteredHomeLayout.supports(progress.layoutVersion())) return true;
        boolean waiting=false;
        for (var group:INDEX.get(progress.layoutVersion()).getOrDefault(chunk.getPos().toLong(),List.of())) {
            if (!progress.unlockedTiles().contains(group.tile()) || progress.roadChunkRepaired(group.key())) continue;
            boolean complete=true;
            for (var cell:group.cells()) {
                var before=cell.before().resolve(blocks);var after=cell.after().resolve(blocks);var guard=cell.guard().resolve(blocks);
                if (before.isEmpty() || after.isEmpty() || guard.isEmpty()) {complete=false;continue;}
                if (!chunk.getBlockState(cell.pos()).equals(before.get())) continue;
                var cp=new ChunkPos(cell.anchor());
                var neighbor=cp.equals(chunk.getPos())?chunk:level.getChunkSource().getChunkNow(cp.x,cp.z);
                if (neighbor==null) {waiting=true;complete=false;continue;}
                // Do not rebuild scenery whose roof, crystal or attachment surface was edited away.
                if (!neighbor.getBlockState(cell.anchor()).equals(guard.get())) continue;
                if (!level.setBlock(cell.pos(),after.get(),Block.UPDATE_CLIENTS)) complete=false;
            }
            if (complete) progress.markRoadChunkRepaired(group.key());
        }
        return !waiting;
    }
}
