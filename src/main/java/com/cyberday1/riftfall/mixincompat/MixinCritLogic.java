package com.cyberday1.riftfall.mixincompat;

import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * critical_strike never crits. {@code CritLogic#modifyDamage} is its only roll, for a player's
 * swing and a player's arrow; null is its own "no crit" answer. Its jump-crit ban is untouched.
 */
@Pseudo
@Mixin(targets = "net.critical_strike.internal.CritLogic", remap = false)
public abstract class MixinCritLogic {

    @Inject(method = "modifyDamage", at = @At("HEAD"), cancellable = true)
    private static void riftfall$noCriticalStrike(@Coerce Object critter, DamageSource source, float amount,
                                                  CallbackInfoReturnable<Object> cir) {
        cir.setReturnValue(null);
    }
}
