package com.cyberday1.riftfall.block.resurrection;

import com.cyberday1.riftfall.block.holycore.HolyCoreStructure;
import com.cyberday1.riftfall.init.RiftfallBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A generated placeholder that becomes a live core once its pedestal is loaded. */
public class WorldResurrectionMachineBlockEntity extends BlockEntity {
    public WorldResurrectionMachineBlockEntity(BlockPos pos, BlockState state) {
        super(RiftfallBlockEntities.WORLD_RESURRECTION_MACHINE.get(), pos, state);
    }
    public static void tick(Level level, BlockPos pos, BlockState state, WorldResurrectionMachineBlockEntity marker) {
        if (level instanceof ServerLevel server && level.getGameTime() % 20 == 0) HolyCoreStructure.initialize(server, pos);
    }
}
