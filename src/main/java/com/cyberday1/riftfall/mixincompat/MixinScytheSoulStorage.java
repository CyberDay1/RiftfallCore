package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.WeaponDonors;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.shipovskijkorp.scythes.mod.item.WitheringScytheItem", remap = false)
public abstract class MixinScytheSoulStorage {
    @Inject(method = "addSouls", at = @At("HEAD"), cancellable = true)
    private static void riftfall$noParallelSoulProgression(ItemStack stack, int amount, CallbackInfo ci) {
        if (WeaponDonors.applies(stack)) ci.cancel();
    }
}
