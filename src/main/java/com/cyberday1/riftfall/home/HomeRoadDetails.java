package com.cyberday1.riftfall.home;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Applies authored road details once to existing homes, with live water containment checks. */
public final class HomeRoadDetails {
    record State(ResourceLocation id, Map<String,String> properties) {
        Optional<BlockState> resolve(Function<ResourceLocation,Optional<Block>> blocks) {
            return blocks.apply(id).map(block -> {
                var state=block.defaultBlockState();
                for (var entry:properties.entrySet()) {
                    var property=block.getStateDefinition().getProperty(entry.getKey());
                    if (property==null) throw new IllegalStateException("Unknown road detail property: "+id+" "+entry.getKey());
                    state=with(state,property,entry.getValue());
                }
                return state;
            });
        }
    }
    record Cell(BlockPos pos, State before, State after, String kind) {}
    record Group(ShatteredHomeTiles.GridPos tile,String key,List<Cell> cells) {}
    private static final Map<Integer,Map<Long,List<Group>>> INDEX=load();
    private HomeRoadDetails() {}

    private static <T extends Comparable<T>> BlockState with(BlockState state,Property<T> property,String value) {
        return state.setValue(property,property.getValue(value).orElseThrow(() -> new IllegalStateException("Invalid road detail property value")));
    }
    private static State state(JsonObject json) {
        Map<String,String> properties=new HashMap<>();
        json.getAsJsonObject("Properties").entrySet().forEach(e -> properties.put(e.getKey(),e.getValue().getAsString()));
        return new State(ResourceLocation.parse(json.get("Name").getAsString()),Map.copyOf(properties));
    }
    private static Map<Integer,Map<Long,List<Group>>> load() {
        try (var in=HomeRoadDetails.class.getResourceAsStream("/data/riftfall/home_repairs/road_details_v1.json")) {
            if (in==null) throw new IllegalStateException("Missing road details");
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("revision").getAsInt()!=1) throw new IllegalStateException("Unsupported road details");
            var templates=new HashMap<String,List<Cell>>();
            for (var entry:root.getAsJsonObject("templates").entrySet()) {
                var cells=new ArrayList<Cell>();
                for (var item:entry.getValue().getAsJsonArray()) {
                    var row=item.getAsJsonObject();var a=row.getAsJsonArray("position");
                    var p=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
                    var before=state(row.getAsJsonObject("before"));var after=state(row.getAsJsonObject("after"));
                    String kind=row.get("kind").getAsString();
                    if (p.getX()<5 || p.getX()>94 || p.getZ()<5 || p.getZ()>94 || p.getY()<10 || p.getY()>75
                            || !Set.of("rock","dry_stair","wet_stair").contains(kind)
                            || !after.id().toString().equals(kind.equals("rock")?"mcwpaths:deepslate_strewn_rocky_path":"minecraft:cobbled_deepslate_stairs"))
                        throw new IllegalStateException("Invalid road detail: "+p);
                    cells.add(new Cell(p,before,after,kind));
                }
                templates.put(entry.getKey(),List.copyOf(cells));
            }
            var versions=new HashMap<Integer,Map<Long,List<Group>>>();
            for (int version:List.of(ShatteredHomeLayout.VERSION)) {
                var chunks=new HashMap<Long,List<Group>>();
                for (var tile:ShatteredHomeLayout.tiles(version)) {
                    var local=templates.get(tile.template().toString());
                    if (local==null) throw new IllegalStateException("Missing road details for "+tile.template());
                    var portions=new HashMap<Long,List<Cell>>();
                    for (var cell:local) {
                        var p=tile.position().blockOrigin().offset(cell.pos());
                        portions.computeIfAbsent(new ChunkPos(p).toLong(),k -> new ArrayList<>()).add(new Cell(p,cell.before(),cell.after(),cell.kind()));
                    }
                    portions.forEach((chunk,cells) -> chunks.computeIfAbsent(chunk,k -> new ArrayList<>()).add(
                        new Group(tile.position(),"road-details-v1:"+tile.template()+"@"+chunk,List.copyOf(cells))));
                }
                versions.put(version,Map.copyOf(chunks));
            }
            return Map.copyOf(versions);
        } catch (java.io.IOException e) {throw new IllegalStateException("Cannot read road details",e);}
    }

    /** False asks the existing deferred chunk queue to retry after neighboring chunks become FULL. */
    public static boolean repairChunk(ServerLevel level,LevelChunk chunk,ShatteredHomeProgress progress) {
        return repairChunk(level,chunk,progress,BuiltInRegistries.BLOCK::getOptional);
    }
    static boolean repairChunk(ServerLevel level,LevelChunk chunk,ShatteredHomeProgress progress,
            Function<ResourceLocation,Optional<Block>> blocks) {
        if (!ShatteredHomeLevels.isShatteredHome(level.dimension()) || !ShatteredHomeLayout.supports(progress.layoutVersion())) return true;
        boolean retry=false;
        for (var group:INDEX.get(progress.layoutVersion()).getOrDefault(chunk.getPos().toLong(),List.of())) {
            if (!progress.unlockedTiles().contains(group.tile()) || progress.roadChunkRepaired(group.key())) continue;
            boolean done=true;
            for (var cell:group.cells()) {
                var before=cell.before().resolve(blocks);var after=cell.after().resolve(blocks);
                if (before.isEmpty() || after.isEmpty()) {done=false;continue;}
                if (!chunk.getBlockState(cell.pos()).equals(before.get())) continue;
                var support=read(level,chunk,cell.pos().below());
                var above=read(level,chunk,cell.pos().above());
                if (support==null || above==null) {done=false;retry=true;continue;}
                if (!support.isCollisionShapeFullBlock(level,cell.pos().below()) || !above.isAir()) continue;
                boolean enclosed=true,waiting=false;
                if (cell.kind().equals("wet_stair")) {
                    for (var direction:Direction.Plane.HORIZONTAL) {
                        var p=cell.pos().relative(direction);var neighbor=read(level,chunk,p);
                        if (neighbor==null) {waiting=true;continue;}
                        if (!neighbor.isCollisionShapeFullBlock(level,p)) enclosed=false;
                    }
                }
                if (waiting) {done=false;retry=true;continue;}
                if (enclosed) level.setBlock(cell.pos(),after.get(),Block.UPDATE_CLIENTS);
            }
            if (done) progress.markRoadChunkRepaired(group.key());
        }
        return !retry;
    }
    private static BlockState read(ServerLevel level,LevelChunk chunk,BlockPos p) {
        var cp=new ChunkPos(p);
        var loaded=cp.equals(chunk.getPos())?chunk:level.getChunkSource().getChunkNow(cp.x,cp.z);
        return loaded==null?null:loaded.getBlockState(p);
    }
}
