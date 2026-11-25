package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeEncounters;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sentities.AI.InfectedConsumeFromRemains", remap = false)
public abstract class MixinSporeConsumeRemains {
    @Inject(method = "isCorpse", at = @At("HEAD"), cancellable = true)
    private void riftfall$leaveAuthoredBlocks(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SporeEncounters.restricted(entity)) cir.setReturnValue(false);
    }
}
