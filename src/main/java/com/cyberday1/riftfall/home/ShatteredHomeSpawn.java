package com.cyberday1.riftfall.home;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Finds real ground in the 100x100 sanctuary, never an assumed legacy altitude. */
final class ShatteredHomeSpawn {
    static final int CENTER_X = 50;
    static final int APPROACH_Z = 58;
    private ShatteredHomeSpawn() {}

    static Vec3 find(ServerLevel level) {
        for (int radius = 0; radius < ShatteredHomeTiles.TILE_SIZE; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = CENTER_X + dx, z = APPROACH_Z + dz;
                    if (x < 1 || z < 1 || x >= 99 || z >= 99) continue;
                    // Level#getHeight returns min build height for an unloaded chunk. Load first,
                    // or a saved sanctuary can be mistaken for void and get a rescue pad underneath.
                    level.getChunk(x >> 4, z >> 4);
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    // Look for another surface if this one is obstructed, never a cave or an old
                    // rescue pad underneath the island in the same column.
                    BlockPos feet = new BlockPos(x, top, z);
                    if (top > level.getMinBuildHeight() && top + 1 < level.getMaxBuildHeight()
                            && safe(level, feet))
                        return Vec3.atBottomCenterOf(feet);
                }
            }
        }
        // If the sanctuary has been mined away, provide a small rescue pad above its remains.
        // Only write into air so player structures are never overwritten.
        int floorY = level.getMinBuildHeight() + 64;
        for (int x = CENTER_X-1; x <= CENTER_X+1; x++)
            for (int z = APPROACH_Z-1; z <= APPROACH_Z+1; z++)
                floorY = Math.max(floorY, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        if (floorY + 3 >= level.getMaxBuildHeight())
            throw new IllegalStateException("No safe home arrival space below the build limit");
        for (int x = CENTER_X-1; x <= CENTER_X+1; x++)
            for (int z = APPROACH_Z-1; z <= APPROACH_Z+1; z++) {
                BlockPos floor = new BlockPos(x, floorY, z);
                if (!level.getBlockState(floor).isAir())
                    throw new IllegalStateException("Home rescue pad would overwrite terrain");
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            }
        return new Vec3(CENTER_X + .5, floorY + 1, APPROACH_Z + .5);
    }

    static boolean safe(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        var state = level.getBlockState(floor);
        if (!state.isFaceSturdy(level, floor, Direction.UP) || !state.getFluidState().isEmpty()
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) return false;
        // Air-only headroom also excludes fire, berry bushes, powder snow and fluids.
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) return false;
        return level.noCollision(new AABB(feet.getX()+.2, feet.getY(), feet.getZ()+.2,
                feet.getX()+.8, feet.getY()+1.8, feet.getZ()+.8));
    }
}
