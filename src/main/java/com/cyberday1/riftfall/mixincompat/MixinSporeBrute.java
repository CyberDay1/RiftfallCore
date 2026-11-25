package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeEncounters;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sentities.EvolvedInfected.Brute", remap = false)
public abstract class MixinSporeBrute {
    @Inject(method = "blocky", at = @At("HEAD"), cancellable = true)
    private void riftfall$throwWithoutExcavating(CallbackInfoReturnable<BlockState> cir) {
        if (SporeEncounters.restricted((Entity) (Object) this))
            cir.setReturnValue(Blocks.COBBLESTONE.defaultBlockState());
    }
}
