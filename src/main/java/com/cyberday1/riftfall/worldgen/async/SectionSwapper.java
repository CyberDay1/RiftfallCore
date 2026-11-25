package com.cyberday1.riftfall.worldgen.async;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;

/** Commits sparse writes through worldgen, preserving chunk bookkeeping and untouched cells. */
public final class SectionSwapper {
    private SectionSwapper() {}
    public static void apply(WorldGenLevel level, BakedRoom room, RandomSource random) {
        var pendingFluids = new ArrayList<BlockPos>();
        var authoredSources = new HashSet<BlockPos>();
        for (var info : room.writes()) {
            var pos = info.pos();
            var state = info.state();
            var fluid = level.getFluidState(pos);
            if (info.nbt() != null) {
                Clearable.tryClear(level.getBlockEntity(pos));
                level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 20);
            }
            if (!level.setBlock(pos, state, 18))
                throw new IllegalStateException("Cannot commit baked block at " + pos);
            // WorldGenRegion creates pending block entities and updates heightmaps, light and postprocessing.
            if (info.nbt() != null) {
                var entity = level.getBlockEntity(pos);
                if (entity != null) {
                    var tag = info.nbt().copy();
                    if (entity instanceof RandomizableContainer) tag.putLong("LootTableSeed", random.nextLong());
                    entity.loadWithComponents(tag, level.registryAccess());
                    entity.setChanged();
                }
            }
            if (state.getFluidState().isSource()) {
                authoredSources.add(pos);
            } else if (state.getBlock() instanceof LiquidBlockContainer container) {
                container.placeLiquid(level, pos, state, fluid);
                if (!fluid.isSource()) pendingFluids.add(pos);
            }
        }
        // Match template waterlogging: flowing fluid may acquire an existing neighbouring source,
        // but a source authored by this template must not leak into another authored cell.
        boolean changed = true;
        var directions = new Direction[]{Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        while (changed && !pendingFluids.isEmpty()) {
            changed = false;
            var iterator = pendingFluids.iterator();
            while (iterator.hasNext()) {
                var pos = iterator.next();
                var fluid = level.getFluidState(pos);
                for (var direction : directions) {
                    if (fluid.isSource()) break;
                    var neighbour = pos.relative(direction);
                    var candidate = level.getFluidState(neighbour);
                    if (candidate.isSource() && !authoredSources.contains(neighbour)) fluid = candidate;
                }
                var state = level.getBlockState(pos);
                if (fluid.isSource() && state.getBlock() instanceof LiquidBlockContainer container) {
                    container.placeLiquid(level, pos, state, fluid);
                    changed = true;
                    iterator.remove();
                }
            }
        }
        DeferredEntitySpawner.spawnAll(level, room.entities());
    }
}
