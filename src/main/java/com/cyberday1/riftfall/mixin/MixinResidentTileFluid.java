package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.home.ResidentTileProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Water or lava from a neighbouring tile does not flow into a resident's tile and wash its blocks away. */
@Mixin(FlowingFluid.class)
public abstract class MixinResidentTileFluid {
    @Inject(method = "canSpreadTo", at = @At("HEAD"), cancellable = true)
    private void riftfall$stayOutOfResidentTile(BlockGetter getter, BlockPos from, BlockState fromState, Direction direction,
            BlockPos to, BlockState toState, FluidState toFluid, Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        if (getter instanceof Level level && ResidentTileProtection.fluidBlocked(level, from, to)) cir.setReturnValue(false);
    }
}
