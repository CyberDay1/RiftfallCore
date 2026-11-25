package com.cyberday1.riftfall.worldgen.async.processors;

import com.cyberday1.riftfall.block.barrel.BarrelTier;
import com.cyberday1.riftfall.block.barrel.RiftBarrelBlock;
import com.cyberday1.riftfall.block.barrel.RiftBarrels;
import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.MarkerShapes;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.worldgen.async.BakedRoomContext;
import com.cyberday1.riftfall.worldgen.async.SectionProcessor;
import com.cyberday1.riftfall.worldgen.barrel.BarrelRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Async rewrite of {@code BarrelConversionProcessor} as a pure function over flat arrays.
 *
 * <p>
 * Identifies chest markers in the section's block array, resolves barrel tier from the rift seed
 * and absolute position, and replaces with either a barrel state or air. Tally operations are recorded only after the column commits successfully.
 */
public final class AsyncBarrelConversion implements SectionProcessor {

    @Override
    public void process(BlockState[] blocks, net.minecraft.core.SectionPos sectionPos, BakedRoomContext ctx) {
        BarrelRules rules = ctx.barrelRules();
        long riftSeed = ctx.riftSeed();
        int baseY = sectionPos.minBlockY();

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = (x & 15) | ((z & 15) << 4) | ((y & 15) << 8);
                    BlockState input = blocks[index];
                    if (input == null) continue;
                    if (input.getBlock() != RiftfallMarkers.block(MarkerKind.RIFT_CHEST)) {
                        continue;
                    }

                    BlockPos absPos = new BlockPos(sectionPos.minBlockX() + x, baseY + y, sectionPos.minBlockZ() + z);
                    @Nullable BarrelTier tier = rules.resolve(riftSeed, absPos);
                    if (tier == null) {
                        blocks[index] = Blocks.AIR.defaultBlockState();
                    } else {
                        blocks[index] = RiftBarrels.block(tier).defaultBlockState()
                                .setValue(RiftBarrelBlock.FACING,
                                        input.getValue(MarkerShapes.RiftChestMarkerBlock.FACING));
                    }
                }
            }
        }
    }
}