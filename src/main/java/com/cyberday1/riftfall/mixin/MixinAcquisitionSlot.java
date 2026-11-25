package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.AcquisitionBan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class MixinAcquisitionSlot {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void riftfall$noDisabledExtraction(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (AcquisitionBan.blocked(((Slot) (Object) this).getItem())) cir.setReturnValue(false);
    }
}
