package com.cyberday1.riftfall.block.holycore;

import com.cyberday1.riftfall.block.resurrection.WorldResurrectionMachinePartBlock;
import com.cyberday1.riftfall.block.resurrection.WorldResurrectionMachinePartBlock.PartPosition;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Fixed 3x3 pedestal, with a 5x5 by seven-block animation envelope. Never loads chunks. */
public final class HolyCoreStructure {
    public static final double MODEL_SCALE = 2.8;
    public static final int CLEARANCE_HEIGHT = 7;
    public static final int ALL_PARTS = 255;
    public enum Problem { NONE, UNLOADED, MISSING_PART, UNSUPPORTED, OBSTRUCTED }
    public record Check(Problem problem, BlockPos position, int parts) {
        public boolean complete() { return problem == Problem.NONE; }
    }
    private HolyCoreStructure() {}

    public static boolean loaded(Level level, BlockPos center) {
        for (int x : new int[]{-2, 2}) for (int z : new int[]{-2, 2}) {
            if (!level.hasChunkAt(center.offset(x, 0, z))) return false;
        }
        return true;
    }

    /** Used after generation and manual placement; repeated calls preserve the controller's data. */
    public static boolean initialize(ServerLevel level, BlockPos center) {
        if (!loaded(level, center)) return false;
        BlockState state = level.getBlockState(center);
        if (state.is(RiftfallBlocks.WORLD_RESURRECTION_MACHINE.get())) {
            if (!level.setBlock(center, RiftfallBlocks.HOLY_CORE.get().defaultBlockState(), Block.UPDATE_ALL)) return false;
        } else if (!state.is(RiftfallBlocks.HOLY_CORE.get())) return false;
        repair(level, center);
        return true;
    }

    /** Missing cells only: other blocks are never overwritten. Parts have no drops. */
    public static boolean repair(ServerLevel level, BlockPos center) {
        if (!loaded(level, center)) return false;
        boolean changed = false;
        for (PartPosition part : PartPosition.values()) {
            BlockPos target = center.offset(part.dx(), 0, part.dz());
            if (level.getBlockState(target).isAir()) {
                changed |= level.setBlock(target, RiftfallBlocks.WORLD_RESURRECTION_MACHINE_PART.get()
                        .defaultBlockState().setValue(WorldResurrectionMachinePartBlock.POSITION, part), Block.UPDATE_ALL);
            }
        }
        return changed;
    }

    public static Check check(Level level, BlockPos center) {
        if (!loaded(level, center)) return new Check(Problem.UNLOADED, center, 0);
        int mask = 0;
        BlockPos missing = null;
        for (PartPosition part : PartPosition.values()) {
            BlockPos pos = center.offset(part.dx(), 0, part.dz());
            BlockState state = level.getBlockState(pos);
            if (state.is(RiftfallBlocks.WORLD_RESURRECTION_MACHINE_PART.get())
                    && state.getValue(WorldResurrectionMachinePartBlock.POSITION) == part) mask |= 1 << part.ordinal();
            else if (missing == null) missing = pos;
        }
        if (missing != null) return new Check(Problem.MISSING_PART, missing, mask);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos below = center.offset(x, -1, z);
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return new Check(Problem.UNSUPPORTED, below, mask);
            }
        }
        for (int y = 1; y < CLEARANCE_HEIGHT; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos pos = center.offset(x, y, z);
            if (!level.getBlockState(pos).isAir()) return new Check(Problem.OBSTRUCTED, pos, mask);
        }
        return new Check(Problem.NONE, center, mask);
    }
}
