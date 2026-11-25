package com.cyberday1.riftfall.worldgen.async;

import net.minecraft.world.level.block.state.BlockState;

/**
 * One step in the async section-swap pipeline.
 *
 * <p>
 * Implementations receive a flat 4096-element block array for a single section and mutate it
 * in place. The context record carries everything a processor needs without threading extra
 * parameters through every call site.
 */
@FunctionalInterface
public interface SectionProcessor {

    void process(BlockState[] blocks, net.minecraft.core.SectionPos sectionPos, BakedRoomContext ctx);
}