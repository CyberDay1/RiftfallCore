package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeEncounters;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"com.Harbinger.Spore.Sentities.EvolvedInfected.Brute",
        "com.Harbinger.Spore.Sentities.EvolvedInfected.Leaper",
        "com.Harbinger.Spore.Sentities.EvolvedInfected.Stalker"}, remap = false)
public abstract class MixinSporeHyperEvolution {
    @Inject(method = "HyperEvolve", at = @At("HEAD"), cancellable = true)
    private void riftfall$fixedForm(CallbackInfo ci) {
        if (SporeEncounters.restricted((Entity) (Object) this)) ci.cancel();
    }
}
