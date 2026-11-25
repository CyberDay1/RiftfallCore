package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.compat.BetterCombatCasting;
import net.bettercombat.client.compat.SpellEngineCompatibility;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpellEngineCompatibility.class, remap = false)
public abstract class MixinBetterCombatCasting {
    @Inject(method = "isCastingSpell", at = @At("RETURN"), cancellable = true)
    private static void riftfall$releaseGesture(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (BetterCombatCasting.releasing(player)) cir.setReturnValue(true);
    }
}
