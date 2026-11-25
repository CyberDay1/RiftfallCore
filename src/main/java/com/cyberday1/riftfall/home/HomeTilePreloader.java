package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import java.util.concurrent.Executor;

/** Prepare one future tile per occupied home; never place blocks or unlock terrain early. */
@EventBusSubscriber(modid=RiftfallCore.MODID)
public final class HomeTilePreloader {
    private static final int MAX_HOMES=4;
    // Radius zero requests FULL/accessibility only, without ticking entities or blocks.
    static final TicketType<ChunkPos> TICKET=TicketType.create("riftfall_home_preview",Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<ServerLevel,Job> JOBS=new LinkedHashMap<>();
    private static int cursor;
    private HomeTilePreloader() {}

    static Optional<ShatteredHomeExpansion.Placement> next(ShatteredHomeProgress progress,ResourceLocation dimension) {
        if(progress.unlockedTiles().isEmpty() || !progress.canInsertShard())return Optional.empty();
        if(!ShatteredHomeLayout.supports(progress.layoutVersion()))return Optional.empty();
        return ShatteredHomeExpansion.next(progress.layoutVersion(),progress.unlockedTiles(),ShatteredHomeExpansion.homeSeed(dimension));
    }

    static List<ChunkPos> chunks(ShatteredHomeTiles.GridPos grid) {
        var origin=grid.blockOrigin();var result=new ArrayList<ChunkPos>();
        for(int x=origin.getX()>>4;x<=(origin.getX()+99)>>4;x++)
            for(int z=origin.getZ()>>4;z<=(origin.getZ()+99)>>4;z++)result.add(new ChunkPos(x,z));
        result.sort(Comparator.<ChunkPos>comparingLong(p -> (long)(p.x-3)*(p.x-3)+(long)(p.z-3)*(p.z-3))
                .thenComparingLong(ChunkPos::toLong));
        return List.copyOf(result);
    }

    static final class Job {
        final ServerLevel level;
        final ShatteredHomeExpansion.Placement plan;
        final List<ChunkPos> chunks;
        final int layoutVersion,placedCount;
        int requested;
        Job(ServerLevel level,ShatteredHomeExpansion.Placement plan,int layoutVersion,int placedCount,Executor worker) {
            this.level=level;this.plan=plan;this.chunks=chunks(plan.position());
            this.layoutVersion=layoutVersion;this.placedCount=placedCount;
            // Read off-thread, outside the manager's cache; placement takes it (HomeTileTemplates).
            HomeTileTemplates.prefetch(level,plan.template(),worker);
        }
        boolean advance() {
            if(requested==chunks.size())return false;
            var pos=chunks.get(requested++);
            level.getChunkSource().addRegionTicket(TICKET,pos,0,pos);
            return true;
        }
        void release() {
            for(int i=0;i<requested;i++) {
                var pos=chunks.get(i);level.getChunkSource().removeRegionTicket(TICKET,pos,0,pos);
            }
            requested=0;
            HomeTileTemplates.forget(level,plan.template());
        }
    }

    /** A global one-chunk budget, round-robin across homes rather than a burst per home. */
    static void advance(List<Job> jobs) {
        if(jobs.isEmpty())return;
        for(int i=0;i<jobs.size();i++) {
            int index=Math.floorMod(cursor++,jobs.size());
            if(jobs.get(index).advance())return;
        }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(var it=JOBS.entrySet().iterator();it.hasNext();) {
            var e=it.next();var level=e.getKey();
            var progress=ShatteredHomeProgress.of(level);var job=e.getValue();
            if(level.players().isEmpty() || progress.layoutVersion()!=job.layoutVersion
                    || progress.unlockedTiles().size()!=job.placedCount || !progress.canInsertShard()) {
                e.getValue().release();it.remove();
            }
        }
        for(var level:event.getServer().getAllLevels()) {
            if(JOBS.size()>=MAX_HOMES)break;
            if(!ShatteredHomeLevels.isShatteredHome(level.dimension()) || level.players().isEmpty() || JOBS.containsKey(level))continue;
            var progress=ShatteredHomeProgress.of(level);
            next(progress,level.dimension().location()).ifPresent(plan -> JOBS.put(level,
                    new Job(level,plan,progress.layoutVersion(),progress.unlockedTiles().size(),Util.backgroundExecutor())));
        }
        advance(List.copyOf(JOBS.values()));
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        JOBS.values().forEach(Job::release);JOBS.clear();cursor=0;HomeTileTemplates.clear();
    }
}
