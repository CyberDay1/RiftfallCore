package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.compat.BetterCombatWeapons;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WeaponRegistry.class, remap = false)
public abstract class MixinBetterCombatWeapons {
    @Inject(method = "getAttributes(Lnet/minecraft/world/item/ItemStack;)Lnet/bettercombat/api/WeaponAttributes;",
            at = @At("RETURN"), cancellable = true)
    private static void riftfall$keepWeaponFloor(ItemStack stack, CallbackInfoReturnable<WeaponAttributes> cir) {
        cir.setReturnValue(BetterCombatWeapons.forStack(stack, cir.getReturnValue()));
    }
}
