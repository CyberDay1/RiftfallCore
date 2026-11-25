package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.WeaponDonors;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.shipovskijkorp.scythes.mod.item.GoldenScytheItem", remap = false)
public abstract class MixinScytheGoldenHeld {
    @Inject(method = "getHeldGoldenScytheHand", at = @At("RETURN"), cancellable = true)
    private static void riftfall$noDonorPassive(Player player, CallbackInfoReturnable<InteractionHand> cir) {
        InteractionHand hand = cir.getReturnValue();
        if (hand != null && WeaponDonors.applies(player.getItemInHand(hand))) cir.setReturnValue(null);
    }
}
