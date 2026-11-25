package com.cyberday1.riftfall.mixincompat;

import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import dev.shadowsoffire.apotheosis.tiers.augments.TierAugment;
import dev.shadowsoffire.apotheosis.tiers.augments.TierAugmentRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(value = TierAugmentRegistry.class, remap = false)
public abstract class MixinApotheosisAugments {
    @Inject(method = "getAugments", at = @At("HEAD"), cancellable = true)
    private static void riftfall$noTierEffects(WorldTier tier, TierAugment.Target target,
            CallbackInfoReturnable<List<TierAugment>> cir) {
        cir.setReturnValue(List.of());
    }
}
