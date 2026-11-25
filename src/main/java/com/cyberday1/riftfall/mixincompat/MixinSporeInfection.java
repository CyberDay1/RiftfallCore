package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeContainment;
import com.cyberday1.riftfall.mob.SporeEncounters;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sevents.Infection", remap = false)
public abstract class MixinSporeInfection {
    @Inject(method = "onEntityDeath", at = @At("HEAD"))
    private static void riftfall$noDeathConversion(LivingDeathEvent event, CallbackInfo ci) {
        // Preserve native weapon kill rewards and normal death/loot handling.
        SporeEncounters.clearInfection(event.getEntity());
        SporeContainment.clear(event.getEntity());
    }
}
