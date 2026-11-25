package com.cyberday1.riftfall.mixincompat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No free right-click socketing in an inventory; the socketing gate refuses it too. */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.apotheosis.AdventureEvents", remap = false)
public abstract class MixinApotheosisFreeSocketing {
    @Inject(method = "stackedOnOther(Lnet/neoforged/neoforge/event/ItemStackedOnOtherEvent;)V", at = @At("HEAD"), cancellable = true)
    private void riftfall$noFreeSocketing(CallbackInfo ci) { ci.cancel(); }
}
