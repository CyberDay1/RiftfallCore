package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.home.ResidentTileProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Modded demolition often calls Level directly without firing a player break event. */
@Mixin(Level.class)
public abstract class MixinResidentTileDestruction {
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"), cancellable = true)
    private void riftfall$keepResidentTile(BlockPos pos, boolean drops, Entity actor, int recursion,
            CallbackInfoReturnable<Boolean> cir) {
        if (ResidentTileProtection.protectedAt((Level) (Object) this, pos)) cir.setReturnValue(false);
    }
}
