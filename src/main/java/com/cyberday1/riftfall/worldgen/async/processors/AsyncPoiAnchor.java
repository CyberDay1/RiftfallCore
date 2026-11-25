package com.cyberday1.riftfall.worldgen.async.processors;

import com.cyberday1.riftfall.block.marker.PoiAnchorBlock;
import com.cyberday1.riftfall.block.marker.PoiAnchors;
import com.cyberday1.riftfall.worldgen.async.BakedRoomContext;
import com.cyberday1.riftfall.worldgen.async.SectionProcessor;
import net.minecraft.world.level.block.state.BlockState;


/** Replaces authoring anchors with their category's final state. Worldgen registers actual POIs. */
public final class AsyncPoiAnchor implements SectionProcessor {

    @Override
    public void process(BlockState[] blocks, net.minecraft.core.SectionPos sectionPos, BakedRoomContext ctx) {

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = (x & 15) | ((z & 15) << 4) | ((y & 15) << 8);
                    BlockState input = blocks[index];
                    if (input == null) continue;
                    if (!(input.getBlock() instanceof PoiAnchorBlock anchor)) {
                        continue;
                    }

                    blocks[index] = PoiAnchors.finalState(anchor.kind());

                }
            }
        }
    }
}