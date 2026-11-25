package com.cyberday1.riftfall.worldgen.async.processors;

import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.worldgen.async.BakedRoomContext;
import com.cyberday1.riftfall.worldgen.async.SectionProcessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Replaces {@code minecraft:trial_spawner} with a rift mob spawner or no write.
 *
 * <p>
 * Async equivalent of {@link com.cyberday1.riftfall.worldgen.processor.TrialSpawnerProcessor}.
 * Authored templates carry vanilla trial spawners with empty spawn data as placeholders; this
 * processor turns them into working rift spawners or removes them entirely based on the context
 * flag. NBT is dropped because the rift spawner block entity saves and loads nothing.
 *
 * <p>
 * Pure array scan, no world reads.
 */
public final class AsyncTrialSpawner implements SectionProcessor {

    @Override
    public void process(BlockState[] blocks, net.minecraft.core.SectionPos sectionPos, BakedRoomContext ctx) {
        BlockState replacement = ctx.convertSpawners()
                ? RiftfallMarkers.block(MarkerKind.RIFT_MOB_SPAWNER).defaultBlockState()
                : null;
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] != null && blocks[i].is(Blocks.TRIAL_SPAWNER)) {
                blocks[i] = replacement;
            }
        }
    }
}