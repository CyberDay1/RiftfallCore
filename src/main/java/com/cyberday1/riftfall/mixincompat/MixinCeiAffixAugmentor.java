package com.cyberday1.riftfall.mixincompat;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A placed Affix Augmentor upgrades nothing: augmenting is cut with no replacement (JW4). */
@Pseudo
@Mixin(targets = "plus.dragons.createenchantmentindustry.integration.apotheosis.common.processing.affix.affixEnhancer.AffixAugmentorBlockEntity", remap = false)
public abstract class MixinCeiAffixAugmentor {
    @Inject(method = "hasUpgradableAffix", at = @At("HEAD"), cancellable = true)
    private static void riftfall$noBeltAugmenting(ItemStack stack, CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(false); }
}
