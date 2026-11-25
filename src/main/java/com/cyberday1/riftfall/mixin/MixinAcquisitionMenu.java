package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.AcquisitionBan;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AbstractContainerMenu.class)
public abstract class MixinAcquisitionMenu {
    @ModifyVariable(method = "setCarried", at = @At("HEAD"), argsOnly = true)
    private ItemStack riftfall$noDisabledCursor(ItemStack stack) { return AcquisitionBan.allowed(stack); }
}
