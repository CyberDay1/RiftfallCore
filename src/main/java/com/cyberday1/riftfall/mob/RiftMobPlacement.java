package com.cyberday1.riftfall.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.function.Predicate;

/** Bounded nearest-first search using the mob's finalized dimensions, including slime size. */
public final class RiftMobPlacement {
    private static final int RADIUS = 6;
    private static final int[] HEIGHTS = {0, 1, -1, 2, -2};
    private RiftMobPlacement() {}

    public static boolean place(ServerLevel level, Mob mob, BlockPos origin) {
        return find(origin, pos -> {
            mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            return fits(level, mob);
        }).isPresent();
    }

    static Optional<BlockPos> find(BlockPos origin, Predicate<BlockPos> fits) {
        for (int radius = 0; radius <= RADIUS; radius++) {
            for (int dy : HEIGHTS) for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var pos = origin.offset(dx, dy, dz);
                    if (fits.test(pos)) return Optional.of(pos);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean fits(ServerLevel level, Mob mob) {
        var box = mob.getBoundingBox();
        if (box.minY < level.getMinBuildHeight() + 1 || box.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        // Collision queries must never generate neighbouring chunks on the server tick.
        for (int x = Mth.floor(box.minX) >> 4; x <= Mth.floor(box.maxX) >> 4; x++)
            for (int z = Mth.floor(box.minZ) >> 4; z <= Mth.floor(box.maxZ) >> 4; z++)
                if (!level.hasChunk(x, z)) return false;
        if (!level.noCollision(mob, box) || level.containsAnyLiquid(box)
                || !level.getEntities(mob, box.inflate(0.15), e -> e instanceof LivingEntity && e.isAlive()).isEmpty())
            return false;
        // Support the whole footprint, not just one foot hanging over a ledge.
        for (int x = Mth.floor(box.minX + 0.001); x <= Mth.floor(box.maxX - 0.001); x++)
            for (int z = Mth.floor(box.minZ + 0.001); z <= Mth.floor(box.maxZ - 0.001); z++) {
                var floor = BlockPos.containing(x, box.minY - 0.01, z);
                if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
            }
        return true;
    }
}
