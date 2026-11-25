package com.cyberday1.riftfall.home;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One-time, air-only infill of the authored forge's incomplete entrance wall. */
public final class HomeForgeRepairs {
    private static final List<BlockPos> CELLS=load();
    private HomeForgeRepairs() {}

    private static List<BlockPos> load() {
        try (var stream=HomeForgeRepairs.class.getResourceAsStream("/data/riftfall/home_repairs/forge_wall_v1.json")) {
            if (stream==null) throw new IllegalStateException("Missing forge wall repair");
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("revision").getAsInt()!=1 || !root.get("block").getAsString().equals("minecraft:stone"))
                throw new IllegalStateException("Unsupported forge wall repair");
            var cells=new ArrayList<BlockPos>();
            for (var value:root.getAsJsonArray("cells")) {
                var a=value.getAsJsonArray();var p=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
                if (p.getX()<38 || p.getX()>62 || p.getY()<24 || p.getY()>36 || p.getZ()!=59)
                    throw new IllegalStateException("Forge repair escaped the entrance wall: "+p);
                cells.add(p);
            }
            if (cells.isEmpty() || new HashSet<>(cells).size()!=cells.size()) throw new IllegalStateException("Invalid forge wall cells");
            return List.copyOf(cells);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load forge wall repair",e); }
    }

    public static void repairChunk(ServerLevel level, LevelChunk chunk, ShatteredHomeProgress progress) {
        if (!ShatteredHomeLevels.isShatteredHome(level.dimension()) || !ShatteredHomeLayout.supports(progress.layoutVersion())) return;
        var tile=ShatteredHomeLayout.tiles(progress.layoutVersion()).stream().filter(t -> t.id().equals("forge_100x100")).findFirst().orElseThrow();
        if (!progress.unlockedTiles().contains(tile.position())) return;
        String key=tile.template()+"@"+chunk.getPos().toLong();
        if (progress.forgeWallChunkRepaired(key)) return;
        boolean touched=false;
        for (var local:CELLS) {
            var pos=tile.position().blockOrigin().offset(local);
            if (!new ChunkPos(pos).equals(chunk.getPos())) continue;
            touched=true;
            // Never overwrite player construction, liquids or the existing arch masonry.
            if (chunk.getBlockState(pos).isAir()) level.setBlock(pos,Blocks.STONE.defaultBlockState(),Block.UPDATE_CLIENTS);
        }
        if (touched) progress.markForgeWallChunkRepaired(key);
    }
}
