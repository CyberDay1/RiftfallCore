package com.cyberday1.riftfall.mixincompat;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {
        "net.mcreator.opscythes.network.EffectsRedScytheMessage",
        "net.mcreator.opscythes.network.EffectsOrangeScytheMessage",
        "net.mcreator.opscythes.network.EffectsYellowScytheMessage",
        "net.mcreator.opscythes.network.EffectsGreenScytheMessage",
        "net.mcreator.opscythes.network.EffectsBlueScytheMessage",
        "net.mcreator.opscythes.network.EffectsPurpleScytheMessage",
        "net.mcreator.opscythes.network.EffectsPinkScytheMessage",
        "net.mcreator.opscythes.network.EffectsRainbowScytheMessage"
}, remap = false)
public abstract class MixinOpScytheActivation {
    @Inject(method = "pressAction", at = @At("HEAD"), cancellable = true)
    private static void riftfall$skillsOnly(Player player, int type, int held, CallbackInfo ci) {
        // Native packets grant free buffs even with empty hands. Riftfall skills own abilities.
        ci.cancel();
    }
}
