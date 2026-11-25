package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.stat.SingleCrit;
import dev.shadowsoffire.apothic_attributes.ALConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The attributes screen lists every attribute a player has, closed crit rolls included. Hides
 * them after each config load, which is when Apothic rebuilds the set from its file.
 */
@Mixin(value = ALConfig.class, remap = false)
public abstract class MixinALConfig {

    @Inject(method = "load", at = @At("TAIL"))
    private static void riftfall$hideClosedCrits(CallbackInfoReturnable<?> cir) {
        ALConfig.hiddenAttributes.addAll(SingleCrit.CLOSED);
    }
}
