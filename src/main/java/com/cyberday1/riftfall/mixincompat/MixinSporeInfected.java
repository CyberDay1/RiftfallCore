package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeContainment;
import com.cyberday1.riftfall.mob.SporeEncounters;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sentities.BaseEntities.Infected", remap = false)
public abstract class MixinSporeInfected {
    @Inject(method = {"placeRemains", "placeFrozenRemains", "setDefaultLinkage", "spawnWithPoints"},
            at = @At("HEAD"), cancellable = true)
    private void riftfall$noEcology(CallbackInfo ci) {
        if (SporeEncounters.restricted((Entity) (Object) this)) ci.cancel();
    }

    // Starvation growths and infectious remains: the Spore spread a death leaves outside a rift.
    @Inject(method = "placeRemains", at = @At("HEAD"), cancellable = true)
    private void riftfall$noRemainsOutsideRifts(CallbackInfo ci) {
        if (SporeContainment.contained((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = {"canGrief", "interactBlock", "canStarve"}, at = @At("HEAD"), cancellable = true)
    private void riftfall$encounterBody(CallbackInfoReturnable<Boolean> cir) {
        if (SporeEncounters.restricted((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
