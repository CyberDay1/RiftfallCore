package com.cyberday1.riftfall.mixincompat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject creator/import packets on the server, including clients without the UI restriction. */
@Pseudo
@Mixin(targets = "buildaspell.network.SaveSpellPacket", remap = false)
public abstract class MixinBuildASpellSave {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void riftfall$classSkillsOnly(CallbackInfo ci) {
        ci.cancel();
    }
}
