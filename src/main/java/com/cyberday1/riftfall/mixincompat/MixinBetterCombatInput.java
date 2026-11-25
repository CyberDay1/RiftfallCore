package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.compat.BetterCombatCasting;
import net.bettercombat.client.BetterCombatClientMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs before Better Combat's own HEAD injections, including its held-button auto attack. */
@Mixin(value = Minecraft.class, priority = 2000)
public abstract class MixinBetterCombatInput {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void riftfall$releaseBeforeClick(CallbackInfoReturnable<Boolean> cir) {
        var player = ((Minecraft) (Object) this).player;
        if (BetterCombatClientMod.ENABLED && player != null && BetterCombatCasting.releasing(player))
            cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void riftfall$releaseBeforeHold(boolean attack, CallbackInfo ci) {
        var player = ((Minecraft) (Object) this).player;
        if (BetterCombatClientMod.ENABLED && player != null && BetterCombatCasting.releasing(player)) ci.cancel();
    }
}
