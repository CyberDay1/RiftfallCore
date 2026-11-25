package com.cyberday1.riftfall.block.holycore;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shallow stone lip and inset floor, shared by the nine interactive socket cells. */
public final class WorldCoreSocket {
    private static final VoxelShape WHOLE = Shapes.or(
            Block.box(-8, 0, -8, 24, .5, 24),
            Block.box(-10, 0, -14, 26, 2, -8),
            Block.box(-10, 0, 24, 26, 2, 30),
            Block.box(-14, 0, -8, -8, 2, 24),
            Block.box(24, 0, -8, 30, 2, 24));

    private WorldCoreSocket() {}

    public static VoxelShape cell(int dx, int dz) {
        return Shapes.join(WHOLE, Shapes.block().move(dx, 0, dz), BooleanOp.AND).move(-dx, 0, -dz);
    }
}
