package com.cyberday1.riftfall.mixincompat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes native free buffs, including the held-item tick path that bypasses packets. */
@Pseudo
@Mixin(targets = {
        "net.mcreator.opscythes.procedures.RedScythePlayerProcedure",
        "net.mcreator.opscythes.procedures.OrangeScythePlayerProcedure",
        "net.mcreator.opscythes.procedures.YellowScythePlayerProcedure",
        "net.mcreator.opscythes.procedures.GreenScythePlayerProcedure",
        "net.mcreator.opscythes.procedures.BlueScythePlayerProcedure",
        "net.mcreator.opscythes.procedures.PurpleScythePlayerProcedure",
        "net.mcreator.opscythes.procedures.PinkScythePlayerProcedure",
        "net.mcreator.opscythes.procedures.RainbowScythePlayerProcedure"
}, remap = false)
public abstract class MixinOpScytheEffects {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private static void riftfall$removeNativeBuffs(CallbackInfo ci) {
        ci.cancel();
    }
}
