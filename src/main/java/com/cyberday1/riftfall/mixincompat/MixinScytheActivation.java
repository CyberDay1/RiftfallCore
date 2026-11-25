package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.WeaponDonors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.shipovskijkorp.scythes.mod.ability.ScytheAbilityHandler", remap = false)
public abstract class MixinScytheActivation {
    @Inject(method = "activateHeld", at = @At("HEAD"), cancellable = true)
    private static void riftfall$skillsOnly(ServerPlayer player, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (WeaponDonors.applies(stack)) cir.setReturnValue(true);
    }
}
