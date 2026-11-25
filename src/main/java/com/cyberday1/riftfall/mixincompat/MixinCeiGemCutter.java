package com.cyberday1.riftfall.mixincompat;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Gem Cutter already placed in an old world takes no gem off the belt; purity is the Jeweler's. */
@Pseudo
@Mixin(targets = "plus.dragons.createenchantmentindustry.integration.apotheosis.common.processing.socket.gem.gemCutter.GemCutterBlockEntity", remap = false)
public abstract class MixinCeiGemCutter {
    @Inject(method = "isUpgradableGem", at = @At("HEAD"), cancellable = true)
    private static void riftfall$noBeltCutting(ItemStack stack, CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(false); }
}
