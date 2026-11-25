package com.cyberday1.riftfall.mixincompat;

import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Riftfall owns progression: no tutorial gate, tier unlocks or activations. */
@Mixin(value = WorldTier.class, remap = false)
public abstract class MixinApotheosisTutorial {
    // Common-side interception also covers the client before its statistics have synchronized.
    // Reading an item must not award an activation statistic.
    @Inject(method = "isTutorialActive", at = @At("HEAD"), cancellable = true)
    private static void riftfall$showEquipmentImmediately(Player player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    /** Native loot APIs require an enum row; it is a fixed baseline, not player progression. */
    @Inject(method = "getTier", at = @At("HEAD"), cancellable = true)
    private static void riftfall$fixedLootBaseline(Player player, CallbackInfoReturnable<WorldTier> cir) {
        cir.setReturnValue(WorldTier.HAVEN);
    }

    @Inject(method = "setTier", at = @At("HEAD"), cancellable = true)
    private static void riftfall$noTierActivation(Player player, WorldTier tier, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "isUnlocked", at = @At("HEAD"), cancellable = true)
    private static void riftfall$noTierUnlocks(Player player, WorldTier tier, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
