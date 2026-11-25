package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeEncounters;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sentities.EvolvingInfected", remap = false)
public interface MixinSporeEvolution {
    // Stop before native evolution discards the original (refusing its replacement is too late).
    @Inject(method = {"tickEvolution", "tickHyperEvolution", "Evolve", "HyperEvolve"},
            at = @At("HEAD"), cancellable = true)
    default void riftfall$fixedForm(CallbackInfo ci) {
        if (SporeEncounters.restricted((Entity) (Object) this)) ci.cancel();
    }
}
