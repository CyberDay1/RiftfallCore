package com.cyberday1.riftfall.mixincompat;

import dev.shadowsoffire.apotheosis.commands.WorldTierCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldTierCommand.class, remap = false)
public abstract class MixinApotheosisTierCommand {
    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void riftfall$noTierCommand(CallbackInfo ci) { ci.cancel(); }
}
