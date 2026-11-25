package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.RiftfallCore;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Narrow fixes for existing homes, without repasting authored tiles or player construction. */
@EventBusSubscriber(modid=RiftfallCore.MODID)
public final class HomeSurfaceRepairs {
    private static final ResourceLocation LUSH_GRASS=ResourceLocation.parse("biomeswevegone:lush_grass_block");
    private static final Map<ServerLevel,LinkedHashSet<ChunkPos>> PENDING=new WeakHashMap<>();
    private record BankBlock(BlockPos position,ResourceLocation block) {}
    private static final List<BankBlock> BANK=loadBank();
    private HomeSurfaceRepairs() {}

    private static List<BankBlock> loadBank() {
        try (var stream=HomeSurfaceRepairs.class.getResourceAsStream("/data/riftfall/home_repairs/brook_bank_v1.json")) {
            if (stream==null) throw new IllegalStateException("Missing brook bank repair");
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var result=new ArrayList<BankBlock>();
            for (var value:root.getAsJsonArray("blocks")) {
                var row=value.getAsJsonObject();var p=row.getAsJsonArray("position");
                var pos=new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());
                var block=ResourceLocation.parse(row.get("block").getAsString());
                if (pos.getX()<0 || pos.getX()>=100 || pos.getY()<10 || pos.getY()>24 || pos.getZ()<0 || pos.getZ()>=20
                        || !(block.equals(ResourceLocation.parse("minecraft:stone")) || block.equals(ResourceLocation.parse("minecraft:dirt")) || block.equals(ResourceLocation.parse("minecraft:grass_block"))))
                    throw new IllegalStateException("Repair escaped the brook headwater: "+pos);
                result.add(new BankBlock(pos,block));
            }
            return List.copyOf(result);
        } catch (java.io.IOException e) {throw new IllegalStateException("Cannot read brook bank repair",e);}
    }

    /** Run after restoring the level's progress, never on legacy/non-home dimensions. */
    public static void repairBrook(ServerLevel level,ShatteredHomeProgress progress) {
        if (!ShatteredHomeLevels.isShatteredHome(level.dimension()) || progress.brookBankRepaired()
                || !ShatteredHomeLayout.supports(progress.layoutVersion())) return;
        var tile=ShatteredHomeLayout.tiles(progress.layoutVersion()).stream().filter(t -> t.id().equals("03_brook_crossing")).findFirst().orElseThrow();
        if (!progress.unlockedTiles().contains(tile.position())) return;
        var origin=tile.position().blockOrigin();
        for (var entry:BANK) {
            var pos=origin.offset(entry.position());var existing=level.getBlockState(pos);
            // These positions were air in the defective export. Never replace a solid
            // player block, including a waterlogged block; only close air/water gaps.
            if (existing.isAir() || existing.is(Blocks.WATER))
                level.setBlock(pos,BuiltInRegistries.BLOCK.get(entry.block()).defaultBlockState(),Block.UPDATE_ALL);
        }
        progress.markBrookBankRepaired();
    }

    static BlockState grassState(BlockState state,Block alternate) {
        if (!state.is(alternate)) return state;
        var grass=Blocks.GRASS_BLOCK.defaultBlockState();
        return state.hasProperty(BlockStateProperties.SNOWY)
            ? grass.setValue(BlockStateProperties.SNOWY,state.getValue(BlockStateProperties.SNOWY)) : grass;
    }

    @SubscribeEvent public static void loaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ShatteredHomeLevels.isShatteredHome(level.dimension())) return;
        synchronized(PENDING) {PENDING.computeIfAbsent(level,k -> new LinkedHashSet<>()).add(event.getChunk().getPos());}
    }

    @SubscribeEvent public static void unloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        synchronized(PENDING) {var pending=PENDING.get(level);if (pending!=null) pending.remove(event.getChunk().getPos());}
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var alternate=BuiltInRegistries.BLOCK.getOptional(LUSH_GRASS);
        for (var level:event.getServer().getAllLevels()) {
            if (!ShatteredHomeLevels.isShatteredHome(level.dimension())) continue;
            List<ChunkPos> batch=new ArrayList<>();
            synchronized(PENDING) {
                var pending=PENDING.get(level);if (pending==null) continue;
                var it=pending.iterator();
                while(it.hasNext() && batch.size()<4) {batch.add(it.next());it.remove();}
            }
            for (var pos:batch) {
                // Chunk load events can precede FULL status. Never force-load a chunk
                // or interact with its level from inside the chunk-load callback.
                var chunk=level.getChunkSource().getChunkNow(pos.x,pos.z);
                if (chunk==null || !repairChunk(level,chunk,alternate)) {
                    synchronized(PENDING) {PENDING.computeIfAbsent(level,k -> new LinkedHashSet<>()).add(pos);}
                }
            }
        }
    }

    /** Runs the chunk passes on a just-placed tile now; chunks that must wait go on the queue. */
    public static void repairTile(ServerLevel level,ShatteredHomeTiles.GridPos tile) {
        var alternate=BuiltInRegistries.BLOCK.getOptional(LUSH_GRASS);
        var origin=tile.blockOrigin();int size=ShatteredHomeTiles.TILE_SIZE;
        for (int cx=origin.getX()>>4;cx<=(origin.getX()+size-1)>>4;cx++)
            for (int cz=origin.getZ()>>4;cz<=(origin.getZ()+size-1)>>4;cz++) {
                // Placement just loaded these; never call this from a chunk-load callback.
                var chunk=(net.minecraft.world.level.chunk.LevelChunk)level.getChunk(cx,cz);
                if (!repairChunk(level,chunk,alternate)) {
                    var pos=new ChunkPos(cx,cz);
                    synchronized(PENDING) {PENDING.computeIfAbsent(level,k -> new LinkedHashSet<>()).add(pos);}
                }
            }
    }

    /** False when a pass must retry the chunk later. */
    private static boolean repairChunk(ServerLevel level,net.minecraft.world.level.chunk.LevelChunk chunk,Optional<Block> alternate) {
        var pos=chunk.getPos();
        HomeRoadRepairs.repairChunk(level,chunk,ShatteredHomeProgress.of(level));
        HomeForgeRepairs.repairChunk(level,chunk,ShatteredHomeProgress.of(level));
        boolean sceneryDone=HomeSceneryRepairs.repairChunk(level,chunk,ShatteredHomeProgress.of(level));
        boolean notesDone=com.cyberday1.riftfall.lore.NoteBackfill.repairChunk(level,chunk,ShatteredHomeProgress.of(level));
        boolean done=HomeRoadDetails.repairChunk(level,chunk,ShatteredHomeProgress.of(level)) && notesDone && sceneryDone;
        if (alternate.isEmpty()) return done;
        var sections=chunk.getSections();
        for (int sectionIndex=0;sectionIndex<sections.length;sectionIndex++) {
            var section=sections[sectionIndex];
            if (!section.maybeHas(s -> s.is(alternate.get()))) continue;
            int baseY=level.getSectionYFromSectionIndex(sectionIndex)<<4;
            for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++) {
                var old=section.getBlockState(x,y,z);var replacement=grassState(old,alternate.get());
                if (replacement!=old) level.setBlock(new BlockPos(pos.getMinBlockX()+x,baseY+y,pos.getMinBlockZ()+z),replacement,Block.UPDATE_CLIENTS);
            }
        }
        return done;
    }
}
