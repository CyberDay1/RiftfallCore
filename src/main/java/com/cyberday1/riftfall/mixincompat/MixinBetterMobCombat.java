package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.compat.MobCombatPolicy;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "bmc_re.better_mob_combat.logic.MobCombatLogic", remap = false)
public abstract class MixinBetterMobCombat {
    @Inject(method = {"isEligible", "isFallbackMeleeCandidate"}, at = @At("HEAD"), cancellable = true)
    private static void riftfall$authoredCombat(Mob mob, CallbackInfoReturnable<Boolean> cir) {
        if (MobCombatPolicy.preserve(mob)) cir.setReturnValue(false);
    }

    @Inject(method = "playFallbackMeleeAnimation", at = @At("HEAD"), cancellable = true)
    private static void riftfall$authoredAnimation(Mob mob, CallbackInfo ci) {
        if (MobCombatPolicy.preserve(mob)) ci.cancel();
    }
}
