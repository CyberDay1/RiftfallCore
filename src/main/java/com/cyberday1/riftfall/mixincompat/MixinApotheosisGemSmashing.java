package com.cyberday1.riftfall.mixincompat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gems survive anvils; dissolving is the Jeweler's job, at the Jeweler's rate. */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.apotheosis.AdventureEvents", remap = false)
public abstract class MixinApotheosisGemSmashing {
    @Inject(method = "gemSmashing(Ldev/shadowsoffire/placebo/events/AnvilLandEvent;)V", at = @At("HEAD"), cancellable = true)
    private void riftfall$noGemSmashing(CallbackInfo ci) { ci.cancel(); }
}
