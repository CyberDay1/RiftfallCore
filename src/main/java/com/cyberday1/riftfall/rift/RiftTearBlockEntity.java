package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.init.RiftfallBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Nothing but a place to hang a renderer.
 *
 * <p>
 * It holds no data, saves none, and never ticks. A tear's whole state is already on the block, and
 * the only thing this exists to buy is that a {@code BlockEntityRenderer} runs every frame with a
 * {@code PoseStack} in hand. A block model cannot: it bakes into the chunk mesh, which is why the
 * tear could not turn to face anyone until it stopped being one.
 *
 * <p>
 * Only the bottom block of a tear gets one &mdash; see {@link RiftTearBlock#newBlockEntity}. The
 * picture is a single image spanning the whole rip, so one renderer draws all of it, and giving the
 * other two blocks an entity apiece would only buy two more chances to draw it again.
 */
public class RiftTearBlockEntity extends BlockEntity {

    public RiftTearBlockEntity(BlockPos pos, BlockState state) {
        super(RiftfallBlockEntities.RIFT_TEAR.get(), pos, state);
    }
}
